# Karabiner/Goku Configuration Context

This system is called **Phantom Kit**.

## User Interaction Notes
The user is using voice-to-text and may not fully think through requests before speaking. Claude should:
- Dictated letters are always uppercase, but use judgment for actual case (usually lowercase for filenames, code, etc.)
- Reason about what the user is actually trying to accomplish, not just literal words
- Ask clarifying questions if something doesn't align or seems off
- Catch likely mistakes (e.g., "Shift+Tab" when they mean "Ctrl+Shift+Tab" for previous tab in Chrome)
- If something can be verified mechanically (e.g., checking if a file exists, diffing configs, checking generated JSON), **ALWAYS do that instead of asking the user to verify**. Never say "try it and let me know" when you can just check the result yourself with a bash command.
- **CRITICAL: NEVER remove or replace an existing shortcut without explicit permission.** If implementing a new feature requires removing/changing an existing shortcut, STOP and ask the user first. Don't assume "remove the old ones" means all related shortcuts - clarify exactly which ones.
- **NEVER make shortcuts exit to Normal mode without explicit permission.** The user does not like escape/quote/other keys automatically exiting to Normal. Always ask before adding any "exit to Normal" behavior to a shortcut.
- **URL fetching is restricted by security policies.** If you need documentation from a URL, ask the user to provide the relevant content instead of trying to fetch it.
- **Race conditions in messages:** The user may send messages that refer to a past state due to timing/latency. If a message seems to refer to something already addressed or changed, it may have been sent before the user saw the latest changes.
- **Hammerspoon functions should be simple and stateless.** Complex logic, conditionals, and state tracking in Hammerspoon functions can lead to subtle bugs and race conditions. When possible, keep Hammerspoon functions minimal and push complexity to Karabiner rules or use dummy keys that only target apps listen for.

## ⚠️ CRITICAL: DO NOT BREAK EXISTING FUNCTIONALITY ⚠️

**There are NO unit tests for this config. Every change risks breaking something the user relies on.**

Claude has REPEATEDLY broken core functionality by making "simple fixes" without fully understanding the implications:
- Removing shift output from Quote broke ALL capitalization
- Moving rules around broke square brackets
- "Fixing" one layer broke unrelated layers due to rule ordering

**Before making ANY change:**
1. TRACE through exactly what will happen with the change
2. Consider ALL places the affected keys/variables are used
3. If unsure about syntax or behavior, check existing working examples FIRST
4. For complex changes, make ONE small change, sync, and verify before continuing
5. DO NOT chain multiple "fixes" without testing each one

**The Quote key** now just outputs quote when tapped - it's no longer complex. The shift_mirror_oneshot variable is used for Fn+] uppercase mirrored letters.

When in doubt, ASK before changing. A broken keyboard config is extremely frustrating to debug.

## Git Commit Policy
When modifying any file, always check if it's in a git repository and commit changes:
1. After editing a file, check if it's in a git repo: `git -C "$(dirname /path/to/file)" rev-parse --git-dir 2>/dev/null`
2. If it is a git repo, commit the change with an informative message describing what was changed
3. Use short, descriptive commit messages (e.g., "tmuxp: use viddy instead of watch, add 5 shell windows")
4. Don't include Claude attribution in commit messages

## On Startup
Previous Claude sessions sometimes leave things in a broken state. At the start of a session, verify:

1. **Check if karabiner.edn is in sync**:
   ```bash
   diff /Users/rbalicki/code/voicemode/karabiner.edn ~/.config/karabiner.edn
   ```
   - If they differ, the voicemode version is the source of truth
   - Run `npm run sync` to copy and run goku

2. **Check git status in both repos**:
   ```bash
   git -C /Users/rbalicki/code/voicemode status
   git -C ~/.config status
   ```
   - Look for uncommitted changes that should have been committed
   - If voicemode has uncommitted karabiner.edn changes, commit them
   - If ~/.config has uncommitted changes, they may need to be committed or discarded

3. **Check for pending tasks** in the "Pending Tasks" section below

If things look messy, ask the user before making changes.

## Key Files

### Voicemode Repo (`/Users/rbalicki/code/voicemode/`, git repo)
Source of truth for Karabiner config and related scripts.
- `karabiner.edn` - Main Goku config (source of truth, copied to ~/.config/)
- `package.json` - Contains `npm run sync` script to copy and run goku
- `shortcuts.md` - Human-readable shortcuts reference (keep up to date)
- `CLAUDE.md` - This file, context for Claude sessions
- `layers/*.txt` - Individual layer summaries for Hammerspoon overlay
  - `norm.txt`, `ins.txt`, `nav.txt`, `l.txt`, `term.txt`, `tmux.txt`, `chrome.txt`, `vscode.txt`, `comma.txt`, `i.txt`
