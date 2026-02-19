# Current Plan

## Active Tasks

### 1. Add validation for reserved/passthrough keys

Create a validation script that ensures certain keys are NEVER bound in any layer (they must always pass through to macOS):

**Reserved keys:**
- `up_arrow`, `down_arrow` - vertical navigation
- `!Tn` (Ctrl+N), `!Tp` (Ctrl+P) - standard up/down navigation in many apps
- `!Of20` (Alt+F20 = Hotkey 3) - layer overlay trigger
- `page_up`, `page_down` - mapped to left/right click globally

**Implementation:**
- Create `scripts/validate/validate-reserved-keys.bb`
- Parse all rules and flag any that have these keys as triggers (in any layer except perhaps global remaps)
- Add to validation pipeline in sync

### 2. Remove/fix up/down bindings in all submodes

After validation is in place, find and remove any rules that bind up_arrow or down_arrow in layers. These should always pass through. The reference.md shows Ins layer has Shift+Up/Down for brackets - need to verify if this conflicts or if it's acceptable (since it uses Shift modifier).

Check all layers (especially submodes like Ins submodes 1-10) for up/down bindings.

### 3. Fix Command behavior in AltIns mode (layer 7)

In AltIns mode (layer 7), pressing Command seems to pass through unexpectedly. The user wants Command+key combinations to produce end-of-line behavior (like Command typically does for navigation).

Investigation done:
- Cmd+P in AltIns has no rule - falls through to blocking

Fix needed:
- Add Command+key bindings in AltIns that send end-of-line (Cmd+Right) or equivalent navigation
- Or ensure Command modifier passes through properly for navigation keys

### 4. Investigate gibberish keycode display in iTerm

When in various modes, certain key combinations cause iTerm to display gibberish/escape sequences. This suggests some modifier+key combos are passing through unhandled.

Investigation steps:
- Use Karabiner EventViewer to identify which key combinations cause this
- Check if these combos are unbound in current layer
- Either bind them to nothing or add to reserved keys list

### 5. Fix Screenshot Ctrl+P in Admin layer

Admin layer (layer 9) has shortcuts:
- P = Screenshot full → R0275 exists, sends Cmd+Shift+3 ✓
- Ctrl+P = Screenshot selection → **NO RULE EXISTS**

Investigation done:
- R0275 handles bare P correctly (Cmd+Shift+3)
- No rule for Ctrl+P (right_control+P) in layer 9
- Need to add rule for Ctrl+P → Cmd+Shift+4 (selection screenshot)

Fix:
- Get next rule ID
- Add rule: `{:key :p :modi {:mandatory [:right_control]}} → [:!CS4 ...]` in layer 9

### 6. Add validation for Command/Control blocking

Command and Control modifiers should NEVER pass through by themselves. They should only produce output when explicitly intended (e.g., through L-mode modifier sublayers or Comma mode shortcuts).

This means:
- Any bare key with Command or Control modifier must either:
  - Be explicitly bound to an action, OR
  - Be blocked (vk_none)
- Keys should not accidentally pass through with these modifiers

Implementation:
- Add validation to check that Command+key and Control+key combinations in layers are either:
  - Explicitly bound to something
  - Blocked in fallback
- Flag any rules that might accidentally pass through Command/Control

## Notes

- `timeout` command not available on macOS
- App bundle IDs: com.google.Chrome, com.googlecode.iterm2, com.microsoft.VSCode
