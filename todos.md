# Pending Tasks

If work is interrupted or incomplete, document it here so future sessions can continue:

- Create a Tampermonkey setup that exposes functions callable from a layer
- Wifi notifier
- Browser extension: Add current page to extension override for blocking
- Admin layer: restart Hammerspoon, reload Karabiner, clean up this layer, etc.
- Log-Streamer sub-layer: Admin or Term layer keys to tail specific logs (system, browser console, project) into floating overlay (Cmd+up/down/plus/minus for navigation)
- Archive Chrome tabs: shortcut to save all open tabs (to file/bookmarks) and close them
- Deep link replacer: copy URL and view/transform it for other contexts
- Brightness and media controls: layer shortcuts for screen brightness, volume, play/pause, next/prev track
- Caffeine toggle: shortcut to prevent/allow Mac sleep
- Homerow modifier+click (Shift, Cmd, etc.): Use Hammerspoon to enable modifier+click via Homerow
  - Goal: Press Shift+M (or similar) to activate Homerow with shift held, so clicking a label does shift+click
  - Problem: Karabiner can't detect when Homerow finishes; Hammerspoon keyStrokes bypass Karabiner
  - Solution: Hammerspoon handles everything directly (no Karabiner communication needed)
  - Implementation steps:
    1. Karabiner shortcut (e.g., Shift+M from a layer) runs a shell command to signal Hammerspoon
    2. Hammerspoon receives signal, stores which modifier to apply (shift, cmd, etc.)
    3. Hammerspoon activates Homerow via `hs.application.launchOrFocus("Homerow")` + AppleScript
    4. Hammerspoon polls Homerow window count every 100ms: `hs.application.find("Homerow"):allWindows()`
    5. When window count drops (Homerow closed), Hammerspoon:
       a. Posts modifier key-down event: `hs.eventtap.event.newKeyEvent({"shift"}, nil, true):post()`
       b. Posts click event (Homerow already clicked, so maybe not needed?)
       c. Posts modifier key-up event to release
    6. Alternative: Hammerspoon could hold the modifier key down BEFORE activating Homerow, then release after
  - Key insight: Hammerspoon's events go directly to apps, bypassing Karabiner (tested: E→F remap didn't work)
  - Files to modify: ~/.hammerspoon/init.lua, karabiner.edn (shell command trigger)
  - Test command for Homerow window detection: `hs -c 'return #hs.application.find("Homerow"):allWindows()'`
- OSA scripts layer: shortcuts to run common AppleScripts (e.g., close VPN connection success tabs, clear notification center, dismiss dialogs)
- Whispering restart: open/restart Whispering in background without foregrounding
- USB device commands: shortcuts to check/switch input devices (e.g., ensure correct microphone is selected)
- Cmd+Q accessible: need a way to quit applications from a layer
- Copy paste tool: investigate clipboard manager/paste transformation tools
- Clean up karabiner.edn: audit which rules belong in ALL PROFILES vs DESKTOP ONLY sections
- Verify: Shift+equals → tilde still works after shift refactor
- Verify: Left shift alone = (, right shift alone = ) still works (laptop only)
- Verify: Shift+command alone → square brackets still works (laptop only)
- Admin layer: full reset (CLI reload + hardware reset) and document all required permissions (~/.config, etc.)
- LLM transform layer: cut selection → apply LLM transformation → paste result
- Scrolling
- Hammerspoon border indicator bug: borders don't update correctly when screens are resized or when switching between monitors
- Slack/messaging: quick way to message people on Slack or other messaging apps
- Nav layer split: separate navigation within the app (back/forward, tabs) from navigating to other apps (open Chrome, iTerm, etc.)
- Claude co-worker: send messages via text-to-speech to a background Claude session
- H modes (Chrome, VS Code, TMUX): add consistent navigation shortcuts like J, K, comma across all app-specific layers
- Term layer --no-verify: remove from gcmp, git commit -m, and git commit -am 'wip', add to bash aliases instead
- VS Code layer: explore executing commands by name instead of keyboard shortcuts (e.g., via CLI or extension)
- Snippet library: quick access to commonly used text snippets