- `chrome-tab.sh` - Chrome profile switcher script (AppleScript)
- `vscode-open-in-chrome.sh` - Opens VS Code file path in Chrome (used by VS Code layer Ctrl+H)
- `karabiner-layer.100ms.sh` - SwiftBar plugin (symlinked to ~/code/swiftbar/)
- `llm-blurb.txt` - LLM conversation preferences text (pasted by Admin layer L)
- `todos.md` - Pending tasks and feature ideas
- `kinesis-layout1.txt` - Kinesis Advantage 360 firmware layout (copy to /Volumes/ADV360/layouts/)
- `kinesis-keycodes.txt` - Kinesis key codes reference (from Appendix A of programming guide)
- `Adv360-SmartSet-Direct-Programming-Guide-Version-8-8-25.pdf` - Kinesis programming reference

### Config Repo (`~/.config/`, git repo)
Destination for generated configs.
- `karabiner.edn` - Copy of voicemode version (goku reads from here)
- `karabiner/karabiner.json` - Generated by goku (do not edit directly)

### Hammerspoon (`~/.hammerspoon/`, git repo)
- `init.lua` - Hammerspoon config for layer overlay (Ctrl+Shift+Y)
  - Contains `layerFiles` map that must be updated when adding new layers

### Temp Files (runtime state, not in git)
- `/tmp/karabiner-layer` - Current layer code (read by SwiftBar and Hammerspoon)
- `/tmp/karabiner-project` - iso/pin mode state

### Wispr Flow (voice-to-text app)
- `~/Library/Application Support/Wispr Flow/config.json` - Shortcuts and preferences
  - `shortcuts` object maps key codes to actions (ptt, lens, popo)
  - `splitKeybinds` array has same shortcuts in different format
  - Restart Wispr Flow after editing for changes to take effect

## Pending Tasks
See `todos.md` for the list of pending tasks.

## Workflow After Changes
**CRITICAL**: Keep ALL documentation up-to-date after any keybinding changes:
- `CLAUDE.md` - Main reference with full details
- `shortcuts.md` - Quick reference
- `layers/*.txt` - **MUST update when ANY layer shortcut changes** (Hammerspoon overlay files)
- `rhs-slots.md` - RHS key slot grid for Ins mode (tracks what each key+modifier combo does)

**Overlay files (`layers/*.txt`)**: These are shown to the user via Ctrl+Shift+Y overlay. If you change shortcuts in a layer, you MUST update the corresponding `.txt` file or the overlay will be wrong.

**Consistency**: SwiftBar (`karabiner-layer.100ms.sh`) and Hammerspoon overlay (`layers/*.txt`) should use the same names and emojis for layers.

After every change, Claude should:
1. Commit locally in voicemode repo with a short message
2. Run `npm run sync` (copies karabiner.edn to ~/.config/ and runs goku)
3. Commit changes in ~/.config repo

```bash
# In voicemode repo
git add karabiner.edn && git commit -m "message"
npm run sync
cd ~/.config && git add karabiner.edn karabiner/karabiner.json && git commit -m "message"
```
Note: Don't include Claude attribution in commit messages.

## Pre-Commit Checklist

Before committing changes to `karabiner.edn`, verify these common gotchas:

### Shell Commands
- [ ] **Only ONE `{:shell ...}` per rule** - If a rule has multiple `{:shell ...}` blocks, only the LAST one executes. Combine with `&&`: `{:shell "cmd1 && cmd2 && cmd3"}`
- [ ] For osascript, use multiple `-e` flags in ONE shell: `{:shell "osascript -e '...' -e '...'"}`

### Layer Variables
- [ ] Layer transitions set ALL THREE variables correctly:
  - `["layer_X" 0/1]` - the specific layer
  - `["in_any_layer" 0/1]` - 0 for Normal/Ins, 1 for modal layers
  - `["layer_normal" 0/1]` - 1 for Normal, 0 otherwise
- [ ] Layer exits write to `/tmp/karabiner-layer` for SwiftBar

### New Layers
- [ ] Update PANIC BUTTON rule to clear new layer variable
- [ ] Add case to `karabiner-layer.100ms.sh` SwiftBar script
- [ ] Create `layers/*.txt` file for Hammerspoon overlay
- [ ] Update `layerFiles` map in `~/.hammerspoon/init.lua`

