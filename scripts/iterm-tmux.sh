#!/bin/bash
# Usage: iterm-tmux.sh <tmux-window-char>
# Opens iTerm, goes to tab 2, pane 1, sends Ctrl+A + window char

WINDOW_CHAR="$1"

osascript - "$WINDOW_CHAR" <<'EOF'
on run argv
    set windowChar to item 1 of argv
    tell application "iTerm" to activate
    delay 0.1
    tell application "System Events"
        keystroke "2" using command down
        delay 0.05
        keystroke "1" using {command down, option down}
        delay 0.05
        keystroke "a" using control down
        delay 0.05
        keystroke windowChar
    end tell
end run
EOF
