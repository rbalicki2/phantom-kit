# InApp Tab Navigation Plan

## Goal

Add number keys (6-0) in InApp Nav mode (layer 10) to switch tabs, with app-specific behavior:
- **Chrome**: Cmd+N for tab N
- **iTerm**: Shift+N (same as current TMUX mode YUIOP behavior)

Also: Remove YUIOP tab navigation from Chrome mode (layer 3) and TMUX mode (layer 5).

Note: VSCode is explicitly out of scope for this change.

## CRITICAL: Commit Structure

**DO NOT batch changes. Make atomic commits with user verification:**

1. **Commit 1**: Add Chrome + iTerm rules to InApp mode (Stage 1 + Stage 2)
2. **USER VERIFICATION**: User tests that new InApp rules work correctly
3. **Commit 2**: Remove YUIOP from Chrome mode layer 3 (Stage 3)
4. **Commit 3**: Remove YUIOP from TMUX mode layer 5 (Stage 4) - **ONLY R0344-R0353**

**IMPORTANT**: Stage 4 removes ONLY the YUIOP rules (R0344-R0353). Do NOT remove R0354, R0355, or any other TMUX mode rules.

## Confirmed Number Mapping

Position-based (left to right = tab 1 to 10):

| Key | Chrome | iTerm |
|-----|--------|-------|
| 6 | Cmd+1 (tab 1) | Shift+1 (!) |
| 7 | Cmd+2 (tab 2) | Shift+2 (@) |
| 8 | Cmd+3 (tab 3) | Shift+3 (#) |
| 9 | Cmd+4 (tab 4) | Shift+4 ($) |
| 0 | Cmd+5 (tab 5) | Shift+5 (%) |
| Fn+6 (f7) | Cmd+6 (tab 6) | Shift+6 (^) |
| Fn+7 (f8) | Cmd+7 (tab 7) | Shift+7 (&) |
| Fn+8 (f9) | Cmd+8 (tab 8) | Shift+8 (*) |
| Fn+9 (f10) | Cmd+9 (last tab) | Shift+9 (() |
| Fn+0 (f11) | Cmd+9 (last tab) | Shift+0 ()) |

## Current State

### Chrome Mode (layer 3) - Rules to Remove in Stage 3
```
R0296: y → Cmd+1, Normal
R0297: u → Cmd+2, Normal
R0298: i → Cmd+3, Normal
R0299: o → Cmd+4, Normal
R0304: p → Cmd+9 (last tab), Normal
R0300: Fn+Y → Cmd+5, Normal
R0301: Fn+U → Cmd+6, Normal
R0302: Fn+I → Cmd+7, Normal
R0303: Fn+O → Cmd+8, Normal
```

### TMUX Mode (layer 5) - Rules to Remove in Stage 4

**ONLY THESE RULES - DO NOT REMOVE ANYTHING ELSE:**
```
R0344: y → Shift+1, AltIns
R0345: u → Shift+2, AltIns
R0346: i → Shift+3, AltIns
R0347: o → Shift+4, AltIns
R0348: p → Shift+5, AltIns
R0349: Fn+Y (f15) → Shift+6, AltIns
R0350: Fn+U (f16) → Shift+7, AltIns
R0351: Fn+I (f17) → Shift+8, AltIns
R0352: Fn+O (f18) → Shift+9, AltIns
R0353: Fn+P (f19) → Shift+0, AltIns
```

**DO NOT TOUCH these other TMUX rules:**
- R0354: l → Cmd+d, AltIns
- R0355: LOpt+f1 → Cmd+Shift+d, AltIns
- Any other TMUX mode rules

### InApp Mode (layer 10) - Structure
Has app-specific blocks:
- Chrome block (`:rules [:!apple_internal :Chrome ...]`)
- VSCode block (`:rules [:!apple_internal :VSCode ...]`)
- iTerm block (`:rules [:!apple_internal :iTerm ...]`)
- Generic fallback block (`:rules [:!apple_internal ...]`)

---

## Stage 1: Add Chrome Number Keys to InApp Mode

### Pre-check
```bash
bb scripts/query/next-id.bb src/karabiner.edn
```

### Commands

**IMPORTANT**: These rules need to go in the Chrome-specific block of InApp mode. The set-rule.bb script may not handle app conditions correctly. If these end up in the wrong block, manually move them in the EDN.

```bash
# Get the starting ID (assume 3138 - update based on next-id output)
NEXT_ID=3138

# 6 → Cmd+1 (tab 1)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R${NEXT_ID} - --no-clobber
[{:key :6, :id "R${NEXT_ID} [profile=Default:device=Desktop:dsk_layer=10:app=Chrome] 6 → Cmd+1 (tab 1)"} [:!C1 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 7 → Cmd+2 (tab 2)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((NEXT_ID+1)) - --no-clobber
[{:key :7, :id "R$((NEXT_ID+1)) [profile=Default:device=Desktop:dsk_layer=10:app=Chrome] 7 → Cmd+2 (tab 2)"} [:!C2 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 8 → Cmd+3 (tab 3)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((NEXT_ID+2)) - --no-clobber
[{:key :8, :id "R$((NEXT_ID+2)) [profile=Default:device=Desktop:dsk_layer=10:app=Chrome] 8 → Cmd+3 (tab 3)"} [:!C3 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 9 → Cmd+4 (tab 4)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((NEXT_ID+3)) - --no-clobber
[{:key :9, :id "R$((NEXT_ID+3)) [profile=Default:device=Desktop:dsk_layer=10:app=Chrome] 9 → Cmd+4 (tab 4)"} [:!C4 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 0 → Cmd+5 (tab 5)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((NEXT_ID+4)) - --no-clobber
[{:key :0, :id "R$((NEXT_ID+4)) [profile=Default:device=Desktop:dsk_layer=10:app=Chrome] 0 → Cmd+5 (tab 5)"} [:!C5 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+6 (f7) → Cmd+6 (tab 6)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((NEXT_ID+5)) - --no-clobber
[{:key :f7, :id "R$((NEXT_ID+5)) [profile=Default:device=Desktop:dsk_layer=10:app=Chrome] Fn+6 → Cmd+6 (tab 6)"} [:!C6 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+7 (f8) → Cmd+7 (tab 7)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((NEXT_ID+6)) - --no-clobber
[{:key :f8, :id "R$((NEXT_ID+6)) [profile=Default:device=Desktop:dsk_layer=10:app=Chrome] Fn+7 → Cmd+7 (tab 7)"} [:!C7 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+8 (f9) → Cmd+8 (tab 8)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((NEXT_ID+7)) - --no-clobber
[{:key :f9, :id "R$((NEXT_ID+7)) [profile=Default:device=Desktop:dsk_layer=10:app=Chrome] Fn+8 → Cmd+8 (tab 8)"} [:!C8 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+9 (f10) → Cmd+9 (last tab)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((NEXT_ID+8)) - --no-clobber
[{:key :f10, :id "R$((NEXT_ID+8)) [profile=Default:device=Desktop:dsk_layer=10:app=Chrome] Fn+9 → Cmd+9 (last tab)"} [:!C9 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+0 (f11) → Cmd+9 (last tab, same as Fn+9 for Chrome)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((NEXT_ID+9)) - --no-clobber
[{:key :f11, :id "R$((NEXT_ID+9)) [profile=Default:device=Desktop:dsk_layer=10:app=Chrome] Fn+0 → Cmd+9 (last tab)"} [:!C9 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR
```

### Verification
```bash
npm run sync
```

**Expected**: Sync succeeds. Test failures for Chrome layer=10 number keys (new behavior).

**Check rule placement**: Verify the new rules are in the Chrome block, not the generic block:
```bash
grep -A2 "6 → Cmd+1" src/karabiner.edn
```
Should show they're under `Desktop [["dsk_layer" 10]] Chrome`.

---

## Stage 2: Add iTerm Number Keys to InApp Mode

### Commands

iTerm rules output Shift+N (matching current TMUX mode behavior):

```bash
# Continue from NEXT_ID+10
ITERM_ID=$((NEXT_ID+10))

# 6 → Shift+1 (!)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R${ITERM_ID} - --no-clobber
[{:key :6, :id "R${ITERM_ID} [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] 6 → Shift+1"} [:!S1 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 7 → Shift+2 (@)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+1)) - --no-clobber
[{:key :7, :id "R$((ITERM_ID+1)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] 7 → Shift+2"} [:!S2 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 8 → Shift+3 (#)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+2)) - --no-clobber
[{:key :8, :id "R$((ITERM_ID+2)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] 8 → Shift+3"} [:!S3 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 9 → Shift+4 ($)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+3)) - --no-clobber
[{:key :9, :id "R$((ITERM_ID+3)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] 9 → Shift+4"} [:!S4 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 0 → Shift+5 (%)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+4)) - --no-clobber
[{:key :0, :id "R$((ITERM_ID+4)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] 0 → Shift+5"} [:!S5 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+6 (f7) → Shift+6 (^)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+5)) - --no-clobber
[{:key :f7, :id "R$((ITERM_ID+5)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] Fn+6 → Shift+6"} [:!S6 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+7 (f8) → Shift+7 (&)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+6)) - --no-clobber
[{:key :f8, :id "R$((ITERM_ID+6)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] Fn+7 → Shift+7"} [:!S7 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+8 (f9) → Shift+8 (*)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+7)) - --no-clobber
[{:key :f9, :id "R$((ITERM_ID+7)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] Fn+8 → Shift+8"} [:!S8 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+9 (f10) → Shift+9 (()
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+8)) - --no-clobber
[{:key :f10, :id "R$((ITERM_ID+8)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] Fn+9 → Shift+9"} [:!S9 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+0 (f11) → Shift+0 ())
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+9)) - --no-clobber
[{:key :f11, :id "R$((ITERM_ID+9)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] Fn+0 → Shift+0"} [:!S0 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR
```

### Verification
```bash
npm run sync
```

---

## STOP: User Verification Required

After Stage 1 and Stage 2, commit and have user verify:
```bash
git add -A && git commit -m "Add tab/pane switching to InApp mode for Chrome and iTerm"
npm run sync
cd ~/.config && git add -A && git commit -m "Add tab/pane switching to InApp mode for Chrome and iTerm"
```

**User must test:**
- [ ] Chrome InApp mode: 6 → tab 1 (Cmd+1)
- [ ] Chrome InApp mode: 7 → tab 2 (Cmd+2)
- [ ] Chrome InApp mode: Fn+9 → last tab (Cmd+9)
- [ ] iTerm InApp mode: 6 → outputs "!" (Shift+1)
- [ ] iTerm InApp mode: 7 → outputs "@" (Shift+2)

**DO NOT PROCEED TO STAGE 3 UNTIL USER CONFIRMS WORKING.**

---

## Stage 3: Remove YUIOP from Chrome Mode (layer 3)

### Commands
```bash
bb scripts/edit/delete-rule.bb src/karabiner.edn R0296
bb scripts/edit/delete-rule.bb src/karabiner.edn R0297
bb scripts/edit/delete-rule.bb src/karabiner.edn R0298
bb scripts/edit/delete-rule.bb src/karabiner.edn R0299
bb scripts/edit/delete-rule.bb src/karabiner.edn R0304
bb scripts/edit/delete-rule.bb src/karabiner.edn R0300
bb scripts/edit/delete-rule.bb src/karabiner.edn R0301
bb scripts/edit/delete-rule.bb src/karabiner.edn R0302
bb scripts/edit/delete-rule.bb src/karabiner.edn R0303
```

### Verification and Commit
```bash
npm run sync
bb scripts/test/generate-tests.bb
npm run sync
git add -A && git commit -m "Remove YUIOP tab switching from Chrome mode (layer 3)"
cd ~/.config && git add -A && git commit -m "Remove YUIOP tab switching from Chrome mode (layer 3)"
```

**Expected test changes**: Chrome mode (layer=3) tests for y, u, i, o, p keys should now hit the global Desktop blockers instead of R0296-R0304.

---

## Stage 4: Remove YUIOP from TMUX Mode (layer 5)

**CRITICAL: ONLY delete these 10 rules. DO NOT delete R0354, R0355, or any other TMUX rules.**

### Commands
```bash
bb scripts/edit/delete-rule.bb src/karabiner.edn R0344
bb scripts/edit/delete-rule.bb src/karabiner.edn R0345
bb scripts/edit/delete-rule.bb src/karabiner.edn R0346
bb scripts/edit/delete-rule.bb src/karabiner.edn R0347
bb scripts/edit/delete-rule.bb src/karabiner.edn R0348
bb scripts/edit/delete-rule.bb src/karabiner.edn R0349
bb scripts/edit/delete-rule.bb src/karabiner.edn R0350
bb scripts/edit/delete-rule.bb src/karabiner.edn R0351
bb scripts/edit/delete-rule.bb src/karabiner.edn R0352
bb scripts/edit/delete-rule.bb src/karabiner.edn R0353
```

### Verification and Commit
```bash
npm run sync
bb scripts/test/generate-tests.bb
npm run sync
git add -A && git commit -m "Remove YUIOP from TMUX mode (layer 5)"
cd ~/.config && git add -A && git commit -m "Remove YUIOP from TMUX mode (layer 5)"
```

**Expected test changes**: TMUX mode (layer=5) tests for y, u, i, o, p keys and Fn+YUIOP (f15-f19) should now hit global blockers.

**Verify R0354 and R0355 still exist** (l → Cmd+d and LOpt+f1 → Cmd+Shift+d):
```bash
bb scripts/query/match-rules.bb src/karabiner.edn l --state "profile=Default:device=Desktop:layer=5"
```

---

## Stage 5: Update Documentation

### Files to Update
1. `src/layers/inapp.txt` - Add number key shortcuts
2. `src/layers/chrome.txt` - Remove YUIOP tab shortcuts
3. `src/layers/tmux.txt` - Remove YUIOP shortcuts
4. `.claude/reference.md` - Update shortcuts documentation

---

## Test Change Summary

### Expected Changes (OK to regenerate)
- New: InApp Chrome layer=10 tests for 6,7,8,9,0,f7,f8,f9,f10,f11 → match new rules
- New: InApp iTerm layer=10 tests for 6,7,8,9,0,f7,f8,f9,f10,f11 → match new rules
- Changed: Chrome mode layer=3 tests for y,u,i,o,p → now blocked
- Changed: TMUX mode layer=5 tests for y,u,i,o,p,f15,f16,f17,f18,f19 → now blocked

### Surprising (investigate before regenerating)
- Any number key in layer=10 matching the wrong app block
- Any unexpected layer transitions
- Rules being deleted that weren't in the delete list
- R0354 or R0355 affected in any way
- Other layers affected

---

## Manual Verification Checklist

After Stage 1+2 (before removing old rules):
- [ ] Chrome InApp: 6 → tab 1
- [ ] Chrome InApp: 7 → tab 2
- [ ] Chrome InApp: 0 → tab 5
- [ ] Chrome InApp: Fn+9 → last tab
- [ ] iTerm InApp: 6 → "!"
- [ ] iTerm InApp: 7 → "@"

After Stage 3:
- [ ] Chrome mode: y is blocked (no tab switch)

After Stage 4:
- [ ] TMUX mode: y is blocked (no output)
- [ ] TMUX mode: l still works (Cmd+d)
