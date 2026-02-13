# Phantom Kit Mental Model

Focus: State transitions, invariants, and behaviors that must be preserved.

## Principles

### Prefer No-ops Over Conditional Behavior

When cleaning up state, always perform the cleanup unconditionally rather than checking if it's needed. For example, `pkill warpd` is safe even if warpd isn't running. This simplifies logic and prevents bugs from missed edge cases.

### Every Transition = Panic + Desired State

Conceptually, every state transition should behave as if it:
1. Calls panic (clears ALL state—both Karabiner variables and external state)
2. Sets the exact state we want

This isn't literally implementable: shell commands can't set Karabiner variables, and we can't clear external state we're actively relying on (see Exception below). But it's the mental model—transitions should be as self-contained as possible and not rely on "current state is probably X".

### Centralize State in Karabiner

**Goal**: Manage as much state as possible within Karabiner, minimizing external dependencies.

**Example**: The Kinesis keyboard has native layer support, but we don't use it for mode switching. Instead, all Fn+key combos send F-keys that Karabiner interprets. The keyboard layers exist but behave identically—Karabiner is the single source of truth for mode.

### External State Awareness

Be extra wary of state that lives outside Karabiner variables:

| External State | Where | Cleanup |
|----------------|-------|---------|
| Cmd held down | macOS (app/window switcher) | `osascript 'key up command'` |
| warpd process | System process | `pkill warpd` |
| Homerow labels | Homerow app | `hs -c 'dismissHomerow()'` |
| Scroll timer | Hammerspoon | `hs -c 'scrollStop()'` |
| Hover mode tap | Hammerspoon | `hs -c 'hoverModeStop()'` |
| Held modifiers | macOS | Panic releases all |

Note: Frontmost app affects behavior (e.g., H key enters different layers) but we don't modify it.

Any state transition that could leave external state dirty must clean it up explicitly. Panic mode clears all external state listed above.

## State Variables

Four variables track all state:

| Variable | Range | Purpose |
|----------|-------|---------|
| `mode` | 0-28 | Current layer |
| `in_modal` | 0/1 | Whether in a modal layer |
| `submode` | -1 to 4 | Overlay state within Ins mode (-1 = N/A, 0 = none active) |
| `mouse_from_ins` | -1/0/1 | Return destination for Label mode (-1=N/A, 0=Normal, 1=Ins). Future: rename to `return_to_layer` |

### Invariants

1. **in_modal = (mode >= 2 ? 1 : 0)** — Must always hold. If out of sync, behavior breaks.
2. **submode = -1 when mode != 1** — Submodes only exist within Ins mode. Set to 0 on Ins entry.
3. **mouse_from_ins = -1 when mode != 13** — Only valid in Label mode. Set to 0 or 1 on Label entry.

### Explicit State Transitions

Every state transition in karabiner.edn MUST explicitly set ALL variables to their correct values, even if we expect them to already be correct. No implicit state. This prioritizes correctness and future refactors over brevity.

Additionally, all state transitions should clear ALL external state (see External State Awareness table). This follows the "prefer no-ops" principle—clearing state that's already clean is harmless.

**Exception**: Don't clear external state that the target mode depends on. For example, Grid mode relies on warpd running, so transitions within Grid mode (navigation keys) must not kill warpd. App/window switcher relies on Cmd being held, so J/K cycling must not release Cmd.

Example: Entering Normal should set `mode=0, in_modal=0, submode=-1, mouse_from_ins=-1` and also clear external state (pkill warpd, dismissHomerow, release Cmd, etc.) even if we "know" some are already correct.

## Global Shortcuts

These work from ANY modal layer (mode >= 2):

| Shortcut | State Change |
|----------|--------------|
| right_ctrl alone | mode=0, in_modal=0, submode=-1, mouse_from_ins=-1 |
| Ctrl+J | mode=1, in_modal=0, submode=0, mouse_from_ins=-1 |
| Panic | mode=0, in_modal=0, submode=-1, mouse_from_ins=-1 |

