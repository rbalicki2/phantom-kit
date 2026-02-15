# TODOs

## Bugs

### Tilde input broken in insert mode
Fn+Space then Fn+↓ should type tilde (~) but doesn't work in insert mode.

## Validation

### Validate ID state strings match actual conditions
Rule IDs contain state strings like `[profile=Default:device=Desktop:dsk_layer=1]`. These should be validated to match the actual rule conditions.

### Validate state strings during sync
Add validation to `npm run sync` that checks state string validity.

## Tooling

### Simplify update-rule.bb
**Problem**: Current `update-rule.bb` has complex operations like `add-to-action`, `set-action`, `add-condition`, etc. Too many modes, hard to use.

**Solution**: Rewrite to simply take an entire rule as input:
1. Find existing rule by ID
2. Delete it
3. Insert the new rule

Order doesn't matter because auto-sorting handles placement.

```bash
echo '{:key :y :id "R3000 ..." ...full rule...}' | bb update-rule.bb src/karabiner.edn src/karabiner.edn -
```

### Update block names to describe behavior
Block names should describe what rules DO, not what state they check. E.g., "Enter submode 3" not "Submode 3 rules".

## Future Ideas

### Higher-level DSL
Create a DSL that compiles to Goku EDN for easier rule authoring.

### Auto-generate documentation
Generate docs from config automatically.

### Type-safe layer definitions
Possibly use Rust macros for type-safe layer definitions.
