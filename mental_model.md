# Phantom Kit Mental Model

Concise source of truth for how the modal keyboard system behaves.

## Overview

One-handed (right hand) modal keyboard system. Three variables track state:
- `mode` (0-28): Current layer
- `in_modal` (0/1): Whether in a modal layer (mode >= 2)
- `submode` (0-4): Overlay state within Ins mode

## Layer Map

| Mode | Name | Entry | Exit | Purpose |
|------|------|-------|------|---------|
| 0 | Normal | Default / right_ctrl from any | - | Layer selector, most keys disabled |
| 1 | Ins | J from Normal | right_ctrl | Typing mode, all keys pass through |
| 2 | Nav | N from Normal | right_ctrl | Open apps, Spotlight |
| 3 | Chrome | H from Normal (Chrome focused) | right_ctrl | Chrome-specific shortcuts |
| 4 | VSCode | H from Normal (VSCode focused) | right_ctrl | VS Code-specific shortcuts |
| 5 | TMUX | H from Normal (iTerm focused) | right_ctrl | Tmux pane switching |
| 6 | Comma | , from Normal | right_ctrl | Clipboard, find, save, undo |
| 7 | L | L from Normal | right_ctrl | Symbols, modifier sub-layers |
| 8 | Term | U from Normal (focuses iTerm) | right_ctrl | Git commands typed into terminal |
| 9 | Admin | I from Normal | right_ctrl | Window management, screenshots |
| 10 | InApp | Fn+HK4 from Normal/Ins | right_ctrl | In-app navigation (scroll, tabs, back/fwd) |
| 13 | Label | M from Normal, Ctrl+M from Ins | right_ctrl / click | Mouse via label overlay |
| 28 | Grid | Fn+M from Normal | right_ctrl / click | Mouse via 3x3 grid |

## Normal Layer (Mode 0)

The hub. Most keys disabled. Active keys:
- **J** → Ins
- **N** → Nav
- **M** → Label mode
- **Fn+M** → Grid mode
- **,** → Comma
- **L** → L layer
- **U** → Term (focuses iTerm)
- **H** → Chrome/VSCode/TMUX (app-dependent)
- **I** → Admin
- **Fn+HK4** → InApp
- **right_ctrl** → sends Escape (stays in Normal)

## Ins Layer (Mode 1)

Typing mode. All keys pass through with these additions:
- **[** → Backspace, **]** → Delete
- **Shift+Up/Down** → `[` / `]`
- **Fn+Up/Down** → `{` / `}`
- **Fn+Space** → Space + shift oneshot (next letter capitalized)
- **Fn+]** → shift_mirror_oneshot (next mirrored letter uppercase)
- **Fn+letter** → mirrored LHS letter (Fn+J→f, Fn+K→d, etc.)
- **rcmd+H then J/K/M/,** → delete word/line left/right
- **rcmd+N then J/K/M/,** → select word/line left/right
- **rcmd+J/K** → word left/right
- **rcmd+M/,** → line start/end

## Nav Layer (Mode 2)

Open applications and locations:
- **Y** → Spotlight (Cmd+Space) → Ins
- **N** → Chrome Personal profile
- **M** → Chrome Work profile
- **H** → iTerm
- **J** → VS Code
- **K** → Karabiner-EventViewer
- **L** → Signal, **Fn+L** → Messages, **Ctrl+L** → WhatsApp
- **O** → Obsidian
- **.** → Finder, **Fn+.** → Go to Folder, **Ctrl+.** → Go to Folder + Paste

## Comma Layer (Mode 6)

Clipboard and editing:
- **,** → Ctrl+C (terminal interrupt)
- **H** → Cmd+C (copy)
- **J** → Cmd+V (paste) → Ins
- **K** → Cmd+F (find), **Shift+K** → Cmd+Shift+F
- **L** → Cmd+Z (undo), **Shift+L** → redo
- **.** → Cmd+S (save)
- **I** → Cmd+A (select all) → Ins
- **O** → Cmd+Shift+P (command palette)
- **P** → Cmd+P
- **N** → Ctrl+R (terminal reverse search)
- **Ctrl+H** → Cmd+W (close)

