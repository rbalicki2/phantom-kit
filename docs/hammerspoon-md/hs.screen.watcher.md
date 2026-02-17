# Hammerspoon docs: hs.screen.watcher
Watch for screen layout changes
This could be the addition or removal of a monitor, a screen resolution change, movement of a monitor in the Display preferences pane, etc.

Note that screen events which happen while your Mac is suspended, may not trigger the watcher in various circumstances (e.g. if you have FileVault enabled and the machine resumes out of hibernation - the screen events will be happening before the drive is unlocked and will not be reported to Hammerspoon)

## API Reference
