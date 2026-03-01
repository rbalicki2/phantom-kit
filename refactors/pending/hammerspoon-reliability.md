# Hammerspoon Reliability Improvement Plan

## Current Problems

1. **Frequent crashes/freezes** - "Hammerspoon dies like 20 times a day"
2. **Stale overlay** - When entering git mode, shows previous layer's overlay instead of current
3. **First character cut off in git mode** - Related to timing between focus and typing

## Architecture Overview

### Current Module Structure

```
~/.hammerspoon/
├── init.lua        # Entry point, global function exports
├── state.lua       # State management (layer, overlay, scroll, hover)
├── deps.lua        # Dependency injection wrapper for HS APIs
├── commands.lua    # Command handlers (scroll, click, etc.)
├── ui.lua          # Visual elements (borders, overlays)
└── tests.lua       # Unit tests
```

### State Flow

1. **Karabiner** writes to `/tmp/karabiner-layer` (e.g., "term", "norm", "ins")
2. **Poll timer** (every 100ms) reads the file and compares to current state
3. **State change** triggers UI updates (border color, brief overlay)

### Key Components

#### Poll Timer (init.lua:64-79)
```lua
local pollTimer = deps.doEvery(0.1, function()
    -- Check for layer changes
    local newLayer = commands.readLayerFromFile()
    if newLayer and newLayer ~= "" then
        local currentLayer = state.getCurrentLayer()
        if newLayer ~= currentLayer then
            state.setLayer(newLayer)
        end
    end
    -- Check scroll flags
    commands.checkScrollFlags()
    -- Process command file
    commands.processCommandFile()
end)
```

#### hs.ipc (init.lua:15)
```lua
require("hs.ipc")
```
This is required for Karabiner's `hs -c 'function()'` calls to work.

## Root Cause Analysis

### Problem 1: Frequent Crashes

**Likely causes:**
- `hs.ipc` instability - known to cause hangs
- Timer callbacks throwing errors and not being caught properly
- Canvas objects not being cleaned up (memory leaks)
- JXA/AppleScript calls blocking the main thread

**Evidence:**
- CLAUDE.md explicitly warns: "Note: hs.ipc can cause issues - if you experience hangs, try commenting this out"
- Scripts call `hs -c '...'` which uses hs.ipc

### Problem 2: Stale Overlay

**Root cause:** 100ms poll interval is too slow. When entering git mode:
1. Karabiner rule writes "term" to `/tmp/karabiner-layer`
2. User immediately presses a git command key
3. The type-ins template executes before poll timer reads the new layer
4. Overlay shows old layer because state hasn't updated yet

### Problem 3: First Character Cut Off

**Root cause:** The templates type characters immediately without waiting for focus:
```
:type-ins "osascript -e 'tell app \"System Events\" to key code 8 using control down' -e 'tell app \"System Events\" to keystroke \"%s\"' && echo altins > /tmp/karabiner-layer"
```

When iTerm isn't fully focused, the first keystroke is lost.

## Improvement Plan

### Phase 1: Reduce hs.ipc Dependency (Priority: HIGH)

**Goal:** Minimize `hs -c` calls to reduce crash surface area.

**Current state:**
```bash
# Multiple scripts call hs -c directly
/opt/homebrew/bin/hs -c 'showLayerOverlay()' &
```

**After:**
Use file-based communication instead of hs.ipc for non-critical functions.

```lua
-- commands.lua: Add file-based command processing
local function processCommandFile()
    local content = deps.readFile("/tmp/hammerspoon-command")
    if not content or content == "" then return end
    deps.removeFile("/tmp/hammerspoon-command")

    -- Process commands: "showLayerOverlay", "hideOverlay", etc.
    for cmd in content:gmatch("[^\n]+") do
        executeCommand(cmd)
    end
end
```

**Shell script change:**
```bash
# Before (hs.ipc, can hang)
/opt/homebrew/bin/hs -c 'showLayerOverlay()' &

# After (file-based, reliable)
echo "showLayerOverlay" >> /tmp/hammerspoon-command
```

### Phase 2: Faster Layer Detection (Priority: HIGH)

**Goal:** Reduce overlay staleness by using hs.pathwatcher instead of polling.

**Current state:**
```lua
-- 100ms poll timer
local pollTimer = deps.doEvery(0.1, function()
    local newLayer = commands.readLayerFromFile()
    ...
end)
```

**After:**
```lua
-- React instantly to file changes
local layerWatcher = hs.pathwatcher.new("/tmp/karabiner-layer", function(paths, flags)
    local newLayer = commands.readLayerFromFile()
    if newLayer and newLayer ~= "" and newLayer ~= state.getCurrentLayer() then
        state.setLayer(newLayer)
    end
end):start()

-- Keep poll timer for scroll/command processing, but less frequent
local pollTimer = deps.doEvery(0.2, function()
    commands.checkScrollFlags()
    commands.processCommandFile()
end)
```

