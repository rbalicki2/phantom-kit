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
| Held modifier keys | macOS | `panic-cleanup.sh` only (see below) |
| Physically depressed keys | Keyboard/macOS | Can't clear programmatically |
| warpd process | System process | `pkill warpd` |
| Homerow labels | Homerow app | `hs -c 'dismissHomerow()'` |
| Scroll timer | Hammerspoon | `hs -c 'scrollStop()'` |
| Hover mode tap | Hammerspoon | `hs -c 'hoverModeStop()'` |
| L-mode modifier | /tmp/karabiner-lmode-modifier | `rm -f` (always reset) |
| Frontmost app | macOS | Can't clear programmatically |
| Active project (iso/pin/pk) | /tmp/karabiner-project | Can't clear (no default) |

Any state transition that could leave external state dirty must clean it up explicitly. Panic mode clears all external state listed above.

## State Variables

**Note:** These variables only apply to the Desktop profile (Kinesis keyboard). Laptop rules do not use state variables.

Three variables track all state:

| Variable | Range | Purpose |
|----------|-------|---------|
| `dsk_layer` | 0-30 | Current layer |
| `dsk_ins_sub_mode` | -1 to 4 | Overlay state within Ins mode (-1 = N/A, 0 = none active) |
| `dsk_return_to_layer` | -1/0/1 | Return destination for Label mode (-1=N/A, 0=Normal, 1=Ins) |

### Invariants

1. **dsk_ins_sub_mode = -1 when dsk_layer != 1** — Submodes only exist within Ins mode. Set to 0 on Ins entry.
2. **dsk_return_to_layer = -1 when dsk_layer != 13** — Only valid in Label mode. Set to 0 or 1 on Label entry.

These invariants are **automatically enforced** by `validate-rules.bb` on every sync.

### Explicit State Transitions

Every state transition in karabiner.edn MUST explicitly set ALL variables to their correct values, even if we expect them to already be correct. Always set them in the same order every time. No implicit state. This prioritizes correctness and future refactors over brevity.

Additionally, all state transitions should clear external state via `cleanup-external-state.sh`. The script takes an explicit flag for every piece of clearable external state, either `reset` or `keep`. Flags must always appear in this exact order:
```
cleanup-external-state.sh \
  --warpd reset \
  --homerow reset \
  --scroll-timer reset \
  --hover-mode reset \
  --lmode-modifier reset
```

**Exception**: Use `keep` for external state the target mode depends on. For example, Grid mode relies on warpd running (`--warpd keep`).

**Why no held-modifiers flag?** The osascript `key up <modifier>` command clears BOTH synthetic modifier state AND interferes with Karabiner-emitted keystrokes. When run in the background during a transition, it can cancel Ctrl+key outputs that Karabiner is actively emitting (race condition). Karabiner EventViewer shows what Karabiner emits, but the system receives the `key up` canceling the keystroke. Only `panic-cleanup.sh` resets held modifiers, since panic mode does a full reset anyway.

Example: Entering Normal should set `dsk_layer=0, dsk_ins_sub_mode=-1, dsk_return_to_layer=-1` and call `cleanup-external-state.sh` with all flags set to `reset`.

### Rule Ordering: Leaf to Root

Karabiner uses the first matching rule, so rules must be ordered **leaf to root**—from most specific conditions to least specific:

1. **Layer-specific rules** — Rules with `["dsk_layer" N]` conditions belong in their layer sections
2. **Variable-specific fallbacks** — Rules checking other variables (e.g., `dsk_return_to_layer`)
3. **Global fallbacks** — Catch-all rules with `nil` or no condition, placed at the end

**Key principle**: A rule with condition X should never appear before a rule with condition X AND Y. The more specific rule (X AND Y) must come first, or it will be shadowed.

**Example**: The `right_control` exit rules follow this pattern:
- Layer 13/28 (mouse modes) have their own exit rules in their sections
- Global fallback at the end catches all other layers

### Key Blocking Strategy

Unused keys are disabled via a global fallback rule at the end of karabiner.edn that blocks all unmapped keys. Layer-specific rules define what keys DO; the fallback catches everything else.

LHS keys (q,w,e,r,t,a,s,d,f,g,z,x,c,v,b, backspace, delete) are enforced by validation to only appear in blocking rules - this is a one-handed (RHS) keyboard setup.

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

## Conceptual Model: Config as Nested Hashmap

The karabiner.edn config can be conceptualized as a multi-level nested hashmap:

```
Profile → Layer → Submode → Key → [Actions]
```

More precisely:
- **Level 1**: Profile (Desktop, Laptop)
- **Level 2**: Layer (0=Normal, 1=Ins, 2=Nav, ...)
- **Level 3**: Submode (only within layer 1: 0=base, 1=shift_mirror_oneshot, ...)
- **Level 4**: Input key (including modifiers)
- **Value**: Array of actions

Rules "higher" in the tree (less specific) apply as fallbacks to all rules "below" them.
