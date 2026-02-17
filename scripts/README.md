# Scripts

Tools for managing the Karabiner/Goku configuration.

## Directory Structure

```
scripts/
├── actions/     # Shell scripts called during Karabiner rules
├── query/       # Query the EDN config (read-only)
├── edit/        # Modify the EDN config
├── validate/    # Validation scripts (run on sync)
├── generate/    # Generate rules programmatically
├── swiftbar/    # SwiftBar menu bar plugins
├── lib/         # Shared Babashka libraries
├── test/        # Unit tests
└── misc/        # One-off utilities
```

## State String Format

All query scripts accept a unified state string format:

```
layer=1                              # Ins mode
layer=1:submode=1                    # Mirror mode
device=Desktop:layer=1               # Desktop, Ins mode
profile=Default:device=Desktop       # Full specification
```

## Query Scripts (read-only)

### query/list-rules.bb
List rules that apply at a given state.

```bash
bb query/list-rules.bb src/karabiner.edn "layer=1:submode=1" --format summary
bb query/list-rules.bb src/karabiner.edn "layer=1" --exact
```

### query/match-rules.bb
Find rules matching a specific key + modifiers + state.

```bash
bb query/match-rules.bb src/karabiner.edn j --layer 0
bb query/match-rules.bb src/karabiner.edn p --state "device=Desktop:layer=1"
```

### query/describe-rules.bb
Generate human-readable descriptions for all rules.

### query/analyze-rules.bb
Analyze rules for common issues (submode/return-to mismatches).

```bash
bb query/analyze-rules.bb src/karabiner.edn
bb query/analyze-rules.bb src/karabiner.edn --state "layer=1"
```

## Edit Scripts (modify EDN)

### edit/set-rule.bb
Set a rule by ID (delete existing, then add). Reads rule from stdin.

```bash
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R1234 -
[{:key :!Of9, :id "R1234 [profile=Default:device=Desktop:layer=1:submode=2]"} [:!Sgrave] [["dsk_layer" 1] ["dsk_ins_sub_mode" 2]]]
EOFR
```

### edit/rename-rules.bb
Batch rename rule IDs or descriptions.

### edit/normalize-edn.bb
Normalize EDN formatting.

## Validation Scripts

Run automatically via `npm run sync`.

### validate/validate-rules.bb
Core validation: invariants, shadowing, catch-all ordering.

### validate/validate-extras.bb
Additional checks: syntax, layer transitions, LHS keys, etc.

### validate/validate-edn.bb
EDN syntax validation.

### validate/fix-rule-ids.bb
Regenerate rule IDs to be sequential.

## Action Scripts (called by rules)

### actions/cleanup-external-state.sh
Reset external state (warpd, homerow, etc.) on layer transitions.

### actions/chrome-tab.sh
Chrome tab manipulation.

### actions/cycle-project.sh
Cycle through project modes.

### actions/term-layer-enter.sh
Actions when entering Term layer.

## SwiftBar

### swiftbar/karabiner-layer.100ms.sh
Menu bar indicator showing current layer.

## Running Tests

```bash
bb test/match-rules-test.bb
```
