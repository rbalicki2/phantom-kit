# Todos

## RHS-Only Transition - Must Have

### Mouse Mode (new layer)
Two styles needed:
- **Vimium-style hints**: Show letter labels on clickable elements, type to click
  - Could use Vimium in Chrome, but need system-wide solution
  - Options: Shortcat, Homerow, or custom Hammerspoon
- **Grid-style positioning**: Ternary search to narrow down cursor position
  - User's idea: uiop for quadrants, narrow down, up=click, down=right-click
  - Options: warpd, Hammerspoon custom, or Karabiner mouse_key

### Media Controls
- Play/pause
- Volume up/down/mute
- Next/prev track
- Could add to existing layer (Nav?) or new layer

### Scroll Controls
- Scroll up/down (page and line)
- Scroll left/right (for horizontal scroll)
- Could use Karabiner mouse_key scroll or Hammerspoon

### Escape in Insert Mode
- Need escape key access while in Ins layer (currently only right_control exits)
- Candidate: Shift+Space = Escape (stays in Ins)

### Mirror/Reverse Mode in Insert
- Goal: One-handed typing with RHS only
- Hold ' or | to activate mirror (Y→T, U→R, etc.)
- Already have layer_mirror with mappings, just need Ins-mode trigger
- Capital letters: ' + Shift + Y should = T (shift passes through)
- Consider: toggle vs hold behavior

### Project-Specific Commands in VS Code
- Shortcuts to run commands like `yarn test $currentfile`
- Depends on project (yarn vs npm, test runner, etc.)
- Could use VS Code tasks, or shell scripts that detect project type
- Maybe extend VS Code layer with project-aware shortcuts

## RHS-Only Transition - Nice to Have

### System Controls
- Brightness up/down
- Sleep/lock screen

### Tab Key Access
- Tab is on LHS - need RHS equivalent for indentation
- Shift+Tab too

### Function Keys
- F1-F12 access if needed (some apps use these)

## Existing Todos

### From CLAUDE.md
- Create a Tampermonkey setup that exposes functions callable from a layer

### Layer System
- Nav layer should not be a typing layer, but a layer chooser layer

### New Features
- Chat interface popover accessible anywhere
