# Keyboard Shortcuts Reference

## Layer N "Nav" (right_control+N)
- **H** = Karabiner-EventViewer, **J** = iTerm, **K** = VS Code, **L** = Signal
- **M** = Chrome Personal, **Comma** = Chrome Work
- **U** = Next tab, **I** = Previous tab
- **N** = Spotlight, **Enter** = Switch windows
- **Ctrl+H** = Close (Cmd+W)
- **P** = Screenshot full, **Ctrl+P** = Screenshot selection
- **Up/Down** = Left/right half, **Space** = Maximize
- **Shift+H/J** = Chrome Personal tabs 1-2, **Shift+K/L** = Chrome Work tabs 1-2
- **Shift+N/M/,/.//** = iTerm tab 2, pane 1, tmux windows 1-5

## Layer M (right_control+M)
- **H** = Copy, **J** = Paste, **K** = Cut, **L** = Undo
- **M** = Ctrl+C, **N** = Ctrl+R
- **I** = Select all, **O** = Find, **P** = Close window
- **Period** = Save, **Comma** = (free)

## Layer H (right_control+H)
- **H** = plus, **N** = equals (Shift variants add Cmd)
- **J/K** = Delete word left/right, **M/Comma** = Delete to line start/end
- **Up/Down** = Delete char left/right (Shift = select instead)
- **Y** = Cmd sub-layer, **U** = Ctrl sub-layer, **I** = Alt sub-layer, **O** = Cmd+Ctrl+Alt sub-layer
- **Ctrl+Y/U/I** = Combined modifier sub-layers (Ctrl+Cmd, Ctrl+Alt, Alt+Cmd)

## Term Layer (right_control+U, iTerm only)
- **H** = "git status ", **Ctrl+H** = + enter
- **J** = "git log ", **Ctrl+J** = + enter
- **K** = "git diff ", **Shift+K** = "git diff head" + enter, **Ctrl+K** = "gdmb" + enter
- **L** = "git commit ", **Ctrl+L** = "git commit -am 'wip'" + enter
- **N** = "git reset ", **Ctrl+N** = + enter
- **M** = "git checkout ", **Ctrl+M** = "gcmp" + enter
- **Comma** = "git stash" + enter, **Ctrl+Comma** = "git stash pop" + enter
- **I** = Split vertical (Cmd+D), **Ctrl+I** = Split horizontal (Cmd+Shift+D)

## Tmux Layer (right_control+J, iTerm only)
- **Y/U/I/O/P** = Tmux windows 1-5 (sends prefix + !/\@/#/$/%\)

## Chrome Layer (right_control+J, Chrome only)
- **H** = New tab, **J** = Prev tab (stays), **K** = Next tab (stays), **L** = Address bar
- **Ctrl+H** = Close tab (stays), **Shift+Ctrl+H** = Close tab + prev tab (stays)
- **Y/U/I/O/P** = Cmd+1/2/3/4/9
- **N** = Cmd+K, **Comma** = Search tabs

## Global RHS (Desktop, no layer)
- **Ctrl+Shift+Y** = Show current layer shortcuts (Hammerspoon overlay)
- **Right Cmd + J/K** = Word left/right
- **Right Cmd + M/Comma** = Line start/end
- **Right Cmd + Up/Down** = Left/right arrows
- **Page down/up** = Left/right click

## RHS Flag (right_control+7)
- Toggle on/off, combines with any layer
- Disables LHS keys: `=`, `g`
- SwiftBar shows "RHS-" prefix
