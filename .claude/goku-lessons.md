# Goku/Karabiner - Syntax & Debugging

Accumulated knowledge about what works and what doesn't in Goku/Karabiner syntax.

## Syntax Lessons Learned

### Mouse Buttons
- `:!Cbutton1` does NOT work - modifier shorthand doesn't apply to mouse buttons
- Use explicit form: `{:pkey :button1 :modi [:left_command]}`

### Modifiers
- `!S` only matches LEFT shift
- To match EITHER shift: `{:key :x :modi {:mandatory [:shift]}}`
- RHS shortcuts must use `right_control`, not `left_control`

### Modifier Key Interception (Critical!)

**Problem**: When you create a rule that matches a modifier key (like `right_shift`) and output that same modifier in the `to` section, Karabiner sends it as a **tap** (down→up immediately), NOT as a maintained hold.

**Evidence**: EventViewer shows `right_shift down` immediately followed by `right_shift up`, even while the physical key is still held. This breaks Shift+letter combinations.

**What DOESN'T work**:
- `[:right_shift ...]` in `to` - sends tap, not hold
- `{:key :right_shift :lazy true}` - lazy doesn't help
- Removing variable sets from `to` - still taps
- `to_if_alone` with `right_shift` in `to` - still taps

**What DOES work**: Use `to_if_held_down` for the modifier output:

```clojure
;; Double-tap shift for caps lock, hold shift for normal shift behavior
[{:key :right_shift :id "..."}
 [:vk_none ...]                           ; to: nothing on press
 [["dsk_layer" 1] ["dsk_ins_sub_mode" 0]] ; condition
 {:held [:right_shift]                    ; to_if_held_down: shift when held
  :afterup [:vk_none ["dsk_ins_sub_mode" 5] ...]}] ; to_after_key_up: state change on release
```

**How this works**:
1. Shift pressed → `to` fires (vk_none, no shift yet)
2. Held past threshold (~70ms) → `to_if_held_down` fires, shift becomes active and STAYS held
3. Shift+j while held → J (capital)
4. Shift released → `to_after_key_up` fires, can set state (e.g., sub_mode 5 for double-tap detection)

**Note on `optional: ["shift"]`**: This means "match with or without shift" but does NOT pass shift through to the output. If you have `optional: ["shift"]` and output just `:j`, you get lowercase j even when shift is held. You need explicit Shift+letter rules with `mandatory: ["shift"]` and `:!Sj` output.

### Shell Commands
- Only ONE `{:shell ...}` per rule executes (the LAST one)
- Combine with `&&`: `{:shell "cmd1 && cmd2"}`
- Shell commands don't have /opt/homebrew/bin in PATH - use full paths

### Variable Values
- Goku requires NUMERIC values: `["dsk_layer" 0]` not `["dsk_layer" "norm"]`

### Key Output Required
- Rules need a key output to work
- Use `:vk_none` for rules with only variable sets/shell: `[:vk_none ["dsk_layer" 0] {:shell "..."}]`

### Conditions
- Multiple conditions require array wrapping: `[["dsk_layer" 0] ["dsk_return_to_layer" 1]]`
- Without outer array, only first condition is used

### The `:alone` Modifier
- `{:alone ...}` requires explicit `nil` if no condition
- Use: `[from to nil {:alone [...]}]`
- `:alone :escape` does NOT work for layer exits - escape goes directly to OS

### Bare Key Matching
- A rule like `[:n :escape]` matches N with ANY modifiers
- To match only bare N: `{:key :n :modi {:optional [:caps_lock]}}`

## Karabiner Rule Precedence

Rules are evaluated **in order** - first match wins.

**Two ways to control precedence:**
1. **Rule ordering**: Put more specific rules earlier
2. **Conditions**: Add exclusion conditions to general rules

**Important**: Output keys are NOT re-processed. When a rule sends `:!S5` (Shift+5), it goes directly to the app.

## Layer Entry Conflict Prevention

