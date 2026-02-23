# Pending Tasks

If work is interrupted or incomplete, document it here so future sessions can continue:

- [DONE] Unit test infrastructure: BFS test generator and runner, ~9.6k tests covering 36 states, integrated into `npm run sync`.
- [DONE] Fix Kinesis firmware: HK4 (Alt+F20) and Fn+space (now Ctrl+Alt+F18) produce different keycodes. Updated `src/kinesis-layout1.txt` and `tests/inputs.json`. Need to flash keyboard with `npm run kinesis`.
- [DONE] Parallelize BFS in test generator: Test processing within each state uses pmap, file writing uses pmap. BFS state discovery remains sequential (necessary for correctness).
- Push voicemode repo to a remote repository
- URL-aware Chrome shortcuts: different shortcuts based on current URL (GitHub vs Gmail vs Docs), via Tampermonkey or Hammerspoon+AppleScript
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
- Auto-select correct microphone: ensure the right microphone input is always selected (e.g., on wake, on device connect)
- Cmd+Q accessible: need a way to quit applications from a layer
- Copy paste tool: investigate clipboard manager/paste transformation tools
- Document reserved keys: create a map of special global key bindings that are reserved
- Consider making up/down arrow globally reserved (not used as part of any layer) (e.g., Hyper+F20 for Label mode dummy output, Ctrl+Alt+Shift+F19 for Wispr popo, etc.) so they don't get accidentally reused
- Verify: Shift+equals → tilde still works after shift refactor
- Verify: Left shift alone = (, right shift alone = ) still works (laptop only)
- Verify: Shift+command alone → square brackets still works (laptop only)
- Admin layer: full reset (CLI reload + hardware reset) and document all required permissions (~/.config, etc.)
- LLM transform layer: cut selection → apply LLM transformation → paste result
- Hammerspoon border indicator bug: borders don't update correctly when screens are resized or when switching between monitors
- Slack/messaging: quick way to message people on Slack or other messaging apps
- Claude co-worker: send messages via text-to-speech to a background Claude session; also Claude Code shortcuts (send selection to session, paste last response, trigger specific prompts)
- Term layer --no-verify: remove from gcmp, git commit -m, and git commit -am 'wip', add to bash aliases instead
- VS Code layer: explore executing commands by name instead of keyboard shortcuts (e.g., via CLI or extension)
- Snippet library: quick access to commonly used text snippets
- Grid mode drag: integrate warpd's drag mode (v) for drag-and-drop operations without physical mouse
- Git-aware border colors: change Hammerspoon border color based on git status (green=clean, red=uncommitted, yellow=unpushed)
- Project-based layer configs: different shortcut sets for different repos, auto-detect via pwd or .git
- Macro recording: record sequence of layer actions, save as named macro, replay with one key via Hammerspoon
- Window arrangement presets: one key to arrange windows for "coding", "research", "communication" layouts
- Session restore: save all Chrome tabs, iTerm sessions, VS Code windows as a "workspace", restore later
- Dismiss all notifications: shortcut to clear macOS notification center
- Debug logging: log layer changes, key presses, and actions to file for debugging issues
- Test harness VM: set up a VM for safely testing Karabiner/Hammerspoon changes without breaking main system
- App change layer reset: Hammerspoon detects frontmost app change → sends hidden key (e.g., F24) → Karabiner rule catches it in app-specific layers (VSCode layer 4, Chrome layer 3, etc.) and resets to Normal if frontmost app doesn't match the layer. Prevents staying stuck in wrong app layer after Cmd+Tab.
- Fuck Slack command: shortcut to quit/mute/dismiss Slack
- InApp mode: Re-enable AppSwitcher/WindowSwitcher entry with different keys (removed up/down because they conflict with navigation in permission dialogs). AppSwitcher (layer 11) and WindowSwitcher (layer 12) modes still exist, just need new entry keys from InApp mode.
- In-App layer shortcuts to consider adding:
  - Find (Cmd+F) - universal search
  - Save (Cmd+S)
  - Undo/Redo (Cmd+Z / Cmd+Shift+Z)
  - Close window (Cmd+W)
  - Zoom in/out (Cmd+Plus/Minus)
  - Full screen toggle (Cmd+Ctrl+F)
  - Preferences (Cmd+Comma)
  - Print (Cmd+P)
  - Find next/prev (Cmd+G / Cmd+Shift+G)
