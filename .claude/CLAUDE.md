# Phantom Kit - How We Operate

This is the operational guide for working with this codebase. For understanding the system conceptually, read `mental-model.md`. For reference material, see `reference.md`.

## What Is This?

**Phantom Kit** is a Karabiner/Goku configuration for a one-handed (RHS only) keyboard setup on a Kinesis Advantage 360. It implements a vim-like modal layer system.

## Critical Warnings

### DO NOT BREAK EXISTING FUNCTIONALITY

There are NO unit tests. Every change risks breaking something the user relies on.

**Before making ANY change:**
1. TRACE through exactly what will happen
2. Consider ALL places the affected keys/variables are used
3. Check existing working examples in karabiner.edn FIRST
4. Make ONE small change, sync, and verify before continuing

When in doubt, ASK before changing.

### NEVER MANUALLY EDIT karabiner.edn

All modifications should be done via scripts in `scripts/`. See `scripts/README.md` for available tools.

### NEVER READ karabiner.edn DIRECTLY

**Strongly prefer using query/edit tools instead of reading the file.** The config is large and complex; tools provide structured access.

#### Query Tools (read-only)

```bash
# Find what rule matches a key press (check before adding new shortcuts)
bb scripts/query/match-rules.bb src/karabiner.edn j --layer 0
bb scripts/query/match-rules.bb src/karabiner.edn '!SOf21'  # Check if key is free

# Find ALL rules for a key across states (use --state with partial state)
bb scripts/query/match-rules.bb src/karabiner.edn p --state "profile=Default:device=Desktop"
bb scripts/query/match-rules.bb src/karabiner.edn p --state "profile=Default:device=Desktop:layer=1"

# List rules in a layer/state
bb scripts/query/list-rules.bb src/karabiner.edn "profile=Default:device=Desktop:layer=9" --format summary
bb scripts/query/list-rules.bb src/karabiner.edn "profile=Default:device=Desktop:layer=1" --exact

# Get detailed info about specific rules
bb scripts/query/describe-rules.bb src/karabiner.edn --id R0025

# Analyze rule patterns and statistics
bb scripts/query/analyze-rules.bb src/karabiner.edn
```

#### Edit Tools (modify config)

```bash
# Set a rule by ID - the primary way to add/modify rules
# Reads the rule from stdin, places it in the correct block based on its condition
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R1234 -
[{:key :j, :id "R1234 [profile=Default:device=Desktop:dsk_layer=7] j → t"} [:t ["dsk_layer" 7] ["dsk_ins_sub_mode" 0] ["dsk_return_to_layer" -1]] [["dsk_layer" 7]]]
EOFR
```

**One-off scripts** (kept for reference, rarely used):
- `remove-lhs-rules.bb` - Bulk remove rules by key pattern
- `rename-rules.bb` - Batch rename rule IDs

#### Validation Tools (run automatically on sync)

Validation runs automatically during `npm run sync`. Manual invocation rarely needed:
```bash
bb scripts/validate/validate-rules.bb src/karabiner.edn    # Core validation
bb scripts/validate/validate-extras.bb src/karabiner.edn   # Additional checks
```

See `scripts/README.md` for full documentation.

### If Tooling Returns Surprising Results

If any script returns unexpected or incorrect results, **investigate and fix the tooling** rather than working around it. The scripts are meant to be reliable; bugs should be fixed, not tolerated.

### NEVER Remove Shortcuts Without Permission

If implementing a new feature requires removing/changing an existing shortcut, STOP and ask first.

## User Interaction Notes

The user is using voice-to-text:
- Dictated letters are always uppercase; use judgment for actual case
- Reason about what the user is actually trying to accomplish
- Catch likely mistakes (e.g., "Shift+Tab" when they mean "Ctrl+Shift+Tab")
- If something can be verified mechanically, DO IT instead of asking the user
- Before adding a new variable, explicitly check with the user

**Terminology note**: "Fn+Shift" or "Function Shift" means the oneshot Shift submode (entered via Fn+]), NOT simultaneous Fn+Shift keys. Kinesis hardware prevents simultaneous Fn+Shift. See rhs-slots.md for the shift+fn column definition.

## Tool Limitations

**WebFetch is sandboxed**: The WebFetch tool cannot fetch external URLs due to sandbox restrictions. For Goku documentation, use the local copies in `docs/goku/` instead of fetching from GitHub.

## On Startup

Previous sessions sometimes leave things broken. Verify:

```bash
# 1. Check if karabiner.edn is in sync
diff /Users/rbalicki/code/voicemode/src/karabiner.edn ~/.config/karabiner.edn

# 2. Check git status
git -C /Users/rbalicki/code/voicemode status
git -C ~/.config status
```

If they differ, the voicemode version is source of truth. Run `npm run sync`.

## Workflow After Changes

1. Commit locally in voicemode repo
2. Run `npm run sync` (validates, copies to ~/.config, runs goku)
3. Commit in ~/.config repo

```bash
git add src/karabiner.edn && git commit -m "message"
npm run sync
cd ~/.config && git add karabiner.edn karabiner/karabiner.json && git commit -m "message"
```

To reload Hammerspoon: `npm run hs`

## When Adding a New Layer

- [ ] Add case to `scripts/swiftbar/karabiner-layer.100ms.sh`
- [ ] Create `src/layers/*.txt` file for Hammerspoon overlay
- [ ] Update `layerFiles` map in `~/.hammerspoon/init.lua`

## Key Documentation Files

**Essential reading** (in `.claude/`):
- **`mental-model.md`** - State variables, invariants, layer behavior. Read when adding layers or debugging state issues.
- **`reference.md`** - Layer shortcuts, mode values, Goku modifier syntax
- **`goku-lessons.md`** - Syntax pitfalls, debugging workflow, things that don't work
- **`rhs-slots.md`** - Complete key mapping grid for Ins mode (bare/fn/shift/shift+fn columns)
- **`hardware.md`** - Kinesis Fn layer mappings, physical key layout
- **`todos.md`** - Pending work and feature ideas

**Source files** (in `src/`):
- `karabiner.edn` - Main Goku config (source of truth)
- `layers/*.txt` - Hammerspoon overlay content per layer
- `kinesis-layout1.txt` - Kinesis firmware Fn layer configuration
- `kinesis-keycodes.txt` - Key code reference for Kinesis macros

**External files**:
- `~/.config/karabiner.edn` - Deployed config (goku reads from here)
- `~/.hammerspoon/init.lua` - Layer overlay config
- `/tmp/karabiner-layer` - Current layer (runtime)

## Scripts Overview

See `scripts/README.md` for complete documentation.

- **`scripts/query/`** - Read-only tools to inspect rules
- **`scripts/edit/`** - Tools to modify karabiner.edn
- **`scripts/validate/`** - Validation (runs automatically on sync)
- **`scripts/generate/`** - Generate state graphs and valid state lists
- **`scripts/actions/`** - Shell scripts called by rules at runtime
- **`scripts/swiftbar/`** - SwiftBar menu bar plugins
