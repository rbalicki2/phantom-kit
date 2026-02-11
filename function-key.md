# Function Key Behavior (Kinesis Advantage 2)

## Right Hand Fn Layer

When Function key is held, RHS keys produce F-keys:

### Top Row (Standard F-Keys)
- Hotkey 3 → Alt+F20 **(Layer overlay trigger)**
- `Y` → F15
- `U` → F16
- `I` → F17
- `O` → F18
- `P` → F19
- `\` → F20

### Middle Row
- Hotkey 4 → F21 (not usable - Karabiner/Hammerspoon don't support F21+)
- `H` → F22
- `J` → F23
- `K` → F24
- `L` → Alt+F1
- `;` → Alt+F2
- `'` → Alt+F3

### Bottom Row (Alt Combinations)
- `N` → Alt+F4
- `M` → Alt+F5
- `,` → Alt+F6
- `.` → Alt+F7
- `/` → Alt+F8

### Numbers (default Kinesis behavior, offset by 1)
- `6` → F7
- `7` → F8
- `8` → F9
- `9` → F10
- `0` → F11

### Navigation and Brackets
- `Up` → Alt+F9
- `Down` → Alt+F10
- `[` → Alt+F11
- `]` → Alt+F12

### Right Thumb Cluster
- `PageUp` → Alt+F16
- `PageDown` → Alt+F17
- `Enter` → Alt+F18
- `Space` → Alt+F20

## Notes

- F21-F24 are seen by Karabiner-EventViewer but Goku syntax (`:f21`) doesn't support them in `from` clause when mapping to itself, and Hammerspoon doesn't recognize them as key names. However, `:f21` → something else DOES work.
- F1-F20 work in both Karabiner and Hammerspoon
- Alt+F combinations work as modifiers in Karabiner (`:!Of1` etc.)
