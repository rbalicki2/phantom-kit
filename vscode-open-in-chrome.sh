#!/bin/bash
# Copy relative path from VS Code and open in Chrome with prefix

# First, copy relative path (Shift+Alt+Cmd+C)
osascript -e 'tell app "System Events" to keystroke "c" using {shift down, option down, command down}'
sleep 0.3

# Check project mode
PROJECT=$(cat /tmp/karabiner-project 2>/dev/null)

if [ "$PROJECT" = "pin" ]; then
    # Open Work Chrome profile
    /Users/rbalicki/code/voicemode/chrome-tab.sh work
    sleep 0.2
    # New tab, type cc + paste + enter
    osascript <<EOF
tell application "System Events"
    keystroke "t" using command down
    delay 0.1
    keystroke "cc"
    keystroke "v" using command down
    delay 0.05
    key code 36
end tell
EOF
else
    # Open Personal Chrome profile
    /Users/rbalicki/code/voicemode/chrome-tab.sh personal
    sleep 0.2
    # New tab, type isof + paste + enter
    osascript <<EOF
tell application "System Events"
    keystroke "t" using command down
    delay 0.1
    keystroke "isof"
    keystroke "v" using command down
    delay 0.05
    key code 36
end tell
EOF
fi

echo > /tmp/karabiner-layer
