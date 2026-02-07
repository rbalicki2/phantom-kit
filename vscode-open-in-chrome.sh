#!/bin/bash
# Copy relative path from VS Code and open in Chrome with prefix

# First, copy relative path (Ctrl+Cmd+E)
osascript -e 'tell application "System Events" to keystroke "e" using {control down, command down}'
sleep 0.3

# Get clipboard contents
FILEPATH=$(pbpaste)

# Check project mode
PROJECT=$(cat /tmp/karabiner-project 2>/dev/null)

if [ "$PROJECT" = "pin" ]; then
    PREFIX="cc"
    /Users/rbalicki/code/voicemode/chrome-tab.sh work
else
    PREFIX="isof"
    /Users/rbalicki/code/voicemode/chrome-tab.sh personal
fi

sleep 0.2
# New tab, type prefix + filepath + enter
osascript <<EOF
tell application "System Events"
    keystroke "t" using command down
    delay 0.1
    keystroke "${PREFIX}${FILEPATH}"
    delay 0.05
    key code 36
end tell
EOF

echo > /tmp/karabiner-layer
