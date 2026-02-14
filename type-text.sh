#!/bin/bash
# Type text into the terminal via osascript
# Usage: type-text.sh "text to type" [--enter]
# Sends Ctrl+C first to clear any existing input

TEXT="$1"
ENTER="${2:-}"

if [ "$ENTER" = "--enter" ]; then
    osascript \
        -e 'tell app "System Events" to key code 8 using control down' \
        -e "tell app \"System Events\" to keystroke \"$TEXT\"" \
        -e 'tell app "System Events" to key code 36'
else
    osascript \
        -e 'tell app "System Events" to key code 8 using control down' \
        -e "tell app \"System Events\" to keystroke \"$TEXT\""
fi