- Prefer keyboard shortcuts over osascript: when an app is foregrounded, use direct keyboard shortcuts instead of osascript calls (e.g., Chrome "last tab" uses osascript but could use Cmd+9)
- Investigate Ctrl+Y bugs: check if Ctrl+Y behavior is correct across all layers
- Number shifting for laptop mode: restore number shifting behavior for laptop keyboard
- Add Shift+letter rules for all letters in insert mode (currently only j has explicit Shift+j → Shift+j rule)
- Caps lock mode (sub_mode 6): add exit mechanism and ensure Backspace/Delete don't exit caps mode
- Caps lock mode: make command navigation keys work in caps lock mode
- Disable Vimium Chrome extension (conflicts with keyboard layer system)
- Rust macro for Karabiner config: Explore deriving the EDN file (or directly the karabiner.json) from a Rust derive macro. Benefits:
  - Type-safe layer definitions and transitions
  - Compile-time validation of state invariants
  - IDE support (autocomplete, go-to-definition for layer names)
  - Generate both the config AND the visualization/documentation from a single source
  - Could define layers as Rust structs with attributes for keys, transitions, conditions
- Auto-generate RHS slots grid: Create a script that iterates through each key+modifier combination in Ins mode, uses match-rules.bb to find which rule catches it, and interprets the output to build the rhs-slots.md table automatically. Would ensure the documentation stays in sync with the actual config.
- [DONE] Add --no-clobber flag to set-rule.bb: Prevents accidental overwrites. Use when adding NEW rules.
- Admin layer: Fix Ctrl+P (screenshot selection) - currently broken
- Admin layer: Add shortcut to clear VPN connection success notification windows
- VPN shortcuts: Add connect/disconnect shortcuts
- Auto-switch system microphone on device connect/disconnect (see plan below)
- Auto-switch Wispr Flow microphone on device connect/disconnect (see plan below)

## Microphone Automation Plan

### Phase 1: System Microphone (SwitchAudioSource)

**Requirements:**
- Shure MV7 is highest priority - if connected, always use it as system mic
- If MacBook is in clamshell mode, NEVER use built-in mic (fall back to Bose or other)
- Manual toggle shortcut to switch between built-in mic and Bose headphones (when Mac open, Shure not connected)

**Implementation:**
1. Install: `brew install switchaudio-osx`
2. Commands:
   - `SwitchAudioSource -c -t input` - get current input device
   - `SwitchAudioSource -a -t input` - list all input devices
   - `SwitchAudioSource -s "Shure MV7" -t input` - set input device
3. Detect clamshell mode: `ioreg -r -k AppleClamshellState -d 4 | grep AppleClamshellState`
4. Create script: `scripts/actions/select-microphone.sh`
   - Auto-select: Shure if connected, else Bose if clamshell, else last manual choice
   - Manual toggle: cycle between built-in and Bose (only when applicable)
5. Add Karabiner shortcut in Admin layer to trigger manual toggle
6. Auto-trigger via LaunchAgent or Hammerspoon on wake/device connect

### Phase 2: Wispr Flow Microphone

**Challenge:** Wispr Flow uses SHA-256 hashed device IDs (Chromium MediaDevices API)

**Get device ID mapping:**
1. Open Wispr Flow
2. Press Cmd+Option+I (DevTools)
3. Run in console:
   ```javascript
   navigator.mediaDevices.enumerateDevices().then(devices => {
     console.log(devices.filter(d => d.kind === 'audioinput').map(d => ({ label: d.label, id: d.deviceId })));
   });
   ```
4. Save mapping to a config file

