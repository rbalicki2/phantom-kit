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
bb scripts/query/list-rules.bb src/karabiner.edn "profile=Desktop:layer=9" --format summary
bb scripts/query/list-rules.bb src/karabiner.edn "profile=Desktop:layer=1" --exact

# Get detailed info about specific rules
bb scripts/query/describe-rules.bb src/karabiner.edn --id R0025

# Analyze rule patterns and statistics
bb scripts/query/analyze-rules.bb src/karabiner.edn
```

#### Edit Tools (modify config)

```bash
# Set a rule by ID (delete existing, then add) - reads from stdin
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R1234 -
[{:key :!Of9, :id "R1234 [profile=Default:device=Desktop:layer=1:submode=2]"} [:!Sgrave] [["dsk_layer" 1] ["dsk_ins_sub_mode" 2]]]
EOFR

# Remove rules by key pattern
bb scripts/edit/remove-lhs-rules.bb src/karabiner.edn src/karabiner.edn --key '!Of19'

# Batch rename rules
bb scripts/edit/rename-rules.bb src/karabiner.edn src/karabiner.edn --pattern 'old' --replacement 'new'
```

#### Validation Tools (run automatically on sync)

```bash
bb scripts/validate/validate-rules.bb src/karabiner.edn    # Core validation
bb scripts/validate/validate-extras.bb src/karabiner.edn   # Additional checks
bb scripts/validate/fix-rule-ids.bb src/karabiner.edn      # Regenerate sequential IDs
```

See `scripts/README.md` for full documentation.

### Script Design Philosophy

The current scripts are overly complicated. When creating or modifying scripts, follow these principles:

1. **Model the underlying primitives** - Scripts should map cleanly to what the EDN file actually contains (rules, sections, conditions)
2. **Keep interfaces simple** - One operation per script. Avoid multi-mode scripts with many flags
3. **Trust auto-sorting** - Don't worry about rule order in edit scripts; sorting will handle it
4. **Prefer whole-object operations** - e.g., "replace this entire rule" rather than "modify field X of rule Y"

See `todos.md` for planned simplifications.

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

## Pre-Commit Checklist

Before committing karabiner.edn changes:

- [ ] **Only ONE `{:shell ...}` per rule** - combine with `&&`
- [ ] Layer transitions set ALL FOUR variables per mental-model.md
- [ ] Layer exits write to `/tmp/karabiner-layer` for SwiftBar
- [ ] RHS shortcuts use `right_control` not `left_control`
- [ ] Shift matching uses explicit form to match EITHER shift

When adding a new layer:
- [ ] Add case to `scripts/swiftbar/karabiner-layer.100ms.sh`
- [ ] Create `src/layers/*.txt` file for Hammerspoon overlay
- [ ] Update `layerFiles` map in `~/.hammerspoon/init.lua`

## Documentation to Keep Updated

After any keybinding changes:
- `mental-model.md` - State transitions and invariants
- `reference.md` - Shortcuts and mode values
- `src/layers/*.txt` - Hammerspoon overlay files

## File Locations

**Source (`src/`)**:
- `src/karabiner.edn` - Main Goku config (source of truth)
- `src/layers/*.txt` - Hammerspoon overlay content
- `src/kinesis-layout1.txt` - Kinesis firmware layout

**Scripts (`scripts/`)**: See `scripts/README.md` for full documentation.
- `scripts/actions/` - Shell scripts called during rules
- `scripts/swiftbar/` - SwiftBar menu bar plugins

**Documentation (`.claude/`)**:
- `CLAUDE.md` - This file (operational guide)
- `mental-model.md` - Conceptual foundation
- `reference.md` - Shortcuts and lookup tables
- `goku-lessons.md` - Syntax knowledge and debugging
- `hardware.md` - Kinesis-specific info
- `todos.md` - Pending tasks

**External**:
- `~/.config/karabiner.edn` - Deployed config (goku reads from here)
- `~/.hammerspoon/init.lua` - Layer overlay config
- `/tmp/karabiner-layer` - Current layer (runtime)
- `/tmp/karabiner-project` - Current project mode (runtime)

## Other Files to Reference

- **`mental-model.md`** - Read when: understanding state transitions, adding new layers, debugging invariant issues
- **`reference.md`** - Read when: looking up shortcuts, mode values, Goku syntax
- **`goku-lessons.md`** - Read when: unsure about syntax, debugging why something doesn't work
- **`hardware.md`** - Read when: working with Kinesis Fn layer, understanding physical key layout
- **`todos.md`** - Read when: looking for pending work or feature ideas
