#!/bin/bash
# Script to set up term layer: focus correct iTerm tab and tmux window

MODE=$(cat /tmp/karabiner-mode 2>/dev/null)
if [ "$MODE" = "pin" ]; then
    TAB=1
else
    TAB=2
fi

# Focus iTerm tab based on mode
osascript -e "tell application \"iTerm\" to tell current window to select tab $TAB"

# Small delay to ensure tab switch completes
sleep 0.05

# Send tmux prefix (Ctrl+A) then % for window 5
osascript -e 'tell app "System Events" to keystroke "a" using control down'
osascript -e 'tell app "System Events" to keystroke "5" using shift down'

# Update layer indicator
echo term > /tmp/karabiner-layer
