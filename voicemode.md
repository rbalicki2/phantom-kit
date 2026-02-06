# Karabiner/Goku Configuration Summary

## Key Files
- `/Users/rbalicki/code/voicemode/karabiner.edn` - Main Goku config
- `~/.config/karabiner.edn` - Copy destination (run `cp karabiner.edn ~/.config/ && goku`)
- `~/.config/karabiner/karabiner.json` - Generated Karabiner config
- `/Users/rbalicki/code/voicemode/chrome-tab.sh` - Chrome profile switcher script
- `/Users/rbalicki/code/voicemode/karabiner-layer.1s.sh` - SwiftBar plugin (symlinked to ~/code/swiftbar/)

## Terminology
- **"hyper"** = right_control (NOT actual hyper key)
- User's keyboard has numbers/symbols swapped (bare 1 = !, shift+1 = 1)

## Workflow After Changes
```bash
cp /Users/rbalicki/code/voicemode/karabiner.edn ~/.config/ && goku
cd ~/.config && git add karabiner.edn karabiner/karabiner.json && git commit -m "message"
```

## Goku Modifier Legend
- `!C` = Command, `!Q` = right_command
- `!T` = left_control (for right_control use `{:key :x :modi {:mandatory [:right_control]}}`)
- `!O` = left_option, `!E` = right_option
- `!S` = left_shift, `!R` = right_shift
- `##` = optional any

## Layers

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
- **H** = Open Karabiner-EventViewer (temporary for debugging)

### Layer M (right_control+M)
- **M** = Control+C (terminal copy/interrupt)
- **H** = Command+C (GUI copy)
- **J** = Command+V (paste)

### Tmux Layer (right_control+J)
- Sends Control+A (tmux prefix) on entry
- **Y/U/I/O/P** = !, @, #, $, % (tmux windows 1-5)
- **Pending**: Should only activate in iTerm (`:applications` section added but condition not yet applied)

## SwiftBar Status
- Shows current layer in menu bar
- Reads from `/tmp/karabiner-layer`
- All layer entries/exits write to this file
- Uses Menlo font with fixed 8-char width

## Chrome Profile Switching
`chrome-tab.sh` uses System Events AppleScript to find windows by title:
- Personal profile windows contain "(Personal)"
- Work profile windows contain "Robert" but not "(Personal)"
- Requires `/usr/bin/osascript` in Accessibility permissions

## Pending Work
- Add iTerm-only condition to Tmux layer entry rule
- The `:applications {:iTerm ["com.googlecode.iterm2"]}` section was added
- Need to modify the tmux entry rule to include `:iTerm` condition
