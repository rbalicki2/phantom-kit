# Karabiner/Goku Configuration Context

## Key Files
- `/Users/rbalicki/code/voicemode/karabiner.edn` - Main Goku config (source of truth)
- `/Users/rbalicki/code/voicemode/shortcuts.md` - Human-readable shortcuts reference (keep up to date)
- `~/.config/karabiner.edn` - Copy destination
- `~/.config/karabiner/karabiner.json` - Generated Karabiner config
- `/Users/rbalicki/code/voicemode/chrome-tab.sh` - Chrome profile switcher script
- `/Users/rbalicki/code/voicemode/karabiner-layer.1s.sh` - SwiftBar plugin (symlinked to ~/code/swiftbar/)

## Workflow After Changes
Always keep CLAUDE.md and shortcuts.md up-to-date after any keybinding changes.

After every change, Claude should:
1. Commit locally in voicemode repo with a short message
2. Copy karabiner.edn to ~/.config/
3. Run goku
4. Commit changes in ~/.config repo

```bash
# In voicemode repo
git add karabiner.edn && git commit -m "message"
# Then
cp /Users/rbalicki/code/voicemode/karabiner.edn ~/.config/ && goku
cd ~/.config && git add karabiner.edn karabiner/karabiner.json && git commit -m "message"
```
Note: Don't include Claude attribution in commit messages.

## Formatting Guidelines
- Never use tables for listing layer commands - use bullets instead
- Combine related commands onto single lines when logical (e.g., base + modifier variants)

## Testing & Syntax Verification
**Important**: Claude has a history of not knowing exact Goku/Karabiner syntax. For any non-trivial or unfamiliar constructs:
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

## Terminology
- **"hyper"** = right_control (NOT actual hyper key)
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

### Layer N "Nav" (right_control+N)
- **N** = Command+Space (Spotlight)
- **Enter** = Command+` (switch windows)
- **Space** = Maximize (BTT: Ctrl+Opt+Shift+Cmd+G)
- **Up** = Left half (BTT: Ctrl+Opt+Shift+Cmd+V)
- **Down** = Right half (BTT: Ctrl+Opt+Shift+Cmd+B)
- **M** = Chrome Personal profile
- **Comma** = Chrome Work profile
- **U** = Ctrl+Tab (next tab)
- **I** = Ctrl+Shift+Tab (previous tab)
- **H** = Open Karabiner-EventViewer
- **J** = Open iTerm
- **K** = Open VS Code
- **L** = Open Signal
- **Shift+N/M/,/.//** = iTerm tab 2, pane 1, tmux windows 1-5
- **Shift+H/J** = Chrome Personal tabs 1-2
- **Shift+K/L** = Chrome Work tabs 1-2

### Layer M (right_control+M)
- **M** = Control+C (terminal copy/interrupt)
- **N** = Control+R (terminal reverse search)
- **H** = Command+C (GUI copy)
- **J** = Command+V (paste)
- **K** = Command+X (cut)
- **L** = Command+Z (undo)
- **Period** = Command+S (save)
- **I** = Command+A (select all)
- **O** = Command+F (find)
- **P** = Command+W (close)
- **Comma** = (free)

### Layer H (right_control+H)
- **J/K** = Delete word left/right (stays)
- **M/Comma** = Delete to line start/end (stays)
- **Up/Down** = Delete char left/right (stays)
- **Shift+above** = Select instead of delete (stays)
- **H** = plus, **Shift+H** = Cmd+plus (exits)
- **N** = equals, **Shift+N** = Cmd+equals (exits)
- **Y** = Enter Cmd sub-layer (see below)

#### H Layer Modifier Sub-layers
Sub-layers allow pressing modifier+letter combinations easily. Press the entry key from H layer, then any a-z sends modifier+letter and exits to base layer.

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

### Term Layer (right_control+U, iTerm only)
Git shortcuts for terminal. Uses osascript to type text.
- **H** = "git status ", **Ctrl+H** = + enter
- **J** = "git log ", **Ctrl+J** = + enter
- **K** = "git diff ", **Shift+K** = "git diff head" + enter, **Ctrl+K** = "gdmb" + enter
- **L** = "git commit ", **Ctrl+L** = "git commit -am 'wip'" + enter
- **N** = "git reset ", **Ctrl+N** = + enter
- **M** = "git checkout ", **Ctrl+M** = "gcmp" + enter
- **,** = "git stash" + enter, **Ctrl+,** = "git stash pop" + enter
- **I** = Cmd+D (split vertical), **Ctrl+I** = Cmd+Shift+D (split horizontal)

### Tmux Layer (right_control+J, iTerm only)
- Sends Control+A (tmux prefix) on entry
- **Y/U/I/O/P** = !, @, #, $, % (tmux windows 1-5)
- Only activates when iTerm is foreground app

### Chrome Layer (right_control+J, Chrome only)
- Only activates when Google Chrome is foreground app
- **H** = Cmd+T (new tab) - exits layer
- **J** = Ctrl+Shift+Tab (previous tab) - stays in layer
- **K** = Ctrl+Tab (next tab) - stays in layer
- **L** = Cmd+L (address bar) - exits layer
- **M** = Cmd+W (close tab) - stays in layer
- **Comma** = Cmd+Shift+A (search tabs) - exits layer
- **Y** = Cmd+1
- **U** = Cmd+2
- **I** = Cmd+3
- **O** = Cmd+4
- **P** = Cmd+9
- **N** = Cmd+K

### RHS Flag (right_control+7 to toggle)
- Independent boolean flag, combines with any layer
- Toggles on/off (press again to exit)
- Disables LHS keys: `=`, `g`
- SwiftBar shows "RHS-" prefix (e.g., "RHS-Nav", "RHS-M")

### Layer Exit Methods
All layers can be exited by:
- Pressing **escape**
- Pressing **right_control** alone (also sends escape)
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
- Right control alone = Escape (frees up physical escape key)
- Page down/up = left/right click
- Left/right arrows = backspace/delete
- Right control + [ / ] = backspace/delete
- Left command alone = {, Shift+left_command alone = [
- Right command alone = }, Shift+right_command alone = ]
- Right command + J/K = Option+left/right (word navigation)
- Right command + M/comma = Cmd+left/right (line start/end)
- Right command + up/down = left/right arrows
- Space + J/K/M/comma/up/down = Shift+navigation (selection)
- Home = Enter
- Single quote = Control (held) / quote (tapped)
- Equals and backtick swapped (physical ` = equals, physical = key = backtick)
- right_control+6 = F9 (text-to-speech)
- right_control+Y = Shift+F9 (toggle recording)
- Space+Enter = Cmd+Enter

## SwiftBar Status
- Shows current layer in menu bar
- Reads from `/tmp/karabiner-layer` for layer, `/tmp/karabiner-rhs` for RHS flag
- All layer entries/exits write to these files
- Layers: `n`, `m`, `h`, `hC`, `hTC`, `hT`, `hTO`, `hO`, `hOC`, `hCTO`, `tmux`, `chrome`, `term`, default `-`
- RHS flag prefixes output (e.g., "RHS-Nav")
- Uses Menlo font with fixed 8-char width
- **Important**: When adding a new layer, update `karabiner-layer.1s.sh` to handle the new case

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
