# Current Plan

## Active Tasks

### 1. Audit external state cleanup on mode exit
**Status:** In progress

When leaving certain modes, external state needs to be cleared. Currently need to audit:
- J/K hold state (scrolling)
- Cmd-M hold state (command hold)
- Other external state that might leak between modes

Rules that exit modes should call `cleanup-external-state.sh` with appropriate flags. Need to verify all mode transitions properly clean up.

## Notes

- `timeout` command not available on macOS - need gtimeout or alternative approach
- Caps mode in AltIns: non-letter keys should fall through and exit caps, not be blocked
