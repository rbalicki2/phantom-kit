#!/bin/bash
# Hard restart Hammerspoon - kills process and relaunches
# Use when Hammerspoon gets into a broken state

pkill -9 Hammerspoon 2>/dev/null
sleep 1
open -a Hammerspoon
