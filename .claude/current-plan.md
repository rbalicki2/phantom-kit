# Current Plan

## Active Tasks

### Unit Test Infrastructure
**Status:** In progress

**Completed:**
1. Added `--json` flag to `match-rules.bb` for structured JSON output
2. Created `tests/inputs.json` with all keys, modifiers, profiles, devices, apps
3. Created directory structure: `tests/unit/`, `scripts/test/`
4. Documented test format in `.claude/plans/unit-tests.md`

**Next steps:**
1. Create `scripts/test/generate-tests.bb` - BFS generator
2. Create `scripts/test/run-tests.bb` - Test runner/validator
3. Add test running to `npm run sync`

**Test format (JSON):**
```json
{
  "initial_state": {
    "application": "Chrome",
    "device": "Desktop",
    "dsk_ins_sub_mode": -1,
    "dsk_layer": 0,
    "dsk_return_to_layer": -1,
    "profile": "Default"
  },
  "key": {"key": "j"},
  "comment": "Enter AltIns mode",
  "to": {
    "resulting_state": {...},
    "actions": [...]
  },
  "held": null,
  "afterup": null,
  "alone": null
}
```

**BFS algorithm:**
- Queue contains states (layer, submode, return_to) - not keys
- Pop state → generate tests for ALL key/modifier/app combos
- Queue any new resulting_states from to/held/afterup/alone
- Track visited states to avoid duplicates

## Notes

- `timeout` command not available on macOS
- App bundle IDs: com.google.Chrome, com.googlecode.iterm2, com.microsoft.VSCode