### Modifiers
- [ ] RHS shortcuts use `right_control` not `left_control`
- [ ] Shift matching uses `{:key :x :modi {:mandatory [:shift]}}` to match EITHER shift (not `!S` which is left-only)
- [ ] Mouse buttons with modifiers use explicit form: `{:pkey :button1 :modi [:left_command]}`

### Rule Ordering
- [ ] In-layer Ctrl+KEY rules come BEFORE layer entry rules they might conflict with
- [ ] More specific rules come before general rules
- [ ] `shift_mirror_oneshot` rules come before global number-swap rules

## Formatting Guidelines
- Never use tables for listing layer commands - use bullets instead
- Combine related commands onto single lines when logical (e.g., base + modifier variants)

## Testing & Syntax Verification
**Important**: Claude has a history of not knowing exact Goku/Karabiner syntax. For any non-trivial or unfamiliar constructs:
- **Prioritize testable changes over documentation updates.** If a change needs both code and docs, flash/sync the code first so the user can test, then update docs.
- Ask the user to test the change before committing to more work
- When unsure about syntax, check existing working examples in karabiner.edn first
- If something doesn't work, the syntax is likely wrong - don't assume the approach is correct
- **Update the "Things I've Learned" section below** when discovering what works/doesn't work

## Things I've Learned
Document syntax discoveries here to avoid repeating mistakes:

- `:!Cbutton1` does NOT work - modifier shorthand doesn't apply to mouse buttons
- For mouse buttons with modifiers, use explicit form: `{:pkey :button1 :modi [:left_command]}`
- Rule ordering matters: earlier rules in the config take precedence. If a global rule (like page_down→button1) has no layer condition, it will match before layer-specific rules. Add exclusion conditions like `["layer_h_cmd" 0]` to global rules when needed.
- Modifier+click only works reliably for Cmd modifier. Ctrl+click, Alt+click, etc. don't exit the layer properly. Only Cmd sub-layer has page_down/page_up for clicking.
- **RHS layer shortcuts with Ctrl modifier must use `right_control`**, not `left_control`.
- **The `{:alone :escape}` mechanism does NOT work for layer exits.** When right_control is mapped to send escape via `{:alone :escape}`, the escape event goes directly to the OS rather than being re-evaluated by Karabiner rules. Each layer needs an explicit right_control exit rule.
- **Right_control exit rules must use `:alone` modifier** if the layer has Ctrl+key combos. Use `[:right_control :right_control ["layer_X" 1] {:alone [["layer_X" 0] ...]}]` - this passes through right_control normally (so Ctrl+key works) and only exits the layer when tapped alone.
- **Layer entry rules MUST exclude other active layers** to prevent conflicts. When a user enters Nav layer with right_control+N and then presses Shift+J (while still holding right_control), the key combo is actually right_control+Shift+J. Without proper exclusions, this can accidentally trigger other layer entries (like TMUX's right_control+J).
- **Goku only supports ONE condition per rule.** Multiple conditions at the end of a rule like `["layer_h" 0] ["in_any_layer" 0]` will only use the first one. The `:conditions` key syntax also doesn't work. Use rule ordering instead (see "Layer Entry Conflict Prevention" section).
- **Block-level conditions combine with per-rule conditions.** To have both app AND variable conditions, put the app condition in `:rules [:Desktop :Chrome ...]` and the variable condition on the rule itself. This generates a conditions array with both.
- **`!S` only matches LEFT shift.** To match EITHER shift key, use explicit form: `{:key :j :modi {:mandatory [:shift]}}`. The shorthand `!S` = left_shift, `!R` = right_shift specifically.
- **Karabiner only runs ONE shell_command per rule.** If multiple `{:shell ...}` are in the `to` array, only the LAST one executes. Combine commands into a single shell string with `&&` or `;`. Example: `{:shell "warpd --grid & echo norm > /tmp/karabiner-layer"}` instead of separate `{:shell "warpd"}` and `[:layer "norm"]`.
- **Karabiner shell commands don't have /opt/homebrew/bin in PATH.** Use full path `/opt/homebrew/bin/hs` instead of just `hs` when calling Hammerspoon CLI from Karabiner shell commands.
- **Goku requires numeric variable values.** String values like `["mode" "norm"]` don't work - Goku requires integers like `["mode" 0]`. This is a Goku limitation, not Karabiner.
- **Rules need a key output to work.** A rule with only variable sets and shell commands but no key output won't trigger. Use `:vk_none` as a no-op output: `[:right_control [:vk_none ["mode" 0] {:shell "..."}] ["mode" 28]]`

## State Machine (3 Variables)

The modal system uses THREE variables. This eliminates "illegal states" where multiple layers could theoretically be active.

**Variables:**
- `mode` - Current layer (0-27)
- `in_modal` - Is mode >= 2? (0=no, 1=yes) - for blocking rules
- `submode` - Overlay state within Ins mode (0-4)

**Mode values:**
| Mode | Layer | Mode | Layer |
|------|-------|------|-------|
| 0 | Normal | 14 | L-Cmd |
| 1 | Ins | 15 | L-Cmd-Shift |
| 2 | Nav | 16 | L-Ctrl |
| 3 | Chrome | 17 | L-Ctrl-Shift |
| 4 | VSCode | 18 | L-CtrlCmd |
| 5 | TMUX | 19 | L-CtrlCmd-Shift |
| 6 | Comma | 20 | L-CtrlAlt |
| 7 | L (base) | 21 | L-CtrlAlt-Shift |
| 8 | Term | 22 | L-Alt |
| 9 | Admin | 23 | L-Alt-Shift |
| 10 | InApp | 24 | L-AltCmd |
| 11 | AppSwitcher | 25 | L-AltCmd-Shift |
| 12 | WindowSwitcher | 26 | L-Hyper |
| 13 | Mouse | 27 | L-Hyper-Shift |

**Submode values** (overlay on mode=1 Ins):
- 0 = None
- 1 = shift_mirror_oneshot (Fn+] for uppercase mirrored)
- 2 = shift_oneshot (Fn+Space for next letter capitalized)
- 3 = rcmd_h_mode (delete chord: rcmd+H then J/K/M/,)
- 4 = rcmd_n_mode (select chord: rcmd+N then J/K/M/,)