**Implementation:**
1. Config file: `~/.config/mic-device-ids.json` with label→hash mapping
2. Script: `scripts/actions/select-wispr-mic.sh`
   - Update `~/Library/Application Support/Wispr Flow/config.json` field `prefs.user.overrideAudioDeviceId`
   - Restart Wispr Flow: `pkill "Wispr Flow" && open -a "Wispr Flow"`
3. Coordinate with system mic script so both stay in sync
- SwiftBar layer file: Audit all layer transitions to ensure they write to /tmp/karabiner-layer so SwiftBar displays the correct layer
- Add query tool to search rules by output key (e.g., find all rules that output 'w')
- Add rule removal functionality to set-rule.bb (e.g., `bb scripts/edit/set-rule.bb src/karabiner.edn R1234 --delete`)
- Validation: detect submode rules that are redundant because they're fully captured by more generic mode rules. Example: if layer=7 has a rule for key X, a rule for layer=7:submode=1 with the same key X and identical output is redundant and should be flagged.
- Consolidate validations for sync speed: validate-rules.bb and validate-extras.bb do multiple passes through rules. Could consolidate into single pass for faster sync.
- Return-to-layer for InApp Nav mode: Add mechanism to return to previous layer from InApp mode (layer 10)
- Sub-mode for Mouse mode: Add sub-mode support for Mouse mode (Grid mode, layer 28)
- Entry to Mouse mode from InApp Nav: Add shortcut in InApp mode (layer 10) to enter Mouse/Grid mode (layer 28)
- Canonical key ordering: Consider other places where key-order.bb could be used (e.g., documentation generation, rule listing output)
- SwiftBar Claude feedback indicator: Add separate SwiftBar plugin to show when any Claude session is waiting for user feedback. Should work across local and remote dev servers. Current overlay is transient; need persistent indicator.
- Extend file-based command system: InApp scroll now uses command file (/tmp/karabiner-command) instead of hs -c. Could extend to other hs -c calls. Currently ~26 calls remain in templates. Low priority since these are not in hot paths.
- Hammerspoon robustness: Consider adding more pcall wrappers, syntax validation on config reload, or exploring typed alternatives (Fennel, etc.)
- Grid mode broken: Shows a 2x2 grid instead of the expected grid. Something may have been misconfigured. Investigate warpd or Hammerspoon grid mode setup.
- Admin mode: Fn+backslash (F20) causes weird/broken state. Investigate what rule matches and what state it leaves.
- Switcher mode: Need alternative approach for holding Cmd during app switching (layers 11-12). The old switcher_cmd_down/up Hammerspoon commands were removed because they posted raw modifier events that corrupted keyboard state.
- InApp Nav Mode numbers: Add Cmd+0 through Cmd+9 for direct tab switching (0-9 keys → Cmd+0 through Cmd+9)
- Number key ordering: Numbers are reversed from natural order in some layer - investigate and fix
- Consolidate tab navigation into InApp Nav Mode: Move tab changing shortcuts from Tmux Mode (layer 5) and Chrome Mode (layer 3) into InApp Nav Mode (layer 10) for consistency
- Zoom in/out in InApp Mode: Add Cmd+Plus/Cmd+Minus zoom shortcuts to InApp Nav Mode (layer 10) - these are universal across most apps
- [DONE] Hammerspoon reliability refactor: Modular architecture implemented with state.lua (single source of truth), deps.lua (DI for testing), ui.lua, commands.lua, tests.lua. Key improvements: centralized state, validation, pcall everywhere, proper cleanup on reload.
- End Dictation mode for Wispr Flow: Need a shortcut to stop dictating and foreground a configurable terminal/tmux pane. Unsolved problems:
  - How to store/read the target config (file? environment var? Hammerspoon global?)
  - How to target a specific tmux pane (by name? by index? by some marker?)
  - Should this be a Karabiner rule or a Hammerspoon command?
- Hammerspoon remaining work:
  - Add more unit tests (edge cases, error conditions)
  - Consider reducing hs.ipc usage (known to cause hangs)
  - Monitor for any remaining stuck state issues
