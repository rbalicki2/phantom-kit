#!/bin/bash
# Script to focus iTerm and switch to tmux pane 3

PROJECT=$(cat /tmp/karabiner-project 2>/dev/null)
if [ "$PROJECT" = "pin" ]; then
    TAB=1
else
    TAB=2
fi

# Single osascript: activate iTerm, select tab, select first pane, send tmux keys
osascript <<EOF &
tell application "iTerm"
    activate
    tell current window to select tab $TAB
    tell current window to tell current tab to select first session
end tell
delay 0.02
tell application "System Events"
    keystroke "a" using control down
    keystroke "3" using shift down
end tell
EOF

# Update layer indicator
echo altins > /tmp/karabiner-layer
/opt/homebrew/bin/hs -c 'showLayerOverlay()' &
