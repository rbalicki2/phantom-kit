# Long-term TODOs

## Tilde input broken in insert mode

Fn+Space then Fn+↓ should type tilde (~) but doesn't work in insert mode.

## Simplify update-rule.bb

**Problem**: Current `update-rule.bb` has complex operations like `add-to-action`, `set-action`, `add-condition`, etc. Too many modes, hard to use.

**Solution**: Rewrite to simply take an entire rule as input:
1. Find existing rule by ID
2. Delete it
3. Insert the new rule

Order doesn't matter because auto-sorting will handle placement (see "Auto-organize rules by state" above).

**Usage should be**:
```bash
echo '{:key :y :id "R3000 ..." ...full rule...}' | bb update-rule.bb src/karabiner.edn src/karabiner.edn -
```