**in_modal**: Set to 1 when mode >= 2 (modal layers), 0 when mode is 0 or 1. Used for blocking rules like `[:##a :vk_none ["in_modal" 1]]`.

### Layer Transitions

**→ Normal**: `["mode" 0] ["in_modal" 0] ["submode" 0]`
**→ Ins**: `["mode" 1] ["in_modal" 0]`
**→ Modal layer N**: `["mode" N] ["in_modal" 1]`

**Checklist when adding layer transitions**:
1. ✅ Set `mode` to the new layer number
2. ✅ Set `in_modal` appropriately (0 for Normal/Ins, 1 for modal)
3. ✅ Write layer name to `/tmp/karabiner-layer` for SwiftBar

### Handling "Not in mode N" (Exclusion)

Karabiner can only check equality (`mode == N`), not inequality (`mode != N`). Two patterns:

**1. Rule ordering** - specific rules BEFORE generic rules:
```clojure
;; L-Cmd specific (comes first, matches mode 14)
[:page_down [Cmd+click...] ["mode" 14]]
;; Generic (comes after, matches when above doesn't)
[:page_down :button1]
```

**2. Explicit positive conditions** - list modes where rule applies:
```clojure
;; Works from Normal and Ins only
[{...} [...] ["mode" 0]]
[{...} [...] ["mode" 1]]
```

**Note**: With single-mode, mutual exclusion is automatic. Can't be in mode 14 AND mode 22.

## Karabiner Rule Precedence

Karabiner evaluates rules **in order** and uses the **first matching rule**. A rule "matches" when:
1. The input key/modifier matches the rule's `from` clause
2. ALL conditions on the rule are satisfied

**Two ways to control precedence:**

1. **Rule ordering**: Place more specific rules earlier in the config. Earlier rules are checked first.
   - Example: Put `shift_mirror_oneshot` number rules before global number-swap rules

2. **Conditions**: Add conditions to exclude certain states.
   - Example: Add `["shift_mirror_oneshot" 0]` to number-swap rules so they don't match when oneshot is active

**Which to use:**
- **Rule ordering** works when rules are in the same file/section and you control the order
- **Conditions** are necessary when rules are in different sections or you can't reorder them (e.g., global rules in "ALL PROFILES" that need to yield to layer-specific rules in "DESKTOP ONLY")

**Important**: Karabiner does NOT re-process output keys. When a rule sends `:!S5` (Shift+5), that goes directly to the app - it won't be caught by other Karabiner rules. This prevents infinite loops but also means output transformations only happen once.

## Layer Entry Conflict Prevention (Rule Ordering)

**Context**: Layers are entered from Normal layer with single keys. Within layers, Ctrl+KEY combos exist. Rule ordering ensures in-layer Ctrl+KEY rules take precedence.

**Solution**: Rule ordering. Karabiner evaluates rules in order and uses the first match. Place in-layer rules BEFORE layer entry rules they might conflict with.

**How it works**:
- In-layer rules have condition `["mode" N]` (only match when IN that mode)
- Layer entry rules have condition `["mode" 0]` (only match when in Normal mode)
- When rules are ordered correctly, in-layer rules match first and consume the keypress

