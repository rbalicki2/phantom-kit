# Pending Tasks

If work is interrupted or incomplete, document it here so future sessions can continue:

- Create mental_model.md: concise source of truth for how layers/modes should behave (karabiner.edn is too verbose to be a good reference). Should document each layer's purpose, entry/exit, and key bindings in a scannable format.
- Create a Tampermonkey setup that exposes functions callable from a layer
- Wifi notifier
- Browser extension: Add current page to extension override for blocking
- Admin layer: restart Hammerspoon, reload Karabiner, clean up this layer, etc.
- Log-Streamer sub-layer: Admin or Term layer keys to tail specific logs (system, browser console, project) into floating overlay (Cmd+up/down/plus/minus for navigation)
- Archive Chrome tabs: shortcut to save all open tabs (to file/bookmarks) and close them
- Deep link replacer: copy URL and view/transform it for other contexts
- Brightness and media controls: layer shortcuts for screen brightness, volume, play/pause, next/prev track
- Caffeine toggle: shortcut to prevent/allow Mac sleep
- OSA scripts layer: shortcuts to run common AppleScripts (e.g., close VPN connection success tabs, clear notification center, dismiss dialogs)
- USB device commands: shortcuts to check/switch input devices (e.g., ensure correct microphone is selected)
- Cmd+Q accessible: need a way to quit applications from a layer
- Copy paste tool: investigate clipboard manager/paste transformation tools
- Clean up karabiner.edn: audit which rules belong in ALL PROFILES vs DESKTOP ONLY sections
- Audit vk_none: search for rules with only variable sets/shell commands and no key output - these need `:vk_none` to work
- Document reserved keys: create a map of special global key bindings that are reserved (e.g., Hyper+F20 for Label mode dummy output, Ctrl+Alt+Shift+F19 for Wispr popo, etc.) so they don't get accidentally reused
- Verify: Shift+equals → tilde still works after shift refactor
- Verify: Left shift alone = (, right shift alone = ) still works (laptop only)
- Verify: Shift+command alone → square brackets still works (laptop only)
- Admin layer: full reset (CLI reload + hardware reset) and document all required permissions (~/.config, etc.)
- LLM transform layer: cut selection → apply LLM transformation → paste result
- Hammerspoon border indicator bug: borders don't update correctly when screens are resized or when switching between monitors
- Slack/messaging: quick way to message people on Slack or other messaging apps
- Claude co-worker: send messages via text-to-speech to a background Claude session
- Term layer --no-verify: remove from gcmp, git commit -m, and git commit -am 'wip', add to bash aliases instead
- VS Code layer: explore executing commands by name instead of keyboard shortcuts (e.g., via CLI or extension)
- Snippet library: quick access to commonly used text snippets