## L Layer (Mode 7)

Symbols and modifier sub-layers:
- **H** → `+`, **Shift+H** → Cmd+plus
- **N** → `=`, **Shift+N** → Cmd+equals
- **Y** → enter Cmd sub-layer (any letter sends Cmd+letter)
- **U** → enter Ctrl sub-layer
- **I** → enter Alt sub-layer
- **Ctrl+Y/U/I** → combined modifier sub-layers

## Term Layer (Mode 8)

Git commands (focuses iTerm, types text):
- **H** → `git status `, **Fn+H** → + enter
- **J** → `git log `, **Fn+J** → + enter
- **K** → `git diff `, **Fn+K** → `git diff head` + enter
- **L** → `git commit -m `, **Fn+L** → `git commit -am 'wip'` + enter
- **N** → `git reset `, **Fn+N** → `grhh` + enter
- **M** → `git checkout `, **Fn+M** → `gcmp` + enter
- **,** → `git add -A && git stash` + enter, **Fn+,** → `git stash pop` + enter
- **.** → `git add `, **Fn+.** → `git add -A` + enter
- **Y** → `gh pr create` + enter
- **I** → `git push` + enter

## Admin Layer (Mode 9)

Window management and system:
- **Space** → Maximize (BTT)
- **Up** → Left half, **Down** → Right half
- **Enter** → Cmd+` (switch windows)
- **P** → Screenshot full, **Ctrl+P** → Screenshot selection
- **Y** → Restart Whispering
- **L** → LLM blurb paste → Ins

## InApp Layer (Mode 10)

In-app navigation (works across apps):
- **J/K** → Scroll down/up (hold to repeat)
- **Fn+J/K** → End/Home
- **U/I** → Back/Forward
- **M/,** → Prev/Next tab
- **Y** → Close tab, **Shift+Y** → Close + prev, **Fn+Y** → Reopen
- **L** → Address bar (Cmd+L) → Ins
- **O** → Open (Cmd+O) → Ins
- **Up** → App switcher, **Down** → Window switcher
- **HK4** → Go to Nav layer

App-specific H/N vary (Chrome: Cmd+K/Cmd+T, VSCode: Cmd+Shift+P/Cmd+N, etc.)

## Label Mode (Mode 13)

Mouse navigation via label overlay (Homerow):
- Type labels to select target, then:
- **Space** → Click
- **Fn+Space** → Cmd+Click
- **Shift+Space** → Shift+Click
- **Enter** → Right-click
- **Fn+Enter** → Double-click
- **Shift+Enter** → Cmd+Shift+Click
- **Up** → Hover (no click)

Returns to origin layer (Normal or Ins based on entry).

## Grid Mode (Mode 28)

Mouse navigation via 3x3 grid (warpd):
- **U/I/O/J/K/L/M/,/.** → Navigate grid subdivisions
- **N** → Enter normal mode (fine IJKL movement)
- Same click keys as Label mode
- **right_ctrl** → Cancel (no click)

Grid tool positions mouse, we handle all clicks via Hammerspoon.

## Mouse Click Actions (Both Modes)

| Key | Action |
|-----|--------|
| Space | Left click |
| Fn+Space | Cmd+click |
| Shift+Space | Shift+click |
| Enter | Right-click |
| Fn+Enter | Double-click |
| Shift+Enter | Cmd+Shift+click |
| Up | Hover (move mouse, no click) |

## Global Behaviors

- **right_ctrl alone** → Exit to Normal (from any modal layer)
- **Ctrl+J** → Exit to Ins (from some layers)
- **Panic button (Fn+HK3)** → Reset all state, return to Normal
- **Page Down/Up** → Left/Right click (global)

## Kinesis Fn Layer

Fn+key sends F-keys (some with Alt modifier):
- Fn+letter → F13-F24 or Alt+F1-F12
- Used for secondary actions in each layer

## Files

- `karabiner.edn` → Goku config (source, copied to ~/.config/)
- `~/.hammerspoon/init.lua` → Overlays, clicks, scrolling
- `warpd.conf` → Grid mode settings
- `layers/*.txt` → Overlay content for each layer