### Phase 3: Improved Logging (Priority: MEDIUM)

**Goal:** Make logs always available for debugging crashes.

**Current state:** Logs only visible in Hammerspoon console.

**After:**

```lua
-- Add to init.lua
local logFile = io.open("/tmp/hammerspoon.log", "a")

local function log(level, msg)
    local timestamp = os.date("%Y-%m-%d %H:%M:%S")
    local line = string.format("[%s] [%s] %s\n", timestamp, level, msg)

    -- Write to file
    if logFile then
        logFile:write(line)
        logFile:flush()  -- Ensure it's written even if crash
    end

    -- Also print to console
    print(line)
end

-- Wrapper for crash-safe logging
local function safeLog(level, msg)
    pcall(function()
        log(level, msg)
    end)
end

-- Use throughout code
safeLog("INFO", "[init] Initializing Hammerspoon...")
safeLog("WARN", "[state] Unknown layer code: " .. tostring(layer))
safeLog("ERROR", "[deps] Timer callback error: " .. tostring(err))
```

**Log rotation:**
```lua
-- Add to init()
local function rotateLogs()
    local stat = hs.fs.attributes("/tmp/hammerspoon.log")
    if stat and stat.size > 1000000 then  -- 1MB
        os.rename("/tmp/hammerspoon.log", "/tmp/hammerspoon.log.old")
        logFile = io.open("/tmp/hammerspoon.log", "a")
    end
end
```

**Usage:**
```bash
# Watch logs in real-time
tail -f /tmp/hammerspoon.log

# Check recent logs after freeze
tail -100 /tmp/hammerspoon.log
```

### Phase 4: Add Delay to Templates (Priority: MEDIUM)

**Goal:** Fix first character cut-off in git mode.

**Current state:**
```
:type-ins "osascript -e 'tell app \"System Events\" to key code 8 using control down' -e 'tell app \"System Events\" to keystroke \"%s\"' && echo altins > /tmp/karabiner-layer"
```

**After:**
```
:type-ins "osascript -e 'delay 0.03' -e 'tell app \"System Events\" to key code 8 using control down' -e 'tell app \"System Events\" to keystroke \"%s\"' && echo altins > /tmp/karabiner-layer"
```

The 30ms delay allows iTerm to fully receive focus before keystrokes are sent.

### Phase 5: Watchdog Timer (Priority: LOW)

**Goal:** Auto-recover from frozen state.

```lua
-- Add to init.lua
local lastHeartbeat = os.time()

-- Update heartbeat in poll timer
local pollTimer = deps.doEvery(0.2, function()
    lastHeartbeat = os.time()
    -- ... existing code ...
end)

-- Watchdog that checks if poll timer is alive
local watchdog = deps.doEvery(5, function()
    if os.time() - lastHeartbeat > 3 then
        safeLog("ERROR", "Poll timer appears stuck, reloading...")
        hs.reload()
    end
end)
```

## Implementation Order

1. **Phase 3: Logging** - Do this first so we can debug future issues
2. **Phase 1: Reduce hs.ipc** - Eliminate crash source
3. **Phase 2: Pathwatcher** - Fix stale overlay
4. **Phase 4: Template delay** - Fix first character cut-off
5. **Phase 5: Watchdog** - Safety net

## Verification

After each phase, test:

1. **Stability test:** Use keyboard normally for 1 hour, count crashes
2. **Layer switch test:** Enter git mode 10 times, verify overlay shows immediately
3. **Typing test:** In git mode, type commands 10 times, count cut-off characters

## Files to Modify

- `~/.hammerspoon/init.lua` - Main changes
- `~/.hammerspoon/commands.lua` - Add command file processing
- `/Users/rbalicki/code/voicemode/src/karabiner.edn` - Update templates
- `/Users/rbalicki/code/voicemode/scripts/actions/*.sh` - Replace hs -c calls

## Quick Debug Commands

```bash
# Check if Hammerspoon is running
pgrep -x Hammerspoon

# Check recent logs (after implementing Phase 3)
tail -50 /tmp/hammerspoon.log

# Check current layer
cat /tmp/karabiner-layer

# Test hs.ipc is working
timeout 2 /opt/homebrew/bin/hs -c 'return "ok"' && echo "IPC working" || echo "IPC hung!"

# Force reload Hammerspoon
timeout 2 /opt/homebrew/bin/hs -c 'hs.reload()' || killall Hammerspoon && open -a Hammerspoon
```
