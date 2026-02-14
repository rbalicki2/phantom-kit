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
| Held modifier keys | macOS | `osascript 'key up command/shift/option/control'` |
| Physically depressed keys | Keyboard/macOS | Can't clear programmatically |
| warpd process | System process | `pkill warpd` |
| Homerow labels | Homerow app | `hs -c 'dismissHomerow()'` |
| Scroll timer | Hammerspoon | `hs -c 'scrollStop()'` |
| Hover mode tap | Hammerspoon | `hs -c 'hoverModeStop()'` |
| Frontmost app | macOS | Can't clear programmatically |
| Active project (iso/pin) | /tmp/karabiner-project | Can't clear (no default) |

Any state transition that could leave external state dirty must clean it up explicitly. Panic mode clears all external state listed above.

## State Variables

**Note:** These variables only apply to the Desktop profile (Kinesis keyboard). Laptop rules do not use state variables.

Four variables track all state:

| Variable | Range | Purpose |
|----------|-------|---------|
| `dsk_layer` | 0-28 | Current layer |
| `dsk_in_modal_layer` | 0/1 | Whether in a modal layer |
| `dsk_ins_sub_mode` | -1 to 4 | Overlay state within Ins mode (-1 = N/A, 0 = none active) |
| `dsk_return_to_layer` | -1/0/1 | Return destination for Label mode (-1=N/A, 0=Normal, 1=Ins) |

### Invariants

1. **dsk_in_modal_layer = (dsk_layer >= 2 ? 1 : 0)** — Must always hold. If out of sync, behavior breaks.
2. **dsk_ins_sub_mode = -1 when dsk_layer != 1** — Submodes only exist within Ins mode. Set to 0 on Ins entry.
3. **dsk_return_to_layer = -1 when dsk_layer != 13** — Only valid in Label mode. Set to 0 or 1 on Label entry.

### Explicit State Transitions

Every state transition in karabiner.edn MUST explicitly set ALL variables to their correct values, even if we expect them to already be correct. Always set them in the same order every time. No implicit state. This prioritizes correctness and future refactors over brevity.

Additionally, all state transitions should clear ALL external state via `cleanup-external-state.sh`. The script takes an explicit flag for every piece of clearable external state, either `reset` or `keep`. Flags must always appear in this exact order:
```
cleanup-external-state.sh \
  --warpd reset \
  --homerow reset \
  --scroll-timer reset \
  --hover-mode reset \
  --held-modifiers reset
```

**Exception**: Use `keep` for external state the target mode depends on. For example, Grid mode relies on warpd running (`--warpd keep`). App/window switcher relies on Cmd being held (`--held-modifiers keep`).

Example: Entering Normal should set `dsk_layer=0, dsk_in_modal_layer=0, dsk_ins_sub_mode=-1, dsk_return_to_layer=-1` and call `cleanup-external-state.sh` with all flags set to `reset`.

### Rule Ordering in karabiner.edn

Karabiner uses the first matching rule, so rules must be ordered from most specific to least specific:

1. **Submode rules** (submode 1-4) — Most specific, overlay states within Ins mode
2. **Mode-specific rules** (mode 1 Ins, mode 2 Nav, etc.) — Rules for specific layers
3. **Fallback/generic rules** — Catch-all rules with no mode conditions

**Exception**: Global utility rules that apply everywhere (disable LHS keys, panic button, overlay, generic clicks) go first for organizational clarity. These don't check mode variables so ordering doesn't affect their behavior.

### Key Unmapping Strategy

Unused keys are disabled at three levels:

1. **LHS keys (global)**: q,w,e,r,t,a,s,d,f,g,z,x,c,v,b and arrows are disabled globally for all Desktop layers. This is a one-handed (RHS) keyboard setup.

2. **Layer 0 (Normal mode)**: Unused RHS keys are explicitly disabled. Only layer selector keys (j,n,m,i,comma,l,u,h,6,y) are active.

3. **Modal layers (fallback)**: A global fallback rule at the end of karabiner.edn blocks all keys when `dsk_in_modal_layer=1`. Layer-specific rules earlier in the file define what keys DO in each layer; the fallback catches everything else.

This approach means modal layers don't need explicit unmapping rules—they only define active keys, and the fallback handles the rest.

## Global Shortcuts

These work from ANY modal layer (mode >= 2):

| Shortcut | State Change |
|----------|--------------|
| right_ctrl alone | dsk_layer=0, dsk_in_modal_layer=0, dsk_ins_sub_mode=-1, dsk_return_to_layer=-1 |
| Ctrl+J | dsk_layer=1, dsk_in_modal_layer=0, dsk_ins_sub_mode=0, dsk_return_to_layer=-1 |
| Panic | dsk_layer=0, dsk_in_modal_layer=0, dsk_ins_sub_mode=-1, dsk_return_to_layer=-1 |

**Ctrl+N** is global and works from any modal layer (dsk_in_modal_layer=1). It sends escape and calls cleanup-external-state.sh with all flags set to reset.

## Layer Exit Behavior

When an action completes, the destination depends on action type:

**→ Normal (dsk_layer=0)**: Non-typing actions
- Copy, close, undo/redo, window management
- Mouse clicks when dsk_return_to_layer=0

