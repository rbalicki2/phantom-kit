# Karabiner Modifier Behavior

Summary of `from.modifiers` behavior from official Karabiner-Elements documentation.

## Mental Model: Rule Specificity

Rules are evaluated in order. The first matching rule wins. Specificity is determined by which inputs a rule can match:

```
MOST SPECIFIC (matches fewest inputs)
├── {:mandatory [:shift :ctrl]} - only Shift+Ctrl+key
├── {:mandatory [:shift]}       - only Shift+key
├── {:mandatory [:shift], :optional [:caps_lock]} - Shift+key, optionally with caps
├── {:optional [:caps_lock]}    - only bare key (or with caps_lock)
├── (no modi)                   - only bare key with NO modifiers
└── ##key / {:optional [:any]}  - ANY input with this key
LEAST SPECIFIC (matches most inputs - CATCH-ALL)
```

**Critical Rule**: Catch-all rules (`##key`) must come LAST for a given key+condition. If a catch-all comes first, it shadows all later rules for that key.

**Non-overlapping rules don't shadow**: `{:optional [:caps_lock]}` and `{:mandatory [:shift]}` have disjoint match sets - they never compete for the same input. Order between them doesn't matter.

**Overlapping rules DO shadow**: `##key` matches everything `{:mandatory [:shift]}` matches, so if `##key` comes first, the shift rule never fires.

## Key Rules

1. **Without modifiers** (no `from.modifiers` at all):
   - Only matches if NO modifiers are pressed
   - Any modifier prevents the rule from matching

2. **With `optional: [list]`**:
   - Matches if modifiers in the list are pressed (they pass through to output)
   - Matches if NO modifiers are pressed (optional means optional)
   - Other modifiers NOT in the list **prevent the rule from matching**

3. **With `mandatory: [list]`**:
   - Only matches if those modifiers are pressed
   - Mandatory modifiers are **removed/consumed** from output
   - Other modifiers NOT in mandatory **prevent the rule from matching**

4. **With both `mandatory` and `optional`**:
   - Mandatory modifiers must be pressed (consumed)
   - Optional modifiers may be pressed (pass through)
   - Other modifiers prevent matching

5. **`optional: ["any"]`**:
   - Special case: allows any modifiers to be pressed
   - All modifiers pass through to output

## Critical Quote from Docs

> "If you do not include any in modifiers.optional, your manipulator does not change event if extra modifiers (modifiers which are not included in modifiers.mandatory) are pressed."

## Examples from Docs

| From Modifiers | Input | Matches? | Output |
|----------------|-------|----------|--------|
| (none) | escape | ✓ | tab |
| (none) | shift+escape | ✗ | shift+escape (unchanged) |
| `optional: [shift, ctrl]` | escape | ✓ | tab |
| `optional: [shift, ctrl]` | shift+escape | ✓ | shift+tab |
| `optional: [shift, ctrl]` | option+escape | ✗ | option+escape (unchanged) |
| `mandatory: [ctrl]` | h | ✗ | h (unchanged) |
| `mandatory: [ctrl]` | ctrl+h | ✓ | backspace |
| `mandatory: [ctrl]` | ctrl+option+h | ✗ | ctrl+option+h (unchanged) |
| `mandatory: [ctrl], optional: [any]` | ctrl+option+h | ✓ | option+backspace |

## Goku Translation

In Goku EDN:
- `{:key :j}` → no modifiers (only bare key)
- `{:key :j, :modi {:optional [:caps_lock]}}` → `"optional": ["caps_lock"]`
- `{:key :j, :modi {:mandatory [:shift]}}` → `"mandatory": ["shift"]`
- `{:key :j, :modi {:mandatory [:shift], :optional [:caps_lock]}}` → both
- `:##j` or `{:key :##j}` → `"optional": ["any"]` (match with any modifiers)

## Implications for Phantom Kit

1. **`{:optional [:caps_lock]}` does NOT match Shift+key** - confirmed by testing
   - Docs are accurate for current Karabiner 15.x behavior

2. **Safest patterns**:
   - Use `{:mandatory [...], :optional [:caps_lock]}` for modifier+key rules
   - Use `{:optional [:caps_lock]}` only for bare key rules where you want caps_lock tolerance
   - Use `:##key` syntax when you want ANY modifiers to pass through

3. **Rule ordering does NOT cause shadowing** when modifiers are correctly specified
   - Both same-block and cross-block ordering work correctly

## Empirical Test Results (2024)

Tested with `/tmp/test-modifiers.edn` and `/tmp/test-cross-block.edn`:

| Test | Expected | Actual | Result |
|------|----------|--------|--------|
| `{:key :j}` bare J | a | a | PASS |
| `{:key :j}` Shift+J | unchanged | J | PASS |
| `{:optional [:caps_lock]}` bare K | b | b | PASS |
| `{:optional [:caps_lock]}` Shift+K | unchanged | K | PASS |
| Bare L first, Shift+L second (same block) | c, d | c, d | PASS - no shadowing |
| Bare L first, Shift+L second (cross-block) | a, b | a, b | PASS - no shadowing |
| Shift+H first, bare H second | g, h | g, h | PASS |

**Conclusion**: Rule ordering does not cause modifier shadowing in Karabiner 15.x.
The original oneshot bug may have been a different issue or has been fixed in Goku/Karabiner.
