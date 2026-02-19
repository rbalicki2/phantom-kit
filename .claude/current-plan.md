# Current Plan

## Active Tasks

### 1. Fix Command behavior in AltIns mode (layer 7)

In AltIns mode (layer 7), Command seems to pass through unexpectedly. User wants Command to go to end of line (like Cmd+Right typically does).

Investigation needed:
- Check what Cmd+key combinations do in AltIns
- Add appropriate bindings for Command+navigation

### 2. Investigate gibberish keycode display in iTerm

When in various modes, certain key combinations cause iTerm to display gibberish/escape sequences. This suggests some modifier+key combos are passing through unhandled.

Investigation steps:
- Use Karabiner EventViewer to identify which key combinations cause this
- Check if these combos are unbound in current layer
- Either bind them to nothing or add to reserved keys list

## Notes

- `timeout` command not available on macOS
- App bundle IDs: com.google.Chrome, com.googlecode.iterm2, com.microsoft.VSCode