**Example**: VS Code layer has Ctrl+H. If user holds right_control while in VS Code and presses H:
- VS Code Ctrl+H rule: `[{:key :h :modi {:mandatory [:right_control]}} [action] ["mode" 4]]`
- This matches first because VS Code rules come before Normal layer rules

**Checklist when adding a new layer**:
1. Pick the next available mode number (see State Machine section)
2. If the layer has Ctrl+KEY shortcuts that conflict with other layer entries, place its rule block BEFORE those layer entry blocks
3. Update SwiftBar script (`karabiner-layer.100ms.sh`) with new layer case
4. Create `layers/*.txt` file for Hammerspoon overlay
5. Update `layerFiles` map in `~/.hammerspoon/init.lua`

## Panic Button (Fn+HK3)

**Fn+HK3** (Shift+Alt+F19) is a global emergency reset that clears ALL state and returns to Normal mode. Use it when the keyboard gets into a stuck or unexpected state.

**What it does:**
- Releases any held modifiers (like Cmd from app/window switcher)
- Sets `mode=0`, `in_modal=0`, `submode=0`
- Writes "norm" to `/tmp/karabiner-layer`

**No maintenance needed** for new layers - the panic button simply resets the 3 variables to their Normal state.

## Layer Exit Modes: Normal vs Ins

When a layer action exits, decide whether to go to **Normal** or **Ins** mode:

- **→ Ins mode**: Action opens a text field where user will type (address bar, search, find dialog, Spotlight)
- **→ Normal mode**: Action doesn't require typing (tab switch, close, refresh, undo, copy/paste)

**Examples that should exit to Ins:**
- Chrome: new tab, address bar, search tabs, Cmd+K
- Comma: find (Cmd+F), find in files
- Nav: Spotlight (Cmd+Space)

## Kinesis Advantage 360 Layout

The keyboard firmware layout is stored in `kinesis-layout1.txt` (source of truth in voicemode repo).

**To update the layout:**
1. Edit `kinesis-layout1.txt` in this repo
2. Check if the Kinesis drive is mounted: `test -d /Volumes/ADV360 && echo "mounted"`
3. If mounted, copy to ALL NINE layout slots (user sometimes ends up in other layers):
   ```bash
   for i in 1 2 3 4 5 6 7 8 9; do cp kinesis-layout1.txt /Volumes/ADV360/layouts/layout${i}.txt; done
   ```
4. Eject the drive and the keyboard will reload the layout

**Note:** The drive is only mounted when the keyboard is in programming mode. If `/Volumes/ADV360` doesn't exist, the keyboard isn't connected as a drive.

**Layout file format:** Uses Kinesis macro syntax where `{key}>{output}` maps Fn+key to output. Keys in `<function1>` section are Fn layer mappings. The current config maps RHS keys to F-keys (some with Alt modifier) for use with Karabiner. Modifier+key triggers use syntax like `{rctr}{hk4}>{esc}`.

**Key codes reference:** Always consult `kinesis-keycodes.txt` for valid key names. Do NOT guess key codes - look them up in that file first.

**Kinesis key codes:** See `kinesis-keycodes.txt` for all valid key names including modifiers (`rctr`, `lctr`, `ralt`, `lalt`, `rsft`, `lsft`, `rcmd`, `lcmd`) and special keys (`esc`, `enter`, `space`, etc.).

**Kinesis Fn layer sends left Alt:** The `{-lalt}` syntax sends left_option. This is an exception to the usual right-modifier rule. In Karabiner rules matching Fn+key combos, use `!O` (left_option) not `!E` (right_option).

**Fn+Shift cannot be combined:** The Kinesis Fn key and Shift key cannot be pressed together - this is a hardware/firmware limitation. Never create shortcuts that require Fn+Shift+key combinations.

**Fn key is NOT detectable:** The Kinesis Fn key is handled entirely at firmware level. It does NOT send any keycode to the computer. You cannot detect "Fn held" or "Fn alone" in Karabiner - only Fn+key combinations (which send F-keys or Alt+F-keys per the layout file).

**Avoid F13 and F14:** These open macOS System Settings even with modifiers. Never use them in layouts.

## Keyboard Context
This config is designed for a **Kinesis Advantage 360** with right-hand-side (RHS) layers. **This is a ONE-HANDED (right hand only) keyboard setup on Desktop profile.** The user only presses right-hand keys. All layer keys (H, J, K, L, M, N, comma, etc.) are on the right side of the keyboard.

