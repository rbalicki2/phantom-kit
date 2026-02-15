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
