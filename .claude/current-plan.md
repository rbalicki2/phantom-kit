# Current Plan

## Active Tasks

### 1. Investigate gibberish keycode display in iTerm

When in various modes, certain key combinations cause iTerm to display gibberish/escape sequences. This suggests some modifier+key combos are passing through unhandled.

Investigation steps:
- Use Karabiner EventViewer to identify which key combinations cause this
- Check if these combos are unbound in current layer
- Either bind them to nothing or add to reserved keys list

## Notes

- `timeout` command not available on macOS
- App bundle IDs: com.google.Chrome, com.googlecode.iterm2, com.microsoft.VSCode
