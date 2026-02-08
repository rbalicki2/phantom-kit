# Mirror Mode Design Doc

## Goal
Enable full RHS-only typing in Insert mode, including access to all LHS letters, symbols, navigation, and common shortcuts.

---

## Core Mirror Mechanism

**Trigger**: Space held in combination with another key

RHS letter keys mirror to their LHS equivalents (Y→T, U→R, H→G, J→F, etc.)

**Shift behavior** (Option A - simple):
- Shift+Space + Y = Shift+T (capital T)
- Shift passes through naturally

**Shift behavior** (Option B - sticky, more complex):
- Shift+Space enters a mode where next key is shifted+mirrored
- Adds complexity, defer unless Option A feels bad

**Decision**: Start with Option A

---

## Questions to Resolve

### Space tap behavior
- Currently: Space types space
- With mirror: Space must still type space when tapped alone
- Using `:alone` modifier should work here

### What about Space + non-letter keys?
- Space + numbers?
- Space + symbols on RHS?
- Probably: pass through as Space + key (no mirroring)

---

## Bottom 4 Thumb Keys (Future - not implementing now)

The 4 keys: Ctrl, Cmd, PgUp, PgDn

**Idea**: Dual behavior
- Alone: Arrow keys (up/down/left/right?)
- With other keys: Act as modifiers

**Note for todos**: Define which key maps to which arrow/modifier

---

## Symbol Access (Future - not implementing now)

### Current problems
- No opening parenthesis `(` on RHS (left shift alone = `(`, but that's LHS)
- Brackets `[]` and braces `{}` approach is awkward

### Backslash/pipe key weirdness
- Physical key types `/` and `?` - probably remapped somewhere
- Needs investigation

### Auxiliary layer entry candidates
- Single quote `'`
- Key above single quote (backslash/pipe?)
- Hyphen `-` (above that)

These would enter aux layers that return to Insert mode when done.

**Open question**: How many aux layers do we actually need?

---

## Auxiliary Layers Design (Future - not implementing now)

### What needs to be accessible from aux layers:
1. **All symbols** - especially `( ) [ ] { } < >` and others hard to reach
2. **L-style navigation** - word/char/line movement with modifiers
3. **Common shortcuts** - Cmd+F, Cmd+A, F2 (rename), etc.
4. **Escape** - must be accessible somewhere

### Navigation sub-layer idea
- Enter via aux layer key
- Sticky modifiers: Shift (select), Cmd (by line), Alt (by word), Delete toggle
- Use L layer key mappings but accessible from Insert mode

### Entry keys
- Quote `'` = aux layer 1
- Shift+Quote `"` = aux layer 2 (or same layer with modifier?)
- Minimize number of entry keys

---

## True RHS-Only Setup (Future considerations)

When fully RHS-only:
- No trackpad attached to keyboard
- Page up/down won't be clicks
- Rely entirely on Mouse mode for cursor positioning
- May need to rethink some mappings

---

## Open Questions

1. **Mirror key choice**: Is Space the right key? Alternatives: Enter (but we use it for newlines)

2. **Layer return behavior**: Aux layers return to Insert. What triggers return?
   - After single action?
   - Explicit exit key?
   - Timeout?

3. **Escape access**: Where? Candidates:
   - Space + some key?
   - Dedicated aux layer key?
   - Right_control (current, but exits to Normal not stays in Insert)

4. **Modifier combos**: How to type Cmd+T (new tab) from RHS only?
   - Need Cmd accessible
   - Then mirror T
   - Sequence: Aux layer for Cmd, then Space+Y?
   - Or: Space + (something) = Cmd+mirrored key?

---

## Notes for Todos (capture before we start)

- [ ] Bottom 4 keys: arrow behavior alone, modifiers in combo
- [ ] Fix backslash/pipe key (currently types / and ?)
- [ ] Single quote / key above / hyphen as aux layer entries
- [ ] Opening parenthesis on RHS
- [ ] Rethink brackets/braces approach
- [ ] In true RHS-only, page up/down won't be clicks
