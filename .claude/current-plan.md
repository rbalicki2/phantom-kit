# Current Plan

## Active Tasks

### 1. Change InApp mode up/down bindings

**Problem:** In InApp mode (layer 10), up/down currently trigger AppSwitcher and WindowSwitcher. This is problematic because after switching tabs (e.g., to Claude), using up/down to navigate permission dialogs accidentally enters app/window switching mode.

**Current bindings (layer 10):**
- R0389: up_arrow → Enter AppSwitcher (Cmd+Tab)
- R0390: down_arrow → Enter WindowSwitcher (Cmd+`)

**Fix needed:**
- Change up/down in InApp to pass through (so they work normally for navigation)
- Find new keys for AppSwitcher and WindowSwitcher entry
- Keep the AppSwitcher (layer 11) and WindowSwitcher (layer 12) modes themselves - just change how to enter them

**Suggested approach:** Pick different keys in InApp mode for app/window switching. Ask user which keys they want.

### 2. Fix Screenshot Ctrl+P in Admin layer

Admin layer (layer 9) has shortcuts:
- P = Screenshot full → R0275 exists, sends Cmd+Shift+3 ✓
- Ctrl+P = Screenshot selection → **NO RULE EXISTS**

Fix:
- Get next rule ID
- Add rule: `{:key :p :modi {:mandatory [:right_control]}} → [:!CS4 ...]` in layer 9

### 3. Fix Command behavior in AltIns mode (layer 7)

In AltIns mode (layer 7), Command seems to pass through unexpectedly. User wants Command to go to end of line (like Cmd+Right typically does).

Investigation needed:
- Check what Cmd+key combinations do in AltIns
- Add appropriate bindings for Command+navigation

### 4. Investigate gibberish keycode display in iTerm

When in various modes, certain key combinations cause iTerm to display gibberish/escape sequences. This suggests some modifier+key combos are passing through unhandled.

Investigation steps:
- Use Karabiner EventViewer to identify which key combinations cause this
- Check if these combos are unbound in current layer
- Either bind them to nothing or add to reserved keys list

## Notes

- `timeout` command not available on macOS
- App bundle IDs: com.google.Chrome, com.googlecode.iterm2, com.microsoft.VSCode
