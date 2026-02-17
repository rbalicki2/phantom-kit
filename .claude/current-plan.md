# Current Plan

## Active Task Stack (most recent on top)

1. **Fix describe-rules.bb script** - PENDING
   - Bug: writes to files named after rule IDs (e.g., creates file "R2130") instead of displaying output
   - Cause: likely parsing `--id` flag incorrectly as an output file path
   - Example: `bb scripts/query/describe-rules.bb src/karabiner.edn --id R2130` creates file "R2130"

2. **Fix remove-lhs-rules.bb script** - PENDING
   - Bug: `--id` flag removes way more rules than intended (removed 28 rules when trying to remove 1)
   - Example: `bb scripts/edit/remove-lhs-rules.bb src/karabiner.edn src/karabiner.edn --id R2183` removed 28 rules

2. ~~**Fix Shift Behavior in Insert Mode**~~ - NOT BROKEN (user confirmed)

3. **Order New Card** - PENDING

3. **Unit Tests for set-rule.bb** - PENDING
   - Add --create/--update flag to prevent accidental overwrites
   - --create fails if rule ID exists
   - --update fails if rule ID doesn't exist

4. **Fix match-rules.bb to require full state** - PENDING
   - Should require profile and device, not just layer
   - Makes queries more explicit and avoids ambiguity

3. ~~**Alt-Insert Mode (Layer 7)**~~ - COMPLETED
   - Frequency-optimized letter layout
   - Entry: semicolon from Normal
   - Mirror mode via Fn+]

4. ~~**Caps Lock Mode Implementation**~~ - COMPLETED
   - Submodes 5 (shift-pending) and 6 (caps lock) added to state library
   - R1549: submode 0 → 5 on shift press
   - R1550: submode 5 → 6 on shift alone (caps lock enter)
   - R1551-R1561: letter rules (stay in caps lock)
   - R1562-R1563: semicolon/hyphen rules (stay in caps lock)
   - R1564-R1567+: exit rules (comma, period, slash, space, arrows → submode 0)
   - **Pending manual testing**

2. ~~**Fix Validation Rules**~~ - COMPLETED side quest
   - Updated validation to require all 3 state vars
   - Exceptions: desktop fallbacks, `[:vk_none]` alone, same-layer rules
   - Fixed 129 rules with missing state vars
   - Documented in `mental-model.md`

---

# L-Mode Consolidation Plan

## Goal
Replace layers 7 + 14-27 with a new consolidated L-mode: one entry layer + one active layer, with Hammerspoon executing key+modifier combinations.

## Architecture Overview
- **New L-Entry (layer 29)**: Select modifier via Y/U/I/O/etc., writes to file
- **New L-Active (layer 30)**: Any key triggers Hammerspoon to execute key+modifiers
- **File `/tmp/karabiner-lmode-modifier`**: Stores selected modifier(s) (e.g., "C", "CS", "TO")
- **Hammerspoon**: Reads modifier file, executes key + modifiers, clears file

## Approach
Create new layers 29+30, test they work, then remove old layers 7+14-27.

## Tasks (ordered by independence)

### Phase 1: Independent Setup Tasks (can do now, no dependencies)

- [x] **1.1** Add `--lmode-modifier` flag to cleanup-external-state.sh
  - Add 5th flag for clearing `/tmp/karabiner-lmode-modifier`
  - Update from 8 args to 10 args
  - Always pass `reset` (be explicit)

- [x] **1.2** Add `--lmode-modifier` to panic-cleanup.sh
  - Add cleanup of `/tmp/karabiner-lmode-modifier`

- [x] **1.3** Update mental-model.md external state table
  - Add row for lmode-modifier file

- [x] **1.4** Create Hammerspoon function `executeLModeKey(key)`
  - Read modifier from `/tmp/karabiner-lmode-modifier`
  - Map modifier codes to actual modifiers (C=Cmd, S=Shift, T=Ctrl, O=Alt)
  - Execute keystroke with modifiers
  - Clear the modifier file after execution

- [x] **1.5** Create layer overlay file
  - `src/layers/lmode.txt` - shows modifier selection and key execution help

- [x] **1.6** Update SwiftBar to show new layers
  - Add cases for layers 29 (lentry) and 30 (lactive)

### Phase 2: Create New L-Mode (depends on Phase 1)

- [x] **2.1** Create entry point from Normal
  - Updated L key rule (R0040) to enter L-Entry (layer 29)

- [x] **2.2** Create L-Entry modifier selection rules (layer 29)
  - 11 modifier selection rules (Y, U, I, O, H, J, K + Shift variants)

- [x] **2.3** Create L-Active key rules (layer 30)
  - 15 letter keys + 5 number keys + 3 punctuation keys

- [x] **2.4** Create L-Active exit rules (layer 30)
  - RCtrl → Normal, Ctrl+N → escape + Normal, Ctrl+J → Ins

- [x] **2.5** Create L-Entry exit rules (layer 29)
  - RCtrl → Normal, Ctrl+N → escape + Normal

### Phase 3: Test New L-Mode

- [x] **3.1** Verify rules generated in karabiner.json
  - Confirmed layer 29 and 30 rules present with correct conditions

- [ ] **3.2** Manual test: Normal → L-Entry → L-Active → Normal
  - L → Y → H should execute Cmd+H
  - L → U → J should execute Ctrl+J
  - RCtrl should exit to Normal

- [ ] **3.3** Test exit paths
  - Ctrl+J exits to Ins
  - Ctrl+N sends escape and exits to Normal

### Phase 4: Remove Old L-Mode (only after Phase 3 passes)

- [ ] **4.1** Remove entry to old L layer (7)
  - Remove L key rule from Normal that enters layer 7

- [ ] **4.2** Remove old L-mode layers (7, 14-27)
  - Delete all rules for these layers

- [ ] **4.3** Update all cleanup-external-state.sh calls
  - Add `--lmode-modifier reset` to all existing calls

- [ ] **4.4** Final verification
  - Test SwiftBar shows correct layer names
  - Verify old layer numbers no longer active

## Modifier Mapping
| Key | Modifier Code | Actual Modifiers |
|-----|---------------|------------------|
| Y | C | Cmd |
| Shift+Y | CS | Cmd+Shift |
| U | T | Ctrl |
| Shift+U | TS | Ctrl+Shift |
| I | O | Alt |
| Shift+I | OS | Alt+Shift |
| O | CTO | Cmd+Ctrl+Alt |
| H | CT | Cmd+Ctrl |
| J | CO | Cmd+Alt |
| K | TO | Ctrl+Alt |

## Files to Modify
- `scripts/actions/cleanup-external-state.sh` - add --lmode-modifier flag
- `scripts/actions/panic-cleanup.sh` - add lmode-modifier cleanup
- `~/.hammerspoon/init.lua` - add executeLModeKey() function
- `src/karabiner.edn` (via scripts) - new layers 29, 30
- `src/layers/l-entry.txt` (new) - modifier selection overlay
- `src/layers/l-active.txt` (new) - key execution overlay
- `scripts/swiftbar/karabiner-layer.100ms.sh` - add layer 29, 30 cases
- `.claude/mental-model.md` - add lmode-modifier to external state table
- `.claude/reference.md` - update layer numbers
