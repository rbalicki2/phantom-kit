# Keyboard Shortcuts Reference

## Modal System Overview
- **Normal** = Default layer (layer selector) - entered with right_control from Ins
- **Ins** = Typing mode (all keys pass through) - entered with `j` from Normal
- Other layers entered from Normal with single keys: `n`, `m`, `i`, `h`, `u`, `k`
- All layers exit to Normal with `right_control` or `escape`

## Normal Layer (default)
- **j** = Enter Ins (typing mode)
- **n** = Enter Nav layer
- **m** = Enter Mouse layer, **Shift+M** = Homerow Search
- **i** = Enter Layer I (window management)
- **comma** = Enter Comma layer (copy/paste/find)
- **l** = Enter L layer
- **u** = Enter Term layer (focuses iTerm)
- **h** = Enter Chrome/VSCode/TMUX (app-specific)
- **right_control** = Send escape (stays in Normal)

## Ins Layer (j from Normal)
- All keys type normally
- **right_control** = Return to Normal
- **Quote (')** = Shift when held, quote when tapped alone
- **Shift** = Mirror mode (RHS → LHS keys)

## Layer N "Nav" (n from Normal)
- **H** = Karabiner-EventViewer, **J** = iTerm, **K** = VS Code, **L** = Signal, **Shift+L** = Messages, **Ctrl+L** = WhatsApp, **O** = Obsidian
- **M** = Chrome Personal, **Comma** = Chrome Work
- **N** = Spotlight
- **Ctrl+H** = Close (Cmd+W)
- **Period** = Finder, **Shift+Period** = Go to Folder, **Ctrl+Period** = Go to Folder + Paste
- **Shift+J/K** = Back/Forward, **Shift+M/,** = Prev/Next tab, **Shift+Up/Down** = App switch
- Releasing Shift exits to Normal

## Mouse Layer (m from Normal)
- Grid mode with warpd

## Layer I (i from Normal - Window Management)
- **Space** = Maximize, **Up** = Left half, **Down** = Right half
- **Enter** = Switch windows (Cmd+`)
- **P** = Screenshot full, **Ctrl+P** = Screenshot selection

## Comma Layer (comma from Normal)
- **H** = Copy, **J** = Paste (→Ins), **K** = Find (→Ins), **Shift+K** = Find in files (→Ins), **L** = Undo, **Shift+L** = Redo
- **Comma** = Ctrl+C, **N** = Ctrl+R
- **I** = Select all, **O** = Cmd+Shift+P, **Shift+O** = Open, **P** = Cmd+P
- **Ctrl+H** = Close
- **Period** = Save, **M** = Toggle iso/pin mode

## Layer L (l from Normal)
- **H** = plus, **N** = equals (Shift variants add Cmd), **Ctrl+H** = Close
- **J/K** = Delete word left/right, **M/Comma** = Delete to line start/end
- **Up/Down** = Delete char left/right (Shift = select instead)
- **Y** = Cmd sub-layer, **U** = Ctrl sub-layer, **I** = Alt sub-layer, **O** = Cmd+Ctrl+Alt sub-layer
- **Ctrl+Y/U/I** = Combined modifier sub-layers (Ctrl+Cmd, Ctrl+Alt, Alt+Cmd)

## Term Layer (u from Normal, focuses iTerm)
- **H** = "git status ", **Ctrl+H** = + enter
- **J** = "git log ", **Ctrl+J** = + enter
- **K** = "git diff ", **Shift+K** = "git diff head" + enter, **Ctrl+K** = "gdmb" + enter
- **L** = "git commit -m ", **Ctrl+L** = "git commit -am 'wip'" + enter
- **N** = "git reset ", **Ctrl+N** = "grhh" + enter
- **M** = "git checkout ", **Ctrl+M** = "gcmp" + enter
- **Comma** = "git add -A && git stash" + enter, **Ctrl+Comma** = "git stash pop" + enter
- **I** = Split vertical (Cmd+D), **Ctrl+I** = Split horizontal (Cmd+Shift+D)

## Tmux Layer (k from Normal, iTerm only)
- **Y/U/I/O/P** = !, @, #, $, % (panes 1-5)
- **Shift+Y/U/I/O** = ^, &, *, ( (panes 6-9)

## Chrome Layer (k from Normal, Chrome only)
- **H** = New tab (→Ins), **J** = Prev tab (stays), **K** = Next tab (stays), **L** = Address bar (→Ins)
- **Shift+J** = gg (top), **Shift+K** = Shift+G (bottom)
- **Ctrl+H** = Close tab (stays), **Shift+Ctrl+H** = Close tab + prev tab (stays)
- **Y/U/I/O** = Tab 1/2/3/4, **Shift+Y/U/I/O** = Tab 5/6/7/8, **P** = Last tab
- **Shift+L** = Copy URL
- **N** = Cmd+K (→Ins), **Comma** = Search tabs (→Ins), **Period** = Refresh

## VS Code Layer (k from Normal, VS Code only)
- **H** = Copy rel path, **Shift+H** = Copy full path, **Ctrl+H** = Open in Chrome
- **J** = Go to def, **K** = Rename, **L** = Find refs
- **I** = 2nd tab, **Shift+I** = Move next tab, **Shift+U** = Move prev tab
- **O** = Sidebar, **P** = Terminal, **Comma** = Next error (stays), **Period** = Find next (stays)

## Global RHS (Desktop, no layer)
- **Ctrl+Shift+Y** = Show current layer shortcuts (Hammerspoon overlay)
- **Right Cmd + J/K** = Word left/right
- **Right Cmd + M/Comma** = Line start/end
- **Right Cmd + Up/Down** = Left/right arrows
- **Page down/up** = Left/right click

## RHS Flag (right_control+7 enter, right_control+8 exit)
- Combines with any layer
- Disables LHS keys (only matters in Ins layer)
- SwiftBar shows "RHS-" prefix
