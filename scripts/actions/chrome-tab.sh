#!/bin/bash
# Usage: chrome-tab.sh <personal|work> [tab_index]
# Example: chrome-tab.sh personal 2

PROFILE="${1:-personal}"
TAB_INDEX="${2:-}"

if [ "$PROFILE" = "personal" ]; then
    # Personal windows have "(Personal)" in the title
    osascript <<'EOF'
tell application "System Events"
    tell process "Google Chrome"
        set windowNames to name of every window
        repeat with i from 1 to count of windowNames
            if item i of windowNames contains "(Personal)" then
                perform action "AXRaise" of window i
                set frontmost to true
                exit repeat
            end if
        end repeat
    end tell
end tell
EOF
else
    # Work windows have "Robert" but not "(Personal)"
    osascript <<'EOF'
tell application "System Events"
    tell process "Google Chrome"
        set windowNames to name of every window
        repeat with i from 1 to count of windowNames
            set winName to item i of windowNames
            if winName contains "Robert" and winName does not contain "(Personal)" then
                perform action "AXRaise" of window i
                set frontmost to true
                exit repeat
            end if
        end repeat
    end tell
end tell
EOF
fi

# If tab index specified, switch to that tab
if [ -n "$TAB_INDEX" ]; then
    osascript -e "tell application \"Google Chrome\" to set active tab index of window 1 to $TAB_INDEX"
fi
