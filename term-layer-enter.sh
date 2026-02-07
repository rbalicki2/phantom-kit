#!/bin/bash
# Script to set up term layer: focus correct iTerm tab and tmux window

PROJECT=$(cat /tmp/karabiner-project 2>/dev/null)
if [ "$PROJECT" = "pin" ]; then
    TAB=1
else
    TAB=2
fi

# Focus iTerm tab based on project
osascript -e "tell application \"iTerm\" to tell current window to select tab $TAB"

# Small delay to ensure tab switch completes
sleep 0.05

# Focus first (left) pane in the tab
osascript -e 'tell application "iTerm" to tell current window to tell current tab to select first session'

# Send tmux prefix (Ctrl+A) then % for window 5
osascript -e 'tell app "System Events" to keystroke "a" using control down'
osascript -e 'tell app "System Events" to keystroke "5" using shift down'

# Update layer indicator
echo term > /tmp/karabiner-layer
