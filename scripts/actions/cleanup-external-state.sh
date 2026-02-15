#!/bin/bash
# cleanup-external-state.sh
# Clears external state for Karabiner layer transitions.
# All 5 flags are required, in this exact order, each with "reset" or "keep".
#
# Usage:
#   cleanup-external-state.sh \
#     --warpd reset \
#     --homerow reset \
#     --scroll-timer reset \
#     --hover-mode reset \
#     --lmode-modifier reset
#
# NOTE: This script intentionally does NOT reset held modifiers.
# See panic-cleanup.sh for why, and use that script for full resets.

set -e

# Validate exactly 10 arguments (5 flags + 5 values)
if [ $# -ne 10 ]; then
    echo "Error: Expected 10 arguments (5 flags + 5 values), got $#" >&2
    echo "Usage: cleanup-external-state.sh --warpd <reset|keep> --homerow <reset|keep> --scroll-timer <reset|keep> --hover-mode <reset|keep> --lmode-modifier <reset|keep>" >&2
    exit 1
fi

# Validate order and values
validate_flag() {
    local expected_flag="$1"
    local actual_flag="$2"
    local value="$3"

    if [ "$actual_flag" != "$expected_flag" ]; then
        echo "Error: Expected $expected_flag but got $actual_flag" >&2
        exit 1
    fi

    if [ "$value" != "reset" ] && [ "$value" != "keep" ]; then
        echo "Error: $expected_flag must be 'reset' or 'keep', got '$value'" >&2
        exit 1
    fi
}

validate_flag "--warpd" "$1" "$2"
validate_flag "--homerow" "$3" "$4"
validate_flag "--scroll-timer" "$5" "$6"
validate_flag "--hover-mode" "$7" "$8"
validate_flag "--lmode-modifier" "$9" "${10}"

WARPD="$2"
HOMEROW="$4"
SCROLL_TIMER="$6"
HOVER_MODE="$8"
LMODE_MODIFIER="${10}"

# Execute cleanups (all run in background for non-blocking behavior)
if [ "$WARPD" = "reset" ]; then
    (pkill warpd 2>/dev/null || true) &
fi

if [ "$HOMEROW" = "reset" ]; then
    (/opt/homebrew/bin/hs -c 'dismissHomerow()' 2>/dev/null || true) &
fi

if [ "$SCROLL_TIMER" = "reset" ]; then
    (/opt/homebrew/bin/hs -c 'scrollStop()' 2>/dev/null || true) &
fi

if [ "$HOVER_MODE" = "reset" ]; then
    (/opt/homebrew/bin/hs -c 'hoverModeStop()' 2>/dev/null || true) &
fi

if [ "$LMODE_MODIFIER" = "reset" ]; then
    (rm -f /tmp/karabiner-lmode-modifier 2>/dev/null || true) &
fi
