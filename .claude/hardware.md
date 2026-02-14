# Hardware - Kinesis Advantage 360

Physical keyboard layout and firmware configuration.

## One-Handed (RHS) Setup

This is a **right-hand only** keyboard setup. All layer keys (H, J, K, L, M, N, comma, etc.) are on the right side.

**Implications:**
- If you're setting up anything requiring LHS keys, confirm with user first
- Always use **right_command** (`!Q`) and **right_control**, not left modifiers
- LHS letters accessed via Fn+letter mirroring

## Right Thumb Cluster

```
+------+------+
| Ctrl | Cmd  |
+------+------+
| PgUp | PgDn |
+------+------+
| Enter| Space|
+------+------+
```

- **Ctrl** = right_control (layer exits, modifier combos)
- **Cmd** = right_command (word/line navigation)
- **Page Up/Down** = left/right click
- **Enter** = return_or_enter
- **Space** = spacebar

## Kinesis Fn Layer Mappings

The Kinesis Fn key is handled at firmware level - it does NOT send any keycode. Only Fn+key combinations are detectable (they send F-keys or Alt+F-keys).

### Top Row
- Hotkey 3 → Alt+F20 (layer overlay trigger)
- Y → F15, U → F16, I → F17, O → F18, P → F19, \ → F20

### Middle Row
- Hotkey 4 → F21 (not usable in Karabiner)
- H → F22, J → F23, K → F24
- L → Alt+F1, ; → Alt+F2, ' → Alt+F3

### Bottom Row
- N → Alt+F4, M → Alt+F5, , → Alt+F6, . → Alt+F7, / → Alt+F8

### Numbers (default Kinesis, offset by 1)
- 6 → F7, 7 → F8, 8 → F9, 9 → F10, 0 → F11

### Navigation and Brackets
- Up → Alt+F9, Down → Alt+F10, [ → Alt+F11, ] → Alt+F12

### Right Thumb Cluster
- PageUp → Alt+F16, PageDown → Alt+F17, Enter → Alt+F18, Space → Alt+F20

## Layout File

The firmware layout is stored in `src/kinesis-layout1.txt`.

**To update:**
```bash
npm run kinesis  # Copies to all 9 layout slots on /Volumes/ADV360
```

The drive is only mounted when keyboard is in programming mode.

**Syntax**: `{key}>{output}` maps Fn+key to output. Modifier+key triggers use `{rctr}{hk4}>{esc}`.

**Key codes**: See `src/kinesis-keycodes.txt` for valid key names.

## Important Limitations

1. **Fn+Shift cannot be combined** - hardware/firmware limitation
2. **Fn key is NOT detectable** - only Fn+key combinations
3. **Fn layer sends left Alt** - use `!O` not `!E` in Karabiner rules
4. **Avoid F13 and F14** - these open macOS System Settings

## Known Issues

- Kinesis sometimes loses state (not a Karabiner issue)
- If left_command acts like control, unplug/replug the keyboard
