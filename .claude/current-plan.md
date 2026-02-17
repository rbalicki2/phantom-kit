# Current Plan

## Active Tasks

### 1. Fix test output: matched_rule fields are unexpectedly null ✓ DONE

Fixed by passing full match object through `process-single-test` instead of just parsed output.

### 2. Fix RCmd chord mode rules in AltIns mode (layer 7) ✓ DONE

**Fixed:**
- Added R3056: RCmd+h → delete-chord (submode 3) in layer 7
- Changed R2220: RCmd+n from submode 3 → submode 4 in layer 7
- Removed R0081: RCmd+comma in Insert mode (layer 1)
- Removed R2196: RCmd+comma in AltIns mode (layer 7)
- Updated alt-insert-mode.md documentation

### 3. Confirm validation exists for actions starting with character/key

**Status**: Validation already exists in `validate-extras.bb`:
- `check-action-starts-with-variable` - errors if action starts with `["var" value]`
- `check-action-starts-with-shell` - errors if action starts with `{:shell "..."}`

This effectively requires actions to start with a key code like `:vk_none`.

### 4. Remove shell: pattern (outdated)

**Issue**: The `{:shell "..."}` pattern in actions is outdated. Need to:
1. Add a validation to surface all rules using shell: pattern
2. Remove/replace shell: patterns from rules
3. Run the validation and confirm no rules use shell:

**Status**: Not started

### 5. Create delete-rule.bb script ✓ DONE

Created `scripts/edit/delete-rule.bb` to delete rules by ID.

## Completed Tasks

### Unit Test Infrastructure (completed 2024-02-17)

Created comprehensive snapshot testing for the Karabiner state machine:

1. `scripts/test/generate-tests.bb` - BFS test generator
   - Explores all reachable states from root (layer=0, submode=-1, return_to=-1)
   - Tests every key/modifier/app combination in each state
   - Generated 12,200 tests covering 26 states

2. `scripts/test/run-tests.bb` - Test runner/validator
   - Runs all tests and compares actual output to expected
   - Normalizes output formats for comparison
   - Supports `--verbose`, `--fail-fast`, `--filter` options

3. Integration
   - Tests run automatically as part of `npm run sync`
   - Tests run after validation, before deployment

## Notes

- `timeout` command not available on macOS
- App bundle IDs: com.google.Chrome, com.googlecode.iterm2, com.microsoft.VSCode
