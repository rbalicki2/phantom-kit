# Long-term TODOs

## Auto-organize rules by state

**Problem**: Current "sections" in karabiner.edn are manually named and organized. `insert-rules.bb` just stuffs rules at the end. No tooling for section management.

**Solution**: Sections should automatically fall out of state. Each possible state gets its own section, ordered leaf-to-root (most specific first):

1. None (global)
2. Default profile
3. Default + Laptop
4. Default + Desktop
5. Desktop + Layer 0
6. Desktop + Layer 1, Submode 0
7. Desktop + Layer 1, Submode 1
8. Desktop + Layer 1, Submode 2
9. ... (all submodes)
10. Desktop + Layer 2
11. ... (all layers in order)

**Implementation idea**: `reorganize-by-state.bb` script that:
- Parses all rules
- Groups by state (profile + device + layer + submode)
- Outputs in canonical leaf-to-root order
- Generates section names automatically from state

**For now**: Just add rules at the end, refactor later.

## Unified state string for scripts

**Problem**: Scripts like `match-rules.bb` have separate `--layer`, `--modal`, `--submode`, `--return-to` parameters. This is error-prone and verbose.

**Solution**: Single state string parameter everywhere, validated by one shared function. Format TBD but likely similar to rule ID format: `profile=Default:device=Desktop:layer=0:modal=0:submode=-1:return=-1`

**Benefits**:
- Single source of truth for state parsing
- Less parameter boilerplate in every script
- Consistent validation everywhere

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
