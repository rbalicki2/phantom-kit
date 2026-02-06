# Karabiner/Goku Configuration Context

## Key Files
- `/Users/rbalicki/code/voicemode/karabiner.edn` - Main Goku config (source of truth)
- `~/.config/karabiner.edn` - Copy destination
- `~/.config/karabiner/karabiner.json` - Generated Karabiner config
- `/Users/rbalicki/code/voicemode/chrome-tab.sh` - Chrome profile switcher script
- `/Users/rbalicki/code/voicemode/karabiner-layer.1s.sh` - SwiftBar plugin (symlinked to ~/code/swiftbar/)

## Workflow After Changes
```bash
cp /Users/rbalicki/code/voicemode/karabiner.edn ~/.config/ && goku
cd ~/.config && git add karabiner.edn karabiner/karabiner.json && git commit -m "message"
```
Note: Don't include Claude attribution in commit messages.

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

### Layer N (right_control+N)
- **Enter** = Command+` (switch windows)
- **Space** = Maximize (BTT: Ctrl+Opt+Shift+Cmd+G)
- **Up** = Left half (BTT: Ctrl+Opt+Shift+Cmd+V)
- **Down** = Right half (BTT: Ctrl+Opt+Shift+Cmd+B)
- **M** = Chrome Personal profile
- **Comma** = Chrome Work profile
- **Y/U** = Chrome Personal tabs 1-2
- **I/O** = Chrome Work tabs 1-2
- **J** = Open iTerm
- **K** = Open VS Code
- **H** = Open Karabiner-EventViewer
- **L** = Open Signal

### Layer M (right_control+M)
- **M** = Control+C (terminal copy/interrupt)
- **N** = Control+R (terminal reverse search)
- **H** = Command+C (GUI copy)
- **J** = Command+V (paste)

### Tmux Layer (right_control+J, iTerm only)
- Sends Control+A (tmux prefix) on entry
- **Y/U/I/O/P** = !, @, #, $, % (tmux windows 1-5)
- Only activates when iTerm is foreground app

### Chrome Layer (right_control+J, Chrome only)
- Only activates when Google Chrome is foreground app
- **H** = Cmd+T (new tab) - exits layer
- **J** = Ctrl+Shift+Tab (previous tab) - stays in layer
- **K** = Ctrl+Tab (next tab) - stays in layer
- **L** = Cmd+Shift+A - exits layer
- **Y** = Cmd+1
- **U** = Cmd+2
- **I** = Cmd+3
- **O** = Cmd+4
- **P** = Cmd+9
- **N** = Cmd+K

### Layer Exit Methods
All layers can be exited by:
- Pressing **escape**
- Pressing **right_control** alone
- Most layer actions auto-exit (except Chrome J/K for tab cycling)

## Global Remaps (All Profiles)
- Caps Lock = Control (held) / Escape (tapped)
- Numbers = Symbols, Shift+numbers = numbers
- Brackets = Cmd+brackets (back/forward), Cmd+brackets = brackets
- Backslash/pipe swapped
- Left shift alone = (
- Right shift alone = )

## Desktop-Only Remaps
- Page down/up = left/right click
- Left/right arrows = backspace/delete
- Left command alone = {, Shift+left_command alone = [
- Right command alone = }, Shift+right_command alone = ]
- Right command + J/K = Option+left/right (word navigation)
- Right command + M/comma = Cmd+left/right (line start/end)
- Right command + up/down = left/right arrows
- Space + J/K/M/comma/up/down = Shift+navigation (selection)
- Home = Enter
- Single quote = Control (held) / quote (tapped)
- Equals and backtick swapped
- right_control+H = F9 (text-to-speech)
- right_control+Y = Shift+F9 (toggle recording)
- Space+Enter = Cmd+Enter

## SwiftBar Status
- Shows current layer in menu bar
- Reads from `/tmp/karabiner-layer`
- All layer entries/exits write to this file
- Cases: `n`, `m`, `tmux`, `chrome`, default `-`
- Uses Menlo font with fixed 8-char width

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