In-layer Ctrl+KEY rules must come BEFORE layer entry rules they might conflict with.

**Example**: VS Code layer has Ctrl+H. The VS Code Ctrl+H rule must appear before Normal layer rules, otherwise pressing Ctrl+H while in VS Code could trigger a Normal layer entry.

## Testing with match-rules.bb

```bash
cd karabiner-test-harness

# Find what physical key sends (check kinesis-layout1.txt)
# Fn+comma sends Alt+F6

# Test what matches in Normal mode
bb match-rules.bb ../src/karabiner.edn f6 --layer 0 --mod left_option

# Test what matches in Ins mode
bb match-rules.bb ../src/karabiner.edn f6 --layer 1 --mod left_option
```

**Interpreting results:**
- 0 matches = Key passes through (probably a bug)
- 1+ matches = Shows which rule fires
- Multiple matches = First is active, others shadowed

## Common Gotchas

1. **Global rules fire first** - Add `["dsk_in_modal_layer" 0]` to global shortcut rules

2. **Layer entry exclusions** - Entry rules must exclude other active layers to prevent conflicts when holding right_control

3. **Right_control exit + Ctrl combos** - Use `:alone` modifier: passes through right_control normally, only exits layer when tapped alone

4. **Modifier+click** - Only works reliably with Cmd modifier. Ctrl+click, Alt+click don't exit layers properly.

5. **F13 and F14** - These open macOS System Settings even with modifiers. Never use them.

## Debugging Workflow

1. Check `karabiner-EventViewer.app` to see what key codes are being sent
2. Use `match-rules.bb` to see which rules would match
3. Check rule ordering in karabiner.edn
4. Verify conditions are correctly formatted (array wrapping)
5. Test with `npm run sync` after each change

## State Space Dimensions

The state space has multiple dimensions that must ALL be considered:

| Dimension | Values | Where Specified |
|-----------|--------|-----------------|
| Profile | None, Default | Block keyword (`:None`) or implicit |
| Device | laptop, desktop | Block keyword (`:apple_internal`, `:!apple_internal`) |
| Layer | 0-30 | Rule condition `["dsk_layer" N]` |
| Submode | 0-4 (layer 1 only) | Rule condition `["dsk_ins_sub_mode" N]` |
| Return-to | 0,1 (layer 13 only) | Rule condition `["dsk_return_to_layer" N]` |
| App | Chrome, VSCode, iTerm | Block keyword (`:Chrome`, etc.) |

**Critical**: The `scripts/lib/state.bb` library is the canonical source for all state definitions.

### Lesson Learned: Missing App Dimension

In Feb 2025, we learned that **app conditions are a dimension of the state space** that wasn't fully modeled. The reorder-by-state.bb script initially didn't handle apps, causing app-specific rules (like `h` in VSCode) to be merged into the wrong catch-all group.

**The fix**:
1. Added `all-apps` and `app-layers` to state.bb
2. Updated `all-condition-states-for-grouping` to include app-specific states
3. Updated reorder script to extract and match app conditions from blocks

**Takeaway**: When adding new rule types, ensure all condition dimensions are:
1. Defined in state.bb
2. Included in state enumeration functions
3. Handled by the reorder script
4. Validated by validation scripts

### Lesson Learned: Ordering Patterns Conflict

The config uses TWO different ordering patterns that conflict:

1. **Root-to-leaf** (general first): Global rules like `page_up → button2` should apply everywhere UNLESS explicitly overridden. These must come FIRST.

2. **Leaf-to-root** (specific first): App-specific rules like `h in VSCode → VSCode mode` should override the layer catch-all `h → Chrome mode`. These specific rules must come FIRST within their layer.

A naive "always leaf-to-root" reordering breaks pattern #1. Rules using `##key` (any modifiers) in specific layers shadow global rules when reordered.

**Current approach**: Don't auto-reorder. Manually maintain block order. The reorder-by-state.bb script is disabled until we find a smarter approach that respects both patterns.