**→ Ins (dsk_layer=1)**: Text-input actions
- Paste, find, address bar, new tab/file, command palette
- Mouse clicks when dsk_return_to_layer=1

**Stay in layer**: Repeatable actions
- Tab switching, scrolling, back/forward

## Mouse Modes

### Label Mode (dsk_layer=13)

Entry determines return destination via dsk_return_to_layer:
- M from Normal → dsk_return_to_layer=0 → clicks return to Normal
- Ctrl+M from Ins → dsk_return_to_layer=1 → clicks return to Ins

### Grid Mode (dsk_layer=28)

Only entered from Normal. Always returns to Normal.

### Click Handling

Both mouse modes use the same click keys (Space variants for left-click, Enter variants for right-click). The key insight is **hover mode**: pressing Up positions the cursor without clicking, then waits for a follow-up click key. This allows peeking at a target before committing to a click action.

## Ins Mode Submodes

When dsk_layer=1, submode overlays additional behavior without leaving Ins mode.

### Oneshot Submodes (1, 2)

These affect the **next letter only**, then clear. Implementation: every letter key in Ins mode has rules that check submode. When dsk_ins_sub_mode=1 or 2, the rule outputs Shift+letter (or Shift+mirrored letter) and sets dsk_ins_sub_mode=0.

- **dsk_ins_sub_mode=1 (shift_mirror_oneshot)**: Fn+] triggers. Next mirrored letter outputs uppercase.
- **dsk_ins_sub_mode=2 (shift_oneshot)**: Fn+Space triggers. Next letter outputs uppercase.

### Chord Submodes (3, 4)

These are held modes for delete/select word/line operations:

- **dsk_ins_sub_mode=3 (rcmd_h_mode)**: Hold rcmd+H, then J/K/M/, to delete word/line
- **dsk_ins_sub_mode=4 (rcmd_n_mode)**: Hold rcmd+N, then J/K/M/, to select word/line

**Why not separate layers?** These could be modes 29, 30, but they're tightly coupled to Ins mode—you're still typing, just with a modifier chord active. Using submode keeps them as overlays rather than full mode switches.

**Interaction with oneshot**: The chord submodes (3, 4) set submode, which clears any active oneshot. However, plain Cmd+H/N (without rcmd) doesn't clear oneshot—this inconsistency is a bug.

## App/Window Switcher (modes 11, 12)

Entry from InApp layer (dsk_layer=10):
- Up → dsk_layer=11 (App switcher), holds Cmd
- Down → dsk_layer=12 (Window switcher), holds Cmd

While in switcher:
- J/K cycles through apps/windows
- Enter selects and exits (releases Cmd, returns to Normal)
- Ctrl+N exits without selecting (releases Cmd, returns to Normal)

## Mental Model Todos

- [x] Set dsk_return_to_layer when leaving Normal (0) or Ins (1) so it's always correct for Label mode
- [x] Implement dsk_ins_sub_mode=-1 when dsk_layer != 1
- [x] Implement dsk_return_to_layer=-1 when dsk_layer != 13 (done - already set correctly in transitions)
- [x] Audit all state transitions for explicit state setting (done enough)
- [x] Make all state transitions clear ALL external state via cleanup-external-state.sh (done enough)
- [x] Create cleanup-external-state.sh script that clears all external state, with flags to reset specific cleanups (e.g., `--reset-warpd`). Call from Karabiner shell commands instead of inline chained commands.
- [x] Make Ctrl+N truly global: one rule that does ALL cleanup (pkill warpd, dismissHomerow, release Cmd) unconditionally—harmless if not needed
- [x] Prefix all variable names with "dsk_" to make it clear they're desktop-only (dsk_layer, dsk_in_modal_layer, dsk_ins_sub_mode, dsk_return_to_layer)
- [ ] Split submodes into "oneshot" (1, 2) and "rcmd chord" (3, 4) categories in documentation
- [ ] Consider removing dsk_in_modal_layer variable: Add explicit pass-through rules for all keys in Ins mode (layer 1), then global key blocking can be unconditional. Exit rules would need to become per-layer instead of global. Trade-off: one less variable vs ~40+ pass-through rules in Ins.
- [x] Create validation script (validate-edn.bb) to check karabiner.edn rules: (1) action arrays don't start with variable sets (causes null), (2) no multiple shell commands in action arrays (only last executes)

## Potential Bugs

- [ ] Ctrl+J should set dsk_ins_sub_mode=0 when entering Ins (currently doesn't)
- [ ] Cmd+H/N in Ins mode doesn't clear oneshot submode (rcmd+H/N does because it sets dsk_ins_sub_mode=3/4)
- [ ] App/Window switcher exit via right_ctrl: Does it release the held Cmd key? Ctrl+N and Enter release Cmd, but right_ctrl alone might not.
- [x] Grid mode doesn't set dsk_return_to_layer=0 on entry: Fixed - now sets dsk_return_to_layer=0 on entry from Normal.
- [ ] Scroll timer / hover mode not cleared on most exits: Only panic clears them. If you exit InApp (which has scroll) via right_ctrl, is scrollStop() called?
- [ ] Oneshot submodes (1, 2) might not clear on non-letter keys: What if you press a number, symbol, or modifier after entering oneshot? Does it clear or persist incorrectly?