**Ctrl+N** is NOT global. It exists only in:
- Label mode (mode=13) — exits based on mouse_from_ins
- Grid mode (mode=28) — exits to Normal
- App/Window switcher (modes 11, 12) — cancels and exits to Normal

## Layer Exit Behavior

When an action completes, the destination depends on action type:

**→ Normal (mode=0)**: Non-typing actions
- Copy, close, undo/redo, window management
- Mouse clicks when mouse_from_ins=0

**→ Ins (mode=1)**: Text-input actions
- Paste, find, address bar, new tab/file, command palette
- Mouse clicks when mouse_from_ins=1

**Stay in layer**: Repeatable actions
- Tab switching, scrolling, back/forward

## Mouse Modes

### Label Mode (mode=13)

Entry determines return destination via mouse_from_ins:
- M from Normal → mouse_from_ins=0 → clicks return to Normal
- Ctrl+M from Ins → mouse_from_ins=1 → clicks return to Ins

### Grid Mode (mode=28)

Only entered from Normal. Always returns to Normal.

### Click Handling

Both mouse modes use the same click keys (Space variants for left-click, Enter variants for right-click). The key insight is **hover mode**: pressing Up positions the cursor without clicking, then waits for a follow-up click key. This allows peeking at a target before committing to a click action.

## Ins Mode Submodes

When mode=1, submode overlays additional behavior without leaving Ins mode.

### Oneshot Submodes (1, 2)

These affect the **next letter only**, then clear. Implementation: every letter key in Ins mode has rules that check submode. When submode=1 or 2, the rule outputs Shift+letter (or Shift+mirrored letter) and sets submode=0.

- **submode=1 (shift_mirror_oneshot)**: Fn+] triggers. Next mirrored letter outputs uppercase.
- **submode=2 (shift_oneshot)**: Fn+Space triggers. Next letter outputs uppercase.

### Chord Submodes (3, 4)

These are held modes for delete/select word/line operations:

- **submode=3 (rcmd_h_mode)**: Hold rcmd+H, then J/K/M/, to delete word/line
- **submode=4 (rcmd_n_mode)**: Hold rcmd+N, then J/K/M/, to select word/line

**Why not separate layers?** These could be modes 29, 30, but they're tightly coupled to Ins mode—you're still typing, just with a modifier chord active. Using submode keeps them as overlays rather than full mode switches.

**Interaction with oneshot**: The chord submodes (3, 4) set submode, which clears any active oneshot. However, plain Cmd+H/N (without rcmd) doesn't clear oneshot—this inconsistency is a bug.

## App/Window Switcher (modes 11, 12)

Entry from InApp layer (mode=10):
- Up → mode=11 (App switcher), holds Cmd
- Down → mode=12 (Window switcher), holds Cmd

While in switcher:
- J/K cycles through apps/windows
- Enter selects and exits (releases Cmd, returns to Normal)
- Ctrl+N exits without selecting (releases Cmd, returns to Normal)

## Mental Model Todos

- [ ] Rename `mouse_from_ins` to `return_to_layer` (generic return destination)
- [ ] Bug: Ctrl+J should set submode=0 when entering Ins (currently doesn't)
- [ ] Implement submode=-1 when mode != 1 (if Karabiner supports negative values)
- [ ] Implement mouse_from_ins=-1 when mode != 13 (if Karabiner supports negative values)
- [ ] Audit all state transitions for explicit state setting (no implicit assumptions)
- [ ] Make all state transitions clear ALL external state (pkill warpd, dismissHomerow, release Cmd, scrollStop, hoverModeStop)
- [ ] Make Ctrl+N truly global: one rule that does ALL cleanup (pkill warpd, dismissHomerow, release Cmd) unconditionally—harmless if not needed
- [ ] Bug: Cmd+H/N in Ins mode doesn't clear oneshot submode (rcmd+H/N does because it sets submode=3/4)
