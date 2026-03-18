# Phantom Kit - Reference

Quick lookup tables for shortcuts, mode values, and syntax.

## Modal System Overview

- **Normal** (layer 0) = Default layer (layer selector)
- **Ins** (layer 1) = Typing mode (passthrough)
- Other layers entered from Normal with single keys
- All layers exit to Normal with `right_control` alone

## Mode Values

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
| 13 | Label (Mouse) | 27 | L-Hyper-Shift |
| 28 | Grid | | |

## Layer Shortcuts

### Normal Layer (default)
- **j** = Enter Ins, **n** = Nav, **m** = Label Mode, **i** = Admin
- **comma** = Comma layer, **l** = L layer, **u** = Term layer
- **h** = Chrome/VSCode/TMUX (app-specific)
- **right_control** = Send escape (stays in Normal)

### Ins Layer (j from Normal)
- All keys type normally
- **right_control** = Return to Normal
- **[** = Backspace, **]** = Delete
- **Shift+Up/Down** = `[` `]`, **Fn+Up/Down** = `{` `}`
- **Fn+Space** = Space + shift oneshot
- **Fn+letter** = Mirrored LHS letter (lowercase)
- **Fn+]** = Shift mirror oneshot (next mirrored letter uppercase)

### Nav Layer (n from Normal)
- **Y** = Spotlight (→Ins), **Enter** = Cmd+Enter (→Ins)
- **H** = iTerm, **J** = VS Code, **K** = Karabiner-EventViewer
- **N** = Chrome Personal, **M** = Chrome Work
- **L** = Signal, **Fn+L** = Messages, **Ctrl+L** = WhatsApp
- **O** = Obsidian, **Period** = Finder

### Admin Layer (i from Normal)
- **Space** = Maximize, **Up** = Left half, **Down** = Right half
- **Enter** = Switch windows (Cmd+`)
- **P** = Screenshot full, **Fn+P** = Screenshot selection
- **L** = LLM blurb paste (→Ins), **M** = Move to next screen
- **Comma** = Toggle/select microphone

### Comma Layer (comma from Normal)
- **H** = Copy, **J** = Paste (→Ins), **L** = Undo, **Shift+L** = Redo
- **K** = Find (→Ins), **Shift+K** = Find in files
- **Comma** = Ctrl+C, **N** = Ctrl+R, **Period** = Save
- **I** = Select all, **O** = Command palette, **P** = Cmd+P
- **Ctrl+H** = Close, **M** = Cycle project mode

### L Layer (l from Normal)
- **H** = plus, **N** = equals, **Ctrl+H** = Close
- **Y** = Cmd sub-layer, **U** = Ctrl sub-layer, **I** = Alt sub-layer
- **O** = Cmd+Ctrl+Alt sub-layer
- Sub-layers: type any letter to send modifier+letter

### Term Layer (u from Normal, focuses iTerm)
- **H** = "git status ", **J** = "git log ", **K** = "git diff "
- **L** = "git commit -m ", **N** = "git reset ", **M** = "git checkout "
- **I** = "git push --no-verify" + enter, **Fn+I** = "git push -f" + enter
- Fn variants add enter, Ctrl variants run aliases

### InApp Layer (from Normal, app-dependent entry)
- **P** = Zoom in (Cmd+=), **Fn+P** = Zoom out (Cmd+-)
- **U** = Back (Cmd+[), **I** = Forward (Cmd+])
- **J** = Scroll down, **K** = Scroll up

### App-Specific (h from Normal)
**Chrome**: Y/U/I/O = Tabs 1-4, P = Last tab, Period = Refresh
**VSCode**: H = Copy path, J = Go to def, K = Rename, L = Find refs, Fn+J = Go to def in new tab, Fn+K = Quick fix
**TMUX**: J = Prev window, K = Next window, L = Split pane (→Ins), Y = Kill window, N = New window

## Goku Modifier Legend

- `!C` = Command, `!Q` = right_command
- `!T` = left_control (use explicit form for right_control)
- `!O` = left_option, `!E` = right_option
- `!S` = left_shift, `!R` = right_shift
- `##` = optional any

**For right_control**: `{:key :x :modi {:mandatory [:right_control]}}`
**For either shift**: `{:key :x :modi {:mandatory [:shift]}}`

## Terminology

- **"hyper"** = right_control (NOT actual hyper key)
- **"SHK"** = shift key (voice shorthand)
- **"QUK"** = quote key (')
- Numbers/symbols are swapped (bare 1 = !, shift+1 = 1)

## Project Modes

Cycle with Comma layer + M: iso → pin → pk → iso

Affects which iTerm tab Term layer focuses:
- **iso** = tab 2 (default)
- **pin** = tab 1
- **pk** = tab 3

## Global Remaps (All Profiles)

- Caps Lock = Control (held) / Escape (tapped)
- Numbers = Symbols, Shift+numbers = numbers
- Brackets = Cmd+brackets, Cmd+brackets = brackets
- Left shift alone = (, Right shift alone = )

## Desktop-Only Remaps

- LHS letter keys disabled (one-handed setup)
- Page down/up = left/right click
- Right command + J/K = Word left/right
- Right command + M/comma = Line start/end
- Space + navigation = Shift+navigation (selection)
