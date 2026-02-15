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

- [ ] **1.1** Add `--lmode-modifier` flag to cleanup-external-state.sh
  - Add 5th flag for clearing `/tmp/karabiner-lmode-modifier`
  - Update from 8 args to 10 args
  - Always pass `reset` (be explicit)

- [ ] **1.2** Add `--lmode-modifier` to panic-cleanup.sh
  - Add cleanup of `/tmp/karabiner-lmode-modifier`

- [ ] **1.3** Update mental-model.md external state table
  - Add row for lmode-modifier file

- [ ] **1.4** Create Hammerspoon function `executeLModeKey(key)`
  - Read modifier from `/tmp/karabiner-lmode-modifier`
  - Map modifier codes to actual modifiers (C=Cmd, S=Shift, T=Ctrl, O=Alt)
  - Execute keystroke with modifiers
  - Clear the modifier file after execution

- [ ] **1.5** Create layer overlay files
  - `src/layers/l-entry.txt` - show modifier selection keys
  - `src/layers/l-active.txt` - show available keys

- [ ] **1.6** Update SwiftBar to show new layers
  - Add cases for layers 29 and 30

### Phase 2: Create New L-Mode (depends on Phase 1)

- [ ] **2.1** Create entry point from Normal
  - From Normal (layer 0), L key → enter L-Entry (layer 29)

- [ ] **2.2** Create L-Entry modifier selection rules (layer 29)
  - Y → write "C" to file, enter L-Active (30)
  - Shift+Y → write "CS" to file, enter L-Active (30)
  - U → write "T" to file, enter L-Active (30)
  - Shift+U → write "TS" to file, enter L-Active (30)
  - I → write "O" to file, enter L-Active (30)
  - Shift+I → write "OS" to file, enter L-Active (30)
  - H → write "CT" to file, enter L-Active (30)
  - J → write "CO" to file, enter L-Active (30)
  - K → write "TO" to file, enter L-Active (30)
  - O → write "CTO" to file, enter L-Active (30)
  - (plus Shift variants for 4-modifier combos)

- [ ] **2.3** Create L-Active key rules (layer 30)
  - For each RHS key: `hs -c "executeLModeKey('h')"` → Normal
  - Handle Fn+key for mirrored letters
  - ~20-25 key rules total (vs 700+ currently)

- [ ] **2.4** Create L-Active exit rules (layer 30)
  - RCtrl alone → Normal (clear modifier file)
  - Ctrl+J → Ins (clear modifier file)
  - Ctrl+N → escape, Normal (clear modifier file)

- [ ] **2.5** Create L-Entry exit rules (layer 29)
  - RCtrl alone → Normal
  - Ctrl+N → escape, Normal

### Phase 3: Test New L-Mode

- [ ] **3.1** Test modifier selection
  - L → Y → verify file contains "C"
  - L → Shift+U → verify file contains "TS"

- [ ] **3.2** Test key execution
  - L → Y → H should execute Cmd+H
  - L → U → J should execute Ctrl+J
  - L → O → K should execute Cmd+Ctrl+Alt+K

- [ ] **3.3** Test exit paths
  - RCtrl exits to Normal
  - Ctrl+J exits to Ins
  - Verify modifier file is cleared

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
