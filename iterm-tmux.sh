#!/bin/bash
# Usage: iterm-tmux.sh <tmux-window-char>
# Opens iTerm, goes to tab 2, pane 1, sends Ctrl+A + window char

WINDOW_CHAR="$1"

osascript <<EOF
tell application "iTerm" to activate
delay 0.1
tell application "System Events"
    keystroke "2" using command down
    delay 0.05
    keystroke "1" using {command down, option down}
    delay 0.05
    keystroke "a" using control down
    delay 0.05
    keystroke "$WINDOW_CHAR"
end tell
EOF