**Important implications:**
- If you find yourself setting up anything that requires left-hand-side keys, you are likely making a mistake - confirm with the user first
- When adding modifier rules (Cmd, Ctrl, etc.), use **right_command** (`!Q`) and **right_control** - never assume left modifiers
- The user accesses left-hand letters via Fn+letter (Fn+J → f, Fn+K → d, etc.)

### Right Thumb Cluster (6 keys)
```
+------+------+
| Ctrl | Cmd  |
+------+------+
| PgUp | PgDn |
+------+------+
| Enter| Space|
+------+------+
```
- **Ctrl** = right_control (used for layer exits, modifier combos)
- **Cmd** = right_command (used for word/line navigation)
- **Page Up/Down** = mapped to left/right click
- **Enter** = return_or_enter
- **Space** = spacebar

## Terminology
- **"hyper"** = right_control (NOT actual hyper key)
- **"SHK"** = shift key (user's voice shorthand)
- **"QUK"** = quote key (')
- User's keyboard has numbers/symbols swapped (bare 1 = !, shift+1 = 1)

## Goku Modifier Legend
- `!C` = Command, `!Q` = right_command
- `!T` = left_control (for right_control use `{:key :x :modi {:mandatory [:right_control]}}`)
- `!O` = left_option, `!E` = right_option
- `!S` = left_shift, `!R` = right_shift
- `##` = optional any

## Config Structure
The config is organized into two sections:
1. **ALL PROFILES** - Rules without profile conditions (caps lock, number/symbol swap, brackets, shifts)
2. **DESKTOP ONLY** - Rules with `:Desktop` condition (layers, navigation, app-specific behavior)

## Current Layers

### Modal System
The layer system is modal (like vim):
- **Normal** = Default layer (layer selector). Active keys: j, n, m, h, u, k. All other keys disabled.
- **Ins** = Typing mode (all keys pass through)
- Other layers entered from Normal with single keys
- All layers exit to Normal with `right_control` alone (escape does NOT exit layers)

### Normal Layer (default)
- **j** = Enter Ins (typing mode)
- **n** = Enter Nav layer
- **m** = Enter Label Mode (Homerow Search)
- **i** = Enter Admin layer ⚙️
- **comma** = Enter Comma layer
- **l** = Enter L layer
- **u** = Enter Term layer (focuses iTerm)
- **h** = Enter Chrome/VSCode/TMUX (app-specific)
- **right_control** = Send escape (stays in Normal)
- All other letter keys disabled

### Ins Layer (j from Normal)
- All keys type normally (passthrough)
- **right_control** = Return to Normal
- **[** = Backspace, **]** = Delete
- **Shift+Up** = `[`, **Shift+Down** = `]`
- **Fn+Up** = `{`, **Fn+Down** = `}`
- **Fn+Space** = Space + shift oneshot (next letter capitalized)
- **Fn+Enter** = Shift+Enter

#### Fn+Letter (Mirrored Letters)
Fn+letter outputs the mirrored LHS letter (lowercase):
- Fn+Y→t, Fn+U→r, Fn+I→e, Fn+O→w, Fn+P→q
- Fn+H→g, Fn+J→f, Fn+K→d, Fn+L→s, Fn+;→a
- Fn+N→b, Fn+M→v, Fn+,→c, Fn+.→x, Fn+/→z

#### Fn+Number (Mirrored Symbols)
Fn+number outputs the mirrored LHS symbol:
- Fn+6→%, Fn+7→$, Fn+8→#, Fn+9→@, Fn+0→!

#### Fn+] (shift_mirror_oneshot)
Pressing Fn+] enters shift_mirror_oneshot mode. The next letter typed outputs the uppercase mirrored letter:
- Y→T, U→R, I→E, O→W, P→Q
- H→G, J→F, K→D, L→S, ;→A
- N→B, M→V, ,→C, .→X, /→Z

Numbers output mirrored digit: 6→5, 7→4, 8→3, 9→2, 0→1

#### rcmd+H Chord (Delete Mode)
Hold rcmd+H then press a navigation key to delete:
- **J** = delete word left, **K** = delete word right
- **M** = delete to line start, **,** = delete to line end
- **up** = delete char left, **down** = delete char right

#### rcmd+N Chord (Select Mode)
Hold rcmd+N then press a navigation key to select:
- **J** = select word left, **K** = select word right
- **M** = select to line start, **,** = select to line end
- **up** = select char left, **down** = select char right

### Layer N "Nav" (n from Normal)
- **Y** = Command+Space (Spotlight) - exits to Ins
- **N** = Chrome Personal profile
- **M** = Chrome Work profile
- **H** = Open iTerm
- **J** = Open VS Code
- **K** = Open Karabiner-EventViewer
- **L** = Open Signal, **Fn+L** = Open Messages, **Ctrl+L** = Open WhatsApp
- **O** = Open Obsidian
- **Period** = Finder, **Fn+Period** = Finder Go to Folder, **Ctrl+Period** = Go to Folder + Paste
- **Enter** = Cmd+Enter (exits to Ins)

### Admin Layer ⚙️ (i from Normal)
- **Space** = Maximize (BTT: Ctrl+Opt+Shift+Cmd+G)
- **Up** = Left half (BTT: Ctrl+Opt+Shift+Cmd+V)
- **Down** = Right half (BTT: Ctrl+Opt+Shift+Cmd+B)
- **Enter** = Command+` (switch windows)
- **P** = Cmd+Shift+3 (screenshot full), **Ctrl+P** = Cmd+Shift+4 (screenshot selection)
- **Y** = Restart Whispering
- **L** = LLM blurb paste (exits to Ins)


### Comma Layer (comma from Normal)
- **Comma** = Control+C (terminal copy/interrupt)
- **N** = Control+R (terminal reverse search)
- **H** = Command+C (GUI copy)
- **J** = Command+V (paste) - exits to Ins
- **K** = Cmd+F (find), **Shift+K** = Cmd+Shift+F (find in files)
- **L** = Command+Z (undo), **Shift+L** = Redo
- **Period** = Command+S (save)
- **I** = Command+A (select all) - exits to Ins
- **O** = Cmd+Shift+P (command palette), **Shift+O** = Cmd+O (open)
- **P** = Command+P
- **Ctrl+H** = Command+W (close)
- **M** = Toggle iso/pin mode (exits layer)

### Layer L (l from Normal)
- **H** = plus, **Shift+H** = Cmd+plus (exits)
- **N** = equals, **Shift+N** = Cmd+equals (exits)
- **Ctrl+H** = Cmd+W (close, exits)
- **Enter** = Cmd+Enter (exits)
- **Y** = Enter Cmd sub-layer (see below)

#### H Layer Modifier Sub-layers
Sub-layers allow pressing modifier+letter combinations easily. Press the entry key from L layer, then any a-z sends modifier+letter and exits to base layer.

| Entry Key | Modifier | UI Code |
|-----------|----------|---------|
| Y | Cmd | H-C |
| Ctrl+Y | Ctrl+Cmd | H-TC |
| U | Ctrl | H-T |
| Ctrl+U | Ctrl+Alt | H-TO |
| I | Alt | H-O |
| Ctrl+I | Alt+Cmd | H-OC |
| O | Cmd+Ctrl+Alt | H-CTO |

**Cmd sub-layer special**: page_down = Cmd+click, page_up = Cmd+right-click

### Term Layer (u from Normal, focuses iTerm)
Git shortcuts for terminal. Uses osascript to type text. All shortcuts exit to Ins mode.
- **H** = "git status ", **Fn+H** = + enter
- **J** = "git log ", **Fn+J** = + enter
- **K** = "git diff ", **Fn+K** = "git diff head" + enter, **Ctrl+K** = "gdmb" + enter
- **L** = "git commit --no-verify -m ", **Fn+L** = "git commit -am 'wip' --no-verify" + enter
- **N** = "git reset ", **Fn+N** = "grhh" + enter
- **M** = "git checkout ", **Fn+M** = "gcmp" + enter
- **,** = "git add -A && git stash" + enter, **Fn+,** = "git stash pop" + enter
- **.** = "git add ", **Fn+.** = "git add -A" + enter
- **Y** = "gh pr create" + enter
- **I** = "git push --no-verify" + enter

### Tmux Layer (h from Normal, iTerm only)
- Sends Control+A (tmux prefix) on entry
- **Y/U/I/O/P** = !, @, #, $, % (panes 1-5)
- **Fn+Y/U/I/O/P** = ^, &, *, (, ) (panes 6-10)
- **H** = Split vertical (Cmd+D), **J** = Split horizontal (Cmd+Shift+D)
- Only activates when iTerm is foreground app

### Chrome Layer (h from Normal, Chrome only)
- Only activates when Google Chrome is foreground app
- **Y/U/I/O** = Tab 1/2/3/4, **Fn+Y/U/I/O** = Tab 5/6/7/8
- **P** = Last tab
- **Fn+L** = Copy URL
- **M** = Dark mode (Alt+D)
- **Fn+J/K** = Move tab left/right - stays
- **Period** = Refresh, **Fn+Period** = Hard refresh

### VS Code Layer (k from Normal, VS Code only)
- Only activates when VS Code is foreground app
- **H** = Copy relative path, **Shift+H** = Copy full path
- **Ctrl+H** = Copy rel path + open in Chrome (cc/isof prefix based on project)
- **J** = Cmd+D (go to definition)
- **K** = F2 (rename)
- **L** = Cmd+R (find references)
- **I** = Cmd+2 (create 2nd tab), **Shift+I** = Ctrl+Cmd+2 (move to next tab)
- **Shift+U** = Ctrl+Cmd+1 (move to prev tab)
- **O** = Cmd+B (sidebar toggle)
- **P** = Cmd+` (terminal toggle)
- **Comma** = Alt+B (next error, stays)
- **Period** = Alt+V (find next, stays)

### Layer Exit Methods
All layers exit to Normal (not to Ins/typing mode) by:
- Pressing **right_control** alone (escape does NOT exit layers)
- Most layer actions auto-exit (except Chrome J/K for tab cycling)

**Convention**: Actions stay in layer if repeatable (delete, select, tab cycling). Actions exit layer if one-shot (open app, zoom, type symbol).

## Global Remaps (All Profiles)
- Caps Lock = Control (held) / Escape (tapped)
- Numbers = Symbols, Shift+numbers = numbers
- Brackets = Cmd+brackets (back/forward), Cmd+brackets = brackets
- Backslash/pipe swapped
- Left shift alone = (
- Right shift alone = )

## Desktop-Only Remaps
- LHS letter keys (q,w,e,r,t,a,s,d,f,g,z,x,c,v,b) = disabled (no-op)
- Left/right arrows = disabled (no-op)
- Page down/up = left/right click
- Right control + [ / ] = backspace/delete
- Left command alone = {, Shift+left_command alone = [
- Right command alone = }, Shift+right_command alone = ]
- Right command + J/K = Option+left/right (word navigation)
- Right command + M/comma = Cmd+left/right (line start/end)
- Right command + up/down = left/right arrows
- Space + J/K/M/comma/up/down = Shift+navigation (selection)
- Home = Enter
- Equals and backtick swapped (physical ` = equals, physical = key = backtick)
- right_control+6 = F9 (text-to-speech)
- right_control+Y = Shift+F9 (toggle recording)
- Space+Enter = Cmd+Enter

## Iso/Pin Mode
Toggle with M layer + Comma. Affects which iTerm tab the Term layer focuses:
- **pin** = iTerm tab 1
- **iso** = iTerm tab 2 (default)

Stored in `/tmp/karabiner-project`. Shown in SwiftBar status as prefix (e.g., "iso-Nav", "pin-M").

## SwiftBar Status
- Shows current layer in menu bar
- Reads from `/tmp/karabiner-layer` for layer, `/tmp/karabiner-project` for iso/pin mode
- All layer entries/exits write to these files
- Layers: `norm`, `ins`, `n`, `i`, `comma`, `l`, `lC`, `lTC`, `lT`, `lTO`, `lO`, `lOC`, `lCTO`, `tmux`, `chrome`, `vscode`, `term`
- Format: `{mode}-{layer}` (e.g., "iso-Norm", "pin-Nav")
- Uses Menlo font
- **Important**: When adding a new layer, update `karabiner-layer.100ms.sh` to handle the new case

## Hammerspoon Layer Overlay
- **Ctrl+Shift+Y** shows overlay with current layer's shortcuts
- Reads `/tmp/karabiner-layer` to determine active layer
- Displays content from `/Users/rbalicki/code/voicemode/layers/*.txt`
- Auto-hides after 5 seconds, or click to dismiss
- **Important**: When adding a new layer, add a corresponding `.txt` file in `layers/` and update the `layerFiles` map in `init.lua`

## Chrome Profile Switching
`chrome-tab.sh` uses System Events AppleScript to find windows by title:
- Personal profile windows contain "(Personal)"
- Work profile windows contain "Robert" but not "(Personal)"
- Requires `/usr/bin/osascript` in Accessibility permissions

## Application Conditions
In `:applications` section:
```clojure
:applications {
  :iTerm ["com.googlecode.iterm2"]
  :Chrome ["com.google.Chrome"]
}
```

To use in rules, add `:iTerm` or `:Chrome` as a condition after the action:
```clojure
[{:key :j :modi {:mandatory [:right_control]}} [action] :Chrome ["layer_chrome" 0]]
```

## Known Issues
- User's Kinesis keyboard sometimes loses state (not a Karabiner issue)
- If left_command acts like control, it's the Kinesis - unplug/replug to fix

## Desktop Profile
The active profile is "Desktop" - Desktop-specific rules use `:Desktop` condition.
