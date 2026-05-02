# 2H Mode (Two-Handed Insert)

## Goal

Add a two-handed variant of AltIns mode. Currently AltIns (layer 7) blocks all LHS keys because it's designed for one-handed (RHS) typing. 2H mode enables the LHS to type the AltIns Fn-layer characters, so both hands together cover all characters without needing to hold Fn.

## Design: Separate Variable, Not a Submode

A submode won't work because every RHS keystroke in layer 7 sets `dsk_ins_sub_mode=0`. Using a submode for 2H would be cleared on the first RHS keypress.

Instead, add a new variable `dsk_2h_mode` (0 or 1):
- **0** = one-handed (LHS blocked, current behavior)
- **1** = two-handed (LHS types characters)

This variable is only meaningful when `dsk_layer=7`. LHS rules check both `dsk_layer=7` AND `dsk_2h_mode=1`.

### Minimal changes needed:
- Entry rules set `dsk_2h_mode` explicitly (0 for bare H, 1 for F22)
- No other existing rules need modification (dsk_2h_mode is unchecked elsewhere)
- The variable persists through submode changes and label-mode round trips (correct behavior — you return to 2H if you left from 2H)

## Entry / Exit

- **Entry**: From Normal (layer 0), Fn+H (sends F22) → `dsk_layer=7, dsk_ins_sub_mode=0, dsk_2h_mode=1, dsk_return_to_layer=-1`
- **Exit**: Right Control → Normal (existing rule R1278 handles this; dsk_2h_mode becomes irrelevant once dsk_layer≠7)
- **Existing bare-H entry** (R0031): Modify to also set `dsk_2h_mode=0`

## LHS Key Mappings

### Algorithm

For each LHS alpha key, output the AltIns Fn value of its positional mirror on the RHS.

Positional mirror pairs:
```
LHS: Q W E R T | A S D F G | Z X C V B
RHS: P O I U Y | ; L K J H | / . , M N
```

### Letter Mappings (15 keys)

| LHS Physical | Mirror RHS | AltIns Fn of Mirror | Bare Output | Shift Output |
|---|---|---|---|---|
| Q | P | z | z | Z |
| W | O | j | j | J |
| E | I | v | v | V |
| R | U | + | + | * |
| T | Y | = | = | (blocked) |
| A | ; | q | q | Q |
| S | L | l | l | L |
| D | K | p | p | P |
| F | J | g | g | G |
| G | H | y | y | Y |
| Z | / | ; | ; | : |
| X | . | b | b | B |
| C | , | k | k | K |
| V | M | f | f | F |
| B | N | x | x | X |

### Number Mappings (5 keys)

Based on AltIns oneshot column of mirror RHS number:
- LHS 5 ↔ RHS 6: oneshot = %
- LHS 4 ↔ RHS 7: oneshot = `
- LHS 3 ↔ RHS 8: oneshot = #
- LHS 2 ↔ RHS 9: oneshot = @
- LHS 1 ↔ RHS 0: oneshot = \

| LHS Physical | Bare Output | Shift Output |
|---|---|---|
| 5 | % | 9 |
| 4 | ` | 8 |
| 3 | # | 7 |
| 2 | @ | 6 |
| 1 | \ | 5 |

Shift outputs from AltIns shift column of mirror RHS number.

### Other LHS Keys

All other LHS keys (Tab, Backspace, Delete, =, Fn+anything) = no-op (blocked by existing global fallback).

## RHS Behavior

Completely unchanged from regular AltIns. All existing layer 7 rules apply identically (bare, Fn, shift, submodes, chords, double-taps).

## Implementation Steps

### 1. Add `dsk_2h_mode` variable

- Add to `scripts/lib/state.bb` as a new variable with range {0, 1}
- Update state generation (`scripts/generate/states.bb`) if needed
- Update validation to recognize the new variable

### 2. Modify existing entry rule R0031

Add `["dsk_2h_mode" 0]` to the bare-H entry from Normal:
```
R0031: h from layer 0 → dsk_layer=7, dsk_ins_sub_mode=0, dsk_2h_mode=0, dsk_return_to_layer=-1
```

### 3. Add F22 entry rule (new)

From Normal, F22 (Fn+H) enters 2H mode:
```
NEW: F22 from layer 0 → dsk_layer=7, dsk_ins_sub_mode=0, dsk_2h_mode=1, dsk_return_to_layer=-1
```

### 4. Add LHS rules (20 new rules)

15 letter keys + 5 number keys, each with bare and shift handled via `optional: [:left_shift]` or separate rules.

Conditions: `[["dsk_layer" 7] ["dsk_2h_mode" 1]]`

These rules should NOT set state variables (same-layer rules exempt from full state setting per mental-model.md). They simply output the character.

Wait — actually they should set the state to maintain invariants. Looking at existing layer 7 rules like R2006, they set all 3 variables. Our LHS rules should do the same: `["dsk_layer" 7] ["dsk_ins_sub_mode" 0] ["dsk_return_to_layer" -1]`.

Hmm, but setting `dsk_ins_sub_mode=0` would cancel pending double-taps and exit caps mode. That's actually correct — typing an LHS letter should clear pending states just like typing an RHS letter does.

### 5. Update validation

- Allow LHS keys to appear in non-blocking rules when conditioned on `dsk_2h_mode=1`
- Add invariant: `dsk_2h_mode` must be 0 or 1
- Consider: enforce `dsk_2h_mode=0` when `dsk_layer != 7` (optional, since it's harmless if stale)

### 6. Update SwiftBar / Hammerspoon

- Entry rule writes `echo 2h > /tmp/karabiner-layer` (distinct from "altins")
- Add "2H" case to `scripts/swiftbar/karabiner-layer.100ms.sh`
- Create `src/layers/2h.txt` overlay file showing LHS mappings
- Update `layerFiles` map in `~/.hammerspoon/init.lua`

## Rule IDs

Starting from R3226 (next available).

## Verification

After implementation:
1. `npm run sync` passes validation
2. From Normal, Fn+H enters 2H mode
3. RHS typing works identically to regular AltIns
4. LHS keys produce correct characters per mapping table
5. Shift+LHS produces correct shifted output
6. Exit via Right Control returns to Normal
7. Bare H from Normal enters regular AltIns (LHS blocked)
