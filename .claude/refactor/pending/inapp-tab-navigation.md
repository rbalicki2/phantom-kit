# InApp Tab Navigation Plan

## Goal

Add number keys (6-0) in InApp Nav mode (layer 10) to switch tabs, with app-specific behavior:
- **Chrome**: Cmd+N for tab N
- **iTerm** (assuming tmux): Ctrl+B then pane number

Also: Remove YUIOP tab navigation from Chrome mode (layer 3) and TMUX mode (layer 5).

Note: VSCode is explicitly out of scope for this change.

## Confirmed Number Mapping

Position-based (left to right = tab 1 to 10):

| Key | Chrome | iTerm/tmux |
|-----|--------|------------|
| 6 | Cmd+1 (tab 1) | Ctrl+B, 1 (pane 1) |
| 7 | Cmd+2 (tab 2) | Ctrl+B, 2 (pane 2) |
| 8 | Cmd+3 (tab 3) | Ctrl+B, 3 (pane 3) |
| 9 | Cmd+4 (tab 4) | Ctrl+B, 4 (pane 4) |
| 0 | Cmd+5 (tab 5) | Ctrl+B, 5 (pane 5) |
| Fn+6 (f7) | Cmd+6 (tab 6) | Ctrl+B, 6 (pane 6) |
| Fn+7 (f8) | Cmd+7 (tab 7) | Ctrl+B, 7 (pane 7) |
| Fn+8 (f9) | Cmd+8 (tab 8) | Ctrl+B, 8 (pane 8) |
| Fn+9 (f10) | Cmd+9 (last tab) | Ctrl+B, 9 (pane 9) |
| Fn+0 (f11) | Cmd+9 (last tab) | Ctrl+B, 0 (pane 10) |

## Current State

### Chrome Mode (layer 3) - Rules to Remove
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

### TMUX Mode (layer 5) - Rules to Remove
```
R0344: y → Shift+1, AltIns
R0345: u → Shift+2, AltIns
R0346: i → Shift+3, AltIns
R0347: o → Shift+4, AltIns
R0348: p → Shift+5, AltIns
R0349-R0353: Fn+YUIOP → Shift+6-0, AltIns
R0354: l → Cmd+d, AltIns
R0355: LOpt+f1 → Cmd+Shift+d, AltIns
```
NOTE: These just type numbers, not actual pane navigation.

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

For tmux pane selection, we need to send Ctrl+B then the pane number.
Goku syntax: `[:!Tb :1]` sends Ctrl+B then "1".

```bash
# Continue from NEXT_ID+10
ITERM_ID=$((NEXT_ID+10))

# 6 → Ctrl+B, 1 (pane 1)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R${ITERM_ID} - --no-clobber
[{:key :6, :id "R${ITERM_ID} [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] 6 → tmux pane 1"} [:!Tb :1 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 7 → Ctrl+B, 2 (pane 2)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+1)) - --no-clobber
[{:key :7, :id "R$((ITERM_ID+1)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] 7 → tmux pane 2"} [:!Tb :2 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 8 → Ctrl+B, 3 (pane 3)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+2)) - --no-clobber
[{:key :8, :id "R$((ITERM_ID+2)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] 8 → tmux pane 3"} [:!Tb :3 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 9 → Ctrl+B, 4 (pane 4)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+3)) - --no-clobber
[{:key :9, :id "R$((ITERM_ID+3)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] 9 → tmux pane 4"} [:!Tb :4 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# 0 → Ctrl+B, 5 (pane 5)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+4)) - --no-clobber
[{:key :0, :id "R$((ITERM_ID+4)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] 0 → tmux pane 5"} [:!Tb :5 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+6 (f7) → Ctrl+B, 6 (pane 6)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+5)) - --no-clobber
[{:key :f7, :id "R$((ITERM_ID+5)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] Fn+6 → tmux pane 6"} [:!Tb :6 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+7 (f8) → Ctrl+B, 7 (pane 7)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+6)) - --no-clobber
[{:key :f8, :id "R$((ITERM_ID+6)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] Fn+7 → tmux pane 7"} [:!Tb :7 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+8 (f9) → Ctrl+B, 8 (pane 8)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+7)) - --no-clobber
[{:key :f9, :id "R$((ITERM_ID+7)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] Fn+8 → tmux pane 8"} [:!Tb :8 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+9 (f10) → Ctrl+B, 9 (pane 9)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+8)) - --no-clobber
[{:key :f10, :id "R$((ITERM_ID+8)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] Fn+9 → tmux pane 9"} [:!Tb :9 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR

# Fn+0 (f11) → Ctrl+B, 0 (pane 10/0)
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R$((ITERM_ID+9)) - --no-clobber
[{:key :f11, :id "R$((ITERM_ID+9)) [profile=Default:device=Desktop:dsk_layer=10:app=iTerm] Fn+0 → tmux pane 0"} [:!Tb :0 ["dsk_layer" 10] ["dsk_ins_sub_mode" -1] ["dsk_return_to_layer" -1]] [["dsk_layer" 10]]]
EOFR
```

### Verification
```bash
npm run sync
```

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

### Verification
```bash
npm run sync
bb scripts/test/generate-tests.bb
npm run sync
```

**Expected test changes**: Chrome mode (layer=3) tests for y, u, i, o, p keys should now hit the global Desktop blockers instead of R0296-R0304.

---

## Stage 4: Remove YUIOP from TMUX Mode (layer 5)

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
bb scripts/edit/delete-rule.bb src/karabiner.edn R0354
bb scripts/edit/delete-rule.bb src/karabiner.edn R0355
```

### Verification
```bash
npm run sync
bb scripts/test/generate-tests.bb
npm run sync
```

**Expected test changes**: TMUX mode (layer=5) tests for y, u, i, o, p, l keys should now hit global blockers.

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
- Changed: TMUX mode layer=5 tests for y,u,i,o,p,l → now blocked

### Surprising (investigate before regenerating)
- Any number key in layer=10 matching the wrong app block
- Any unexpected layer transitions
- Rules being deleted that weren't in the delete list
- Other layers affected

---

## Manual Verification Checklist

- [ ] Chrome: 6 → tab 1
- [ ] Chrome: 7 → tab 2
- [ ] Chrome: 0 → tab 5
- [ ] Chrome: Fn+9 → last tab
- [ ] iTerm/tmux: 6 → pane 1
- [ ] iTerm/tmux: 7 → pane 2
- [ ] Chrome mode: y is blocked (no tab switch)
- [ ] TMUX mode: y is blocked (no output)
