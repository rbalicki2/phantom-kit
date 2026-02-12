# Keyboard Shortcuts Reference

## Modal System Overview
- **Normal** = Default layer (layer selector) - entered with right_control from Ins
- **Ins** = Typing mode (all keys pass through) - entered with `j` from Normal
- Other layers entered from Normal with single keys: `n`, `m`, `i`, `h`, `u`, `k`
- All layers exit to Normal with `right_control` or `escape`

## Normal Layer (default)
- **J** = Enter Ins (typing mode)
- **N** = Enter Nav layer
- **M** = Enter Label Mode (Homerow)
- **I** = Enter Admin layer ⚙️
- **Comma** = Enter Comma layer (copy/paste/find)
- **L** = Enter L layer
- **U** = Enter Term layer (focuses iTerm)
- **H** = Enter Chrome/VSCode/TMUX (app-specific)
- **Right_control** = Send escape (stays in Normal)

## Ins Layer (J from Normal)
- All keys type normally
- **Right_control** = Return to Normal
- **Ctrl+M** = Homerow (stays in Ins)
- **[** = Backspace, **]** = Delete

### Brackets
- **Shift+Up/Down** = `[` `]`
- **Fn+Up/Down** = `{` `}`

### Mirrored Letters (Fn+key)
- Fn+Y→t, Fn+U→r, Fn+I→e, Fn+O→w, Fn+P→q
- Fn+H→g, Fn+J→f, Fn+K→d, Fn+L→s, Fn+;→a
- Fn+N→b, Fn+M→v, Fn+,→c, Fn+.→x, Fn+/→z

### Oneshots
- **Fn+Space** = Space + shift oneshot (next letter capital)
- **Fn+]** = Capital mirrored letter oneshot (next Fn+letter uppercase)

## Layer N "Nav" (N from Normal)
- **H** = Karabiner-EventViewer, **J** = iTerm, **K** = VS Code, **L** = Signal, **Fn+L** = Messages, **Ctrl+L** = WhatsApp, **O** = Obsidian
- **M** = Chrome Personal, **Comma** = Chrome Work
- **N** = Spotlight
- **Ctrl+H** = Close (Cmd+W)
- **Period** = Finder, **Fn+Period** = Go to Folder, **Ctrl+Period** = Go to Folder + Paste
- **Fn+J/K** = Back/Forward, **Fn+M/,** = Prev/Next tab, **Fn+Up/Down** = App switch
- **Enter** = Cmd+Enter

## Label Mode (M from Normal - Homerow)
- Labels: U I O P J K L ; N M , . /
- **H** = Cancel (escape) + exit to Ins
- **Right_control** = Cancel (escape) + exit to Normal

## Admin Layer ⚙️ (I from Normal)
- **Space** = Maximize, **Up** = Left half, **Down** = Right half
- **Enter** = Switch windows (Cmd+`)
- **P** = Screenshot full, **Ctrl+P** = Screenshot selection

## Comma Layer (Comma from Normal)
- **H** = Copy, **J** = Paste (→Ins), **K** = Find (→Ins), **Fn+K** = Find in files (→Ins), **L** = Undo, **Fn+L** = Redo
- **Comma** = Ctrl+C, **N** = Ctrl+R
- **I** = Select all, **O** = Cmd+Shift+P, **Fn+O** = Open, **P** = Cmd+P
- **Ctrl+H** = Close
- **Period** = Save, **M** = Toggle iso/pin mode

## Layer L (L from Normal)
- **H** = plus, **N** = equals (Fn variants add Cmd), **Ctrl+H** = Close
- **J/K** = Delete word left/right, **M/Comma** = Delete to line start/end
- **Up/Down** = Delete char left/right (Shift = select instead)
- **Y** = Cmd sub-layer, **U** = Ctrl sub-layer, **I** = Alt sub-layer, **O** = Cmd+Ctrl+Alt sub-layer
- **Ctrl+Y/U/I** = Combined modifier sub-layers (Ctrl+Cmd, Ctrl+Alt, Alt+Cmd)
- In sub-layers: **Fn+key** = modifier+mirrored LHS letter

## Term Layer (U from Normal, focuses iTerm)
- **H** = "git status ", **Ctrl+H** = + enter
- **J** = "git log ", **Ctrl+J** = + enter
- **K** = "git diff ", **Fn+K** = "git diff head" + enter, **Ctrl+K** = "gdmb" + enter
- **L** = "git commit -m ", **Ctrl+L** = "git commit -am 'wip'" + enter
- **N** = "git reset ", **Ctrl+N** = "grhh" + enter
- **M** = "git checkout ", **Ctrl+M** = "gcmp" + enter
- **Comma** = "git add -A && git stash" + enter, **Ctrl+Comma** = "git stash pop" + enter
- **I** = Split vertical (Cmd+D), **Ctrl+I** = Split horizontal (Cmd+Shift+D)

## Tmux Layer (H from Normal, iTerm only)
- **Y/U/I/O/P** = !, @, #, $, % (panes 1-5)
- **Shift+Y/U/I/O** = ^, &, *, ( (panes 6-9)

## Chrome Layer (H from Normal, Chrome only)
- **H** = New tab (→Ins), **J** = Prev tab (stays), **K** = Next tab (stays), **L** = Address bar (→Ins)
- **Fn+J** = gg (top), **Fn+K** = Shift+G (bottom)
- **Ctrl+H** = Close tab (stays), **Ctrl+Fn+H** = Close tab + prev tab (stays)
- **Y/U/I/O** = Tab 1/2/3/4, **Fn+Y/U/I/O** = Tab 5/6/7/8, **P** = Last tab
- **Fn+L** = Copy URL
- **N** = Cmd+K (→Ins), **Comma** = Search tabs (→Ins), **Period** = Refresh, **Fn+Period** = Hard refresh

## VS Code Layer (H from Normal, VS Code only)
- **H** = Copy rel path, **Fn+H** = Copy full path, **Ctrl+H** = Open in Chrome
- **J** = Go to def, **K** = Rename, **L** = Find refs
- **I** = 2nd tab, **Fn+I** = Move next tab, **Fn+U** = Move prev tab
- **O** = Sidebar, **P** = Terminal, **Comma** = Next error (stays), **Period** = Find next (stays)

## Global Shortcuts (Desktop, no layer)
- **Fn+Hotkey3** = Show current layer shortcuts (Hammerspoon overlay, Alt+F20)
- **Right Cmd + J/K** = Word left/right
- **Right Cmd + M/Comma** = Line start/end
- **Right Cmd + Up/Down** = Left/right arrows
- **Page down/up** = Left/right click
