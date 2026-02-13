# Phantom Kit Mental Model

Focus: State transitions, invariants, and behaviors that must be preserved.

## State Variables

Four variables track all state:

| Variable | Range | Purpose |
|----------|-------|---------|
| `mode` | 0-28 | Current layer |
| `in_modal` | 0/1 | Whether in a modal layer |
| `submode` | 0-4 | Overlay state within Ins mode |
| `mouse_from_ins` | 0/1 | Whether Label mode was entered from Ins |

### Invariants

1. **in_modal = (mode >= 2 ? 1 : 0)** — Must always hold. If out of sync, behavior breaks.
2. **submode = 0 when mode != 1** — Submodes only exist within Ins mode.
3. **mouse_from_ins only matters when mode = 13** — Controls Label mode return destination.

## Global Shortcuts

These work from ANY modal layer (mode >= 2):

| Shortcut | State Change |
|----------|--------------|
| right_ctrl alone | mode=0, in_modal=0, submode=0 |
| Ctrl+J | mode=1, in_modal=0 |
| Panic | mode=0, in_modal=0, submode=0, mouse_from_ins=0 |

Note: Ctrl+N is NOT global. It only exits specific layers (Label, Grid, App/Window switcher).

## Layer Entry

| Layer | Mode | Entry Key | From State |
|-------|------|-----------|------------|
| Normal | 0 | right_ctrl alone, Panic | mode >= 2 |
| Ins | 1 | J | mode = 0 |
| Ins | 1 | Ctrl+J | mode >= 2 |
| Nav | 2 | N | mode = 0 |
| Chrome | 3 | H | mode = 0, Chrome focused |
| VSCode | 4 | H | mode = 0, VSCode focused |
| TMUX | 5 | H | mode = 0, iTerm focused |
| Comma | 6 | , | mode = 0 |
| L | 7 | L | mode = 0 |
| Term | 8 | U | mode = 0 |
| Admin | 9 | I | mode = 0 |
| InApp | 10 | HK4 | mode = 0 or mode = 1 |
| Label | 13 | M | mode = 0 (sets mouse_from_ins=0) |
| Label | 13 | Ctrl+M | mode = 1 (sets mouse_from_ins=1) |
| Grid | 28 | Fn+M | mode = 0 |

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

### Click Keys (Both Modes)

| Key | Action |
|-----|--------|
| Space | Left click |
| Fn+Space | Cmd+click |
| Shift+Space | Shift+click |
| Enter | Right-click |
| Fn+Enter | Double-click |
| Shift+Enter | Cmd+Shift+click |
| Up | Hover (position only, no click) |

## Ins Mode Submodes

When mode=1, submode modifies the next keypress:

| submode | Name | Trigger | Effect | Clears After |
|---------|------|---------|--------|--------------|
| 0 | Normal | (default) | Keys pass through | — |
| 1 | shift_mirror_oneshot | Fn+] | Next mirrored letter → uppercase | One letter |
| 2 | shift_oneshot | Fn+Space | Next letter → uppercase | One letter |
| 3 | rcmd_h_mode | rcmd+H held | J/K/M/, → delete word/line | Chord complete |
| 4 | rcmd_n_mode | rcmd+N held | J/K/M/, → select word/line | Chord complete |

## App/Window Switcher (modes 11, 12)

Entry from InApp layer (mode=10):
- Up → mode=11 (App switcher), holds Cmd
- Down → mode=12 (Window switcher), holds Cmd

While in switcher:
- J/K cycles through apps/windows
- Enter selects and exits (releases Cmd, returns to Normal)
- Ctrl+N exits without selecting (releases Cmd, returns to Normal)
