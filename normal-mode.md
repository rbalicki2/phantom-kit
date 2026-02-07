# Normal Mode Implementation Plan

## Implementation Instructions

**After completing each task:**
1. Mark the checkbox `[x]` in this file
2. Commit in voicemode repo with message describing current state (so user can test)
3. Run `npm run sync`
4. Commit in ~/.config repo
5. If Hammerspoon files changed, commit in ~/.hammerspoon repo

This ensures progress survives compaction and each task is independently testable.

---

## Overview

Restructure the layer system so that:
- **Normal** layer is the default state (layer selector)
- **Ins** layer is for typing (passthrough)
- All other layers (Nav, M, H, Term, Chrome, VSCode, TMUX) are entered from Normal
- Layer exits return to Normal, not base

### Key Flows
- `right_ctrl` from Ins → Normal
- `right_ctrl` in Normal → sends escape, stays in Normal
- `j` in Normal → Ins
- Letter keys in Normal → enter respective layers
- `right_ctrl`/`escape` from any layer → Normal

### Layer Indicators
- `norm` - Normal layer (default)
- `ins` - Ins layer (typing)
- Existing codes unchanged for other layers

---

## Phase 0: Prefactoring

- [x] **Task 0.1**: Change Chrome/VSCode/TMUX entry to Ctrl+K
  - Currently these layers are entered with `right_ctrl+j`
  - Change to `right_ctrl+k` to free up `j` for Ins layer entry
  - Update karabiner.edn, run goku, test
  - Update documentation (shortcuts.md, CLAUDE.md)

---

## Phase 1: Foundation

- [x] **Task 1.1**: Create Normal layer variable
  - Add `layer_normal` variable
  - Normal should be active by default on Desktop profile
  - Write `norm` to `/tmp/karabiner-layer` on entry

- [x] **Task 1.2**: Create Ins layer
  - Add `layer_ins` variable
  - In Ins layer, all keys pass through (no remapping)
  - Write `ins` to `/tmp/karabiner-layer` on entry

- [x] **Task 1.3**: Normal → Ins transition
  - `j` in Normal → sets `layer_ins` to 1, `layer_normal` to 0
  - Writes `ins` to `/tmp/karabiner-layer`

- [x] **Task 1.4**: Ins → Normal transition
  - `right_ctrl` in Ins → sets `layer_ins` to 0, `layer_normal` to 1
  - Writes `norm` to `/tmp/karabiner-layer`

- [x] **Task 1.5**: Escape behavior in Normal
  - `right_ctrl` in Normal → sends escape to OS, stays in Normal
  - Does NOT exit Normal or enter Ins

---

## Phase 2: Layer Entries from Normal

- [x] **Task 2.1**: Nav layer entry
  - `n` in Normal → enter Nav layer (set `layer_n` to 1, `layer_normal` to 0)
  - Remove old `right_ctrl+n` entry rule

- [x] **Task 2.2**: M layer entry
  - `m` in Normal → enter M layer
  - Remove old `right_ctrl+m` entry rule

- [x] **Task 2.3**: H layer entry
  - `h` in Normal → enter H layer
  - Remove old `right_ctrl+h` entry rule

- [x] **Task 2.4**: Term layer entry
  - `u` in Normal → enter Term layer (focuses iTerm)
  - Remove old `right_ctrl+u` entry rule

- [x] **Task 2.5**: Chrome/VSCode/TMUX layer entry
  - `k` in Normal → enter Chrome layer (if Chrome focused)
  - `k` in Normal → enter VSCode layer (if VSCode focused)
  - `k` in Normal → enter TMUX layer (if iTerm focused)
  - Remove old `right_ctrl+k` entry rules (changed from j in Task 0.1)

---

## Phase 3: Layer Exits to Normal

- [x] **Task 3.1**: Nav layer exit
  - `right_ctrl` and `escape` in Nav → Normal (not base)
  - Set `layer_n` to 0, `layer_normal` to 1
  - Write `norm` to `/tmp/karabiner-layer`

- [x] **Task 3.2**: M layer exit
  - Same pattern: exit to Normal

- [x] **Task 3.3**: H layer exit
  - Same pattern: exit to Normal

- [x] **Task 3.4**: Term layer exit
  - Same pattern: exit to Normal

- [x] **Task 3.5**: Chrome layer exit
  - Same pattern: exit to Normal

- [x] **Task 3.6**: VSCode layer exit
  - Same pattern: exit to Normal

- [x] **Task 3.7**: TMUX layer exit
  - Same pattern: exit to Normal

---

## Phase 4: Nested Layers

- [x] **Task 4.1**: H sub-layers
  - H-C, H-T, H-O, etc. exit directly to Normal (not back to H)
  - Simplifies the flow: no intermediate H step on exit

- [x] **Task 4.2**: Switch/WinSw layers
  - Currently entered from Nav (Shift+H, Shift+J)
  - Exit behavior: return to Normal (not Nav)

---

## Phase 5: Indicators

- [x] **Task 5.1**: Update SwiftBar script
  - Add case for `norm` → display "Norm"
  - Add case for `ins` → display "Ins"
  - Update `karabiner-layer.300ms.sh`

- [x] **Task 5.2**: Create Hammerspoon overlay files
  - Create `layers/norm.txt` with Normal layer shortcuts
  - Create `layers/ins.txt` (minimal - just shows it's typing mode)

- [x] **Task 5.3**: Update Hammerspoon init.lua
  - Add `norm` and `ins` to `layerFiles` map

---

## Phase 6: RHS Flag

- [x] **Task 6.1**: RHS toggle location
  - Already `right_ctrl+7`, works from any layer including Ins

- [x] **Task 6.2**: RHS behavior scope
  - No changes needed: RHS disables LHS keys globally, but no layer uses LHS keys
  - Only Ins layer (typing mode) is affected in practice
  - Goku's one-condition limitation prevents true Ins-only scoping

---

## Phase 7: Cleanup & Documentation

- [x] **Task 7.1**: Remove old entry rules
  - Already done during Phase 2 implementation
  - Old `right_ctrl+KEY` entries replaced by single-key entries from Normal

- [x] **Task 7.2**: Update shortcuts.md
  - Added Modal System Overview section
  - Documented Normal and Ins layers
  - Updated all layer entry instructions (single-key from Normal)

- [x] **Task 7.3**: Update CLAUDE.md
  - Update "Current Layers" section
  - Update flow diagrams
  - Document Normal as default state
  - Note: Interrupted by bug fixes, partially done

- [ ] **Task 7.4**: Test all flows
  - Normal → each layer → Normal
  - Normal → Ins → Normal
  - Ins → right_ctrl → Normal → right_ctrl (sends escape)
  - Layer switching: Nav → Normal → M
  - Nested: Normal → H → H-C → Normal

---

## Open Questions (to resolve during implementation)

1. **in_any_layer variable**: Still needed? Rename to exclude Normal/Ins?

(Default on boot: punted for now)
