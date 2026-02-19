# Current Plan

## Active Tasks

### 1. Fix Screenshot Ctrl+P in Admin layer

Admin layer (layer 9) has shortcuts:
- P = Screenshot full → R0275 exists, sends Cmd+Shift+3 ✓
- Ctrl+P = Screenshot selection → **NO RULE EXISTS**

Fix:
- Get next rule ID
- Add rule: `{:key :p :modi {:mandatory [:right_control]}} → [:!CS4 ...]` in layer 9

### 2. Fix Command behavior in AltIns mode (layer 7)

In AltIns mode (layer 7), Command seems to pass through unexpectedly. User wants Command to go to end of line (like Cmd+Right typically does).

Investigation needed:
- Check what Cmd+key combinations do in AltIns
- Add appropriate bindings for Command+navigation

### 3. Investigate gibberish keycode display in iTerm

When in various modes, certain key combinations cause iTerm to display gibberish/escape sequences. This suggests some modifier+key combos are passing through unhandled.

Investigation steps:
- Use Karabiner EventViewer to identify which key combinations cause this
- Check if these combos are unbound in current layer
- Either bind them to nothing or add to reserved keys list

## Notes

- `timeout` command not available on macOS
- App bundle IDs: com.google.Chrome, com.googlecode.iterm2, com.microsoft.VSCode
