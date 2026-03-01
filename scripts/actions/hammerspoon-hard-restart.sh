#!/bin/bash
# Hard restart Hammerspoon - kills process and relaunches
# Use when Hammerspoon gets into a broken state

pkill -9 Hammerspoon 2>/dev/null

# Clean up scroll state files so scrolling doesn't resume on restart
rm -f /tmp/hs-scroll-down /tmp/hs-scroll-up 2>/dev/null

sleep 1
open -a Hammerspoon
