# Current Plan

## Active Tasks

### 1. External State Cleanup Audit - Completed
**Status:** Completed

**External State Types:**
1. **Warpd process** - Killed by `cleanup-external-state.sh --warpd reset`
2. **Homerow labels** - Dismissed by `panic-cleanup.sh` only (Hammerspoon IPC)
3. **Scroll timer (J/K hold)** - Stopped by `afterup` handler on J/K release (R0358, R0359)
4. **Hover mode** - Stopped by `panic-cleanup.sh` only
5. **L-mode modifier file** - Cleaned by `cleanup-external-state.sh --lmode-modifier reset`
6. **Layer overlay** - Hidden when new overlay is shown, or by `panic-cleanup.sh`

**Submode State (via afterup):**
- Delete chord (submode 3): RCmd+h in Ins (R0064), RCmd+n in AltIns (R2220)
- Select chord (submode 4): RCmd+n in Ins (R0071), RCmd+comma in AltIns (R2196)
- All have `afterup` handlers to clear submode when RCmd is released

**Findings:**
- Most exit rules use `cleanup-external-state.sh` with all flags
- `--homerow reset` now calls `dismissHomerow()` to clear labels on mode exit
- `--scroll-timer reset` and `--hover-mode reset` are still no-ops (panic mode only)
- J/K scroll cleanup relies on `afterup` - if key release isn't detected, scrolling continues
- Chord submode cleanup relies on `afterup` - same concern

**Risk Assessment:**
- **Low risk:** The `afterup` mechanism should fire when keys are released regardless of layer changes
- **Mitigation:** Panic mode (Fn+hk3) is available for full state reset if needed

## Notes

- `timeout` command not available on macOS - need gtimeout or alternative approach
- Caps mode in AltIns: non-letter keys should fall through and exit caps, not be blocked
