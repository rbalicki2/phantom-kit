# Karabiner Tooling Plan

## Vision

Treat `karabiner.edn` as a **compilation target**, not a hand-edited file. The config is conceptually a nested hashmap:

```
Profile → Layer → Submode → Key → [Actions]
```

Rules at higher levels (less specific) act as fallbacks for rules below them.

## Goals

### 1. Accurate Rule State Markers

Every rule ID should contain a valid, parseable state string that exactly matches its conditions:

**Current (broken):**
```
R0064 [layer:1] [Desktop, Layer 1, Submode 3] rcmd+H chord
```
- ID says `[layer:1]` but block name says "Submode 3"
- Actual condition is `["dsk_layer" 1]` (no submode check)
- This rule ENTERS submode 3, it doesn't CHECK for submode 3

**Target:**
```
R0064 [profile=Desktop:dsk_layer=1] Enter rcmd+H chord (submode 3)
```
- State string uses exact variable names
- State string is validated to match actual conditions
- Block name accurately describes behavior

### 2. State String Format

Use exact variable names with `key=value` pairs:

```
profile=Desktop:dsk_layer=1:dsk_ins_sub_mode=0
```

Benefits:
- Parseable and validatable
- Matches variable names in code
- Unambiguous

### 3. Tooling

**list-rules.bb** (done):
- `--exact` flag uses ID state markers for filtering
- Hierarchical mode uses actual conditions
- Can query rules by state

**Validation** (partial):
- Left-modifier validation added
- TODO: Validate ID state strings match actual conditions
- TODO: Validate IDs are valid state strings

**Future:**
- Higher-level DSL that compiles to Goku EDN
- Auto-generate rule IDs with correct state strings
- Type-safe layer definitions

## Current State

### What Works
- `list-rules.bb` with `--exact` correctly filters by ID state marker
- `list-rules.bb` hierarchical mode shows all rules that would apply
- Left-modifier validation catches left_shift/left_control/left_command in Desktop inputs
- Sync validates and deploys config

### What's Broken
- Many rule IDs have incorrect state markers (say `[layer:1]` but named "Submode X")
- No validation that ID state strings match actual conditions
- Rules named "Submode 0" are actually catch-alls (no submode condition)

### Immediate TODOs
1. Fix rule IDs to accurately reflect conditions
2. Add validation: ID state string must match actual rule conditions
3. Standardize state string format to `profile=Desktop:dsk_layer=1:dsk_ins_sub_mode=0`

## Example Queries

```bash
# All rules that WOULD APPLY in layer 1 (hierarchical)
bb scripts/list-rules.bb src/karabiner.edn "profile=Desktop:layer=1" --format summary

# Only rules DEFINED AT exactly layer 1 (no submode in ID)
bb scripts/list-rules.bb src/karabiner.edn "profile=Desktop:layer=1" --exact --format summary

# Only rules DEFINED AT layer 1, submode 1
bb scripts/list-rules.bb src/karabiner.edn "profile=Desktop:layer=1:submode=1" --exact --format ids
```

## TODO List

### Immediate (this session)
- [ ] Fix rule IDs to use new state string format: `profile=Desktop:dsk_layer=1:dsk_ins_sub_mode=0`
- [ ] Add validation: ID state string must match actual rule conditions
- [ ] Fix mislabeled rules (e.g., "Submode 3" rules that are actually layer-1 entry points)

### Short-term
- [ ] Script to auto-regenerate all rule IDs based on actual conditions
- [ ] Validate state strings during `npm run sync`
- [ ] Update block names to accurately describe behavior (not target state)

### Medium-term
- [ ] Higher-level DSL that compiles to Goku EDN
- [ ] Auto-generate documentation from config
- [ ] Type-safe layer definitions (possibly Rust macros)

### Completed
- [x] Add `--exact` flag to list-rules.bb
- [x] Make `--exact` use ID state markers (not actual conditions)
- [x] Infer layer=1 for submode-only ID markers
- [x] Add left-modifier validation to validate-extras.bb
- [x] Document conceptual model (nested hashmap) in mental-model.md
- [x] Fix oneshot clear rules to match either shift key
- [x] Add Shift+backslash = pipe mapping
