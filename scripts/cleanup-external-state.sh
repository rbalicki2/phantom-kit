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
#     --held-modifiers reset

set -e

# Validate exactly 10 arguments (5 flags + 5 values)
if [ $# -ne 10 ]; then
    echo "Error: Expected 10 arguments (5 flags + 5 values), got $#" >&2
    echo "Usage: cleanup-external-state.sh --warpd <reset|keep> --homerow <reset|keep> --scroll-timer <reset|keep> --hover-mode <reset|keep> --held-modifiers <reset|keep>" >&2
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
validate_flag "--held-modifiers" "$9" "${10}"

WARPD="$2"
HOMEROW="$4"
SCROLL_TIMER="$6"
HOVER_MODE="$8"
HELD_MODIFIERS="${10}"

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

# NOTE: Only use --held-modifiers reset for mouse modes (Label, Grid) which may hold
# synthetic modifiers for clicks. Other modes should use --held-modifiers keep to avoid
# race conditions where "key up control" cancels subsequent Ctrl+key outputs.
if [ "$HELD_MODIFIERS" = "reset" ]; then
    (osascript -e 'tell application "System Events" to key up command' \
               -e 'tell application "System Events" to key up shift' \
               -e 'tell application "System Events" to key up option' \
               -e 'tell application "System Events" to key up control' 2>/dev/null || true) &
fi
