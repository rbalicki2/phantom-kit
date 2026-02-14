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
