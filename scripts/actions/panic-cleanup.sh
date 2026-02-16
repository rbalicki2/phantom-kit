#!/bin/bash
# panic-cleanup.sh
# Full external state reset for panic mode (Fn+hk3).
# Resets ALL external state unconditionally.
#
# This is the ONLY place where held modifiers are reset, because:
# - osascript 'key up <modifier>' clears BOTH synthetic modifier state
#   AND interferes with physical/Karabiner-emitted keystrokes
# - If used during normal transitions, it can cancel Ctrl+key outputs
#   that Karabiner is actively emitting (race condition)
# - Karabiner EventViewer shows what Karabiner emits, but the system
#   sees the 'key up' canceling the keystroke
#
# In panic mode, we're doing a full reset anyway, so this is safe.

# Kill warpd
(pkill warpd 2>/dev/null || true) &

# Dismiss Homerow labels
(/opt/homebrew/bin/hs -c 'dismissHomerow()' 2>/dev/null || true) &

# Stop scroll timer
(/opt/homebrew/bin/hs -c 'scrollStop()' 2>/dev/null || true) &

# Stop hover mode
(/opt/homebrew/bin/hs -c 'hoverModeStop()' 2>/dev/null || true) &

# Clear L-mode modifier file
(rm -f /tmp/karabiner-lmode-modifier 2>/dev/null || true) &

# Hide any persistent layer overlay
(/opt/homebrew/bin/hs -c 'hideOverlay()' 2>/dev/null || true) &

# Release any held modifiers (ONLY safe in panic mode)
(osascript -e 'tell application "System Events" to key up command' \
           -e 'tell application "System Events" to key up shift' \
           -e 'tell application "System Events" to key up option' \
           -e 'tell application "System Events" to key up control' 2>/dev/null || true) &

# Restart SwiftBar
(pkill -x SwiftBar; sleep 1; open -a SwiftBar 2>/dev/null || true) &

# Reload Hammerspoon
(/opt/homebrew/bin/hs -c 'hs.reload()' 2>/dev/null || true) &
