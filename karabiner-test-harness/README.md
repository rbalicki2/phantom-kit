# Karabiner Test Harness

Tools for testing and validating Karabiner/Goku configurations.

## Tools

### match-rules.bb

Finds all rules that match a given key + modifiers + state.

```bash
# Basic usage
bb match-rules.bb ../karabiner.edn j --layer 0

# With modifiers
bb match-rules.bb ../karabiner.edn y --layer 1 --mod right_control

# Full state
bb match-rules.bb ../karabiner.edn h --layer 0 \
  --modal 0 --submode -1 --return-to -1 \
  --app com.google.Chrome --device external
```

Options:
- `--layer N` - Set dsk_layer (default: 0)
- `--modal N` - Set dsk_in_modal_layer (default: 0)
- `--submode N` - Set dsk_ins_sub_mode (default: -1)
- `--return-to N` - Set dsk_return_to_layer (default: -1)
- `--app BUNDLE_ID` - Set frontmost app
- `--device TYPE` - `apple_internal` or `external` (default: external)
- `--mod MODIFIER` - Add modifier (repeatable)

### validate-rules.bb

Validates rules for invariant violations and shadowing.

```bash
bb validate-rules.bb ../karabiner.edn
```

Checks for:
- **Invariant violations in conditions**: Rules that assume impossible state combinations
- **Invariant violations in actions**: Actions that set invalid state combinations
- **Shadowed rules**: Rules that can never fire because earlier rules catch all inputs

Invariants checked (from mental_model.md):
1. `dsk_in_modal_layer = (dsk_layer >= 2 ? 1 : 0)`
2. `dsk_ins_sub_mode = -1` when `dsk_layer != 1`
3. `dsk_return_to_layer = -1` when `dsk_layer != 13`

## Tests

Run unit tests:

```bash
bb test/match-rules-test.bb
```

## Using from Code

The tools can be loaded as libraries:

```clojure
(load-file "match-rules.bb")

(match-rules/match-rules
  {:edn-file "../karabiner.edn"
   :key :j
   :mods #{:right_control}
   :state {:dsk_layer 1 :dsk_in_modal_layer 0
           :dsk_ins_sub_mode 0 :dsk_return_to_layer -1}})
;; => {:first-match {...} :all-matches [...] :match-count N}
```
