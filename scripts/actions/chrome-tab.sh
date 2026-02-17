#!/bin/bash
# Usage: chrome-tab.sh <personal|work> [tab_index]
# Example: chrome-tab.sh personal 2

PROFILE="${1:-personal}"
TAB_INDEX="${2:-}"

# Use Hammerspoon for fast profile window focus (timeout to avoid hangs)
timeout 2 /opt/homebrew/bin/hs -c "focusChromeProfile('$PROFILE')" 2>/dev/null

# If tab index specified, switch to that tab
if [ -n "$TAB_INDEX" ]; then
    timeout 2 /opt/homebrew/bin/hs -c "chromeTab($TAB_INDEX)" 2>/dev/null
fi
