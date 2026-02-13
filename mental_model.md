# Phantom Kit Mental Model

Focus: State transitions, invariants, and behaviors that must be preserved.

## State Variables

Three variables track all state:
- `mode` (0-28): Current layer
- `in_modal` (0/1): 1 when mode >= 2 (modal layers)
- `submode` (0-4): Overlay state within Ins mode only

**Invariant**: `in_modal` must equal `(mode >= 2 ? 1 : 0)`. If these get out of sync, behavior breaks.

## Global Shortcuts (Work From Any Layer)

These MUST work regardless of current mode:

| Shortcut | Action | Notes |
|----------|--------|-------|
| **right_ctrl alone** | Exit to Normal | Primary escape from any modal layer |
| **Ctrl+J** | Exit to Ins | Quick return to typing mode |
| **Ctrl+N** | Exit to Normal + Escape | Global escape fallback |
| **Ctrl+Y** | Toggle recording (Wispr) | Works everywhere except Admin layer |
| **Panic (Fn+HK3)** | Full reset | Clears ALL state, kills warpd, releases modifiers |

**Panic Button (Shift+Alt+F19)**: Emergency reset. Sets `mode=0`, `in_modal=0`, `submode=0`, kills warpd, dismisses Homerow, releases held modifiers. Use when keyboard gets stuck.

## Layer Categories

### Non-Modal (in_modal=0)
- **Normal (0)**: Layer selector, most keys disabled
- **Ins (1)**: Typing mode, keys pass through

### Modal (in_modal=1)
All others. Must exit via right_ctrl or action that changes mode.

## State Transitions

### Entry to Normal (mode=0)
- right_ctrl alone from any modal layer
- Ctrl+N from anywhere
- Panic button
- Actions that explicitly exit to Normal (e.g., copy operations)

### Entry to Ins (mode=1)
- J from Normal
- Ctrl+J from any modal layer
- Actions that open text input (address bar, search, new tab, etc.)

### Entry to Modal Layers
All from Normal unless noted:
- **Nav (2)**: N
- **Chrome/VSCode/TMUX (3/4/5)**: H (app-dependent)
- **Comma (6)**: ,
- **L (7)**: L
- **Term (8)**: U (also focuses iTerm)
- **Admin (9)**: I
- **InApp (10)**: Fn+HK4 (also from Ins)
- **Label (13)**: M from Normal, Ctrl+M from Ins
- **Grid (28)**: Fn+M from Normal only

### Exit Behavior Rules

**Exit to Normal**: Actions that don't require typing
- Copy, close, window management, undo/redo

**Exit to Ins**: Actions that open text input
- Address bar, search, find, new tab/file, command palette
- Paste (cursor in text field after)

**Stay in Layer**: Repeatable actions
- Tab switching, scrolling, delete operations

## Mouse Modes (Label & Grid)

### Entry
- **Label**: M from Normal (returns to Normal), Ctrl+M from Ins (returns to Ins)
- **Grid**: Fn+M from Normal only

`mouse_from_ins` variable tracks origin for Label mode return.

### Click Handling
1. User selects target (labels or grid navigation)
2. User presses click key (Space, Enter, etc.)
3. **Grid**: Kill warpd first (mouse stays positioned), then Hammerspoon clicks
4. **Label**: Hammerspoon intercepts Homerow's click, performs our click instead

### Click Actions (Both Modes)
| Key | Action |
|-----|--------|
| Space | Left click |
| Fn+Space | Cmd+click |
| Shift+Space | Shift+click |
| Enter | Right-click |
| Fn+Enter | Double-click |
| Shift+Enter | Cmd+Shift+click |
| Up | Hover (position mouse, no click) |

Hammerspoon functions: `clickLeft()`, `clickRight()`, `clickDouble()`, `clickShift()`, `clickCmd()`, `clickCmdShift()`, `labelRightClick()`, `labelDoubleClick()`, etc.

## Files That Must Stay In Sync

When changing shortcuts, update ALL of these:

| File | Purpose | Update When |
|------|---------|-------------|
| `karabiner.edn` | Source of truth for rules | Any shortcut change |
| `mental_model.md` | State transitions & invariants | Any state-changing behavior |
| `layers/*.txt` | Hammerspoon overlay content | Layer shortcut changes |
| `~/.hammerspoon/init.lua` | Click handlers, overlays | Mouse/click behavior changes |
| `warpd.conf` | Grid mode settings | Grid navigation changes |
| `kinesis-layout1.txt` | Kinesis Fn layer mappings | F-key assignments |
| `/tmp/karabiner-layer` | Current layer (runtime) | Layer entry/exit rules |

### Kinesis Layout
The Kinesis Advantage 360 Fn layer maps keys to F-keys:
- Managed via `kinesis-layout1.txt`
- Copy to `/Volumes/ADV360/layouts/` when keyboard is in programming mode
- `npm run kinesis` copies to all 9 layout slots

Key mappings:
- Fn+HK3 → Shift+Alt+F19 (panic)
- Fn+HK4 → F21
- Bare HK3 → Alt+F19
- Bare HK4 → Alt+F20 (enters InApp)

## Ins Mode Submodes

Within Ins (mode=1), `submode` tracks overlays:
- 0: Normal typing
- 1: shift_mirror_oneshot (Fn+], next mirrored letter uppercase)
- 2: shift_oneshot (Fn+Space, next letter capitalized)
- 3: rcmd_h_mode (delete chord)
- 4: rcmd_n_mode (select chord)

Submodes auto-clear after one keypress.