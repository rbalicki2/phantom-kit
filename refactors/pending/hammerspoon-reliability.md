# Hammerspoon Reliability Refactor

## Goal

Replace unreliable `hs.ipc` with Unix domain sockets and add type-safe message passing using discriminated unions (ADTs).

## Current Problems

1. **Frequent crashes/freezes** - `hs.ipc` hangs, blocking Karabiner shell commands
2. **Stringly-typed commands** - No validation, typos silently fail
3. **Scattered state** - Hard to reason about what states are valid

## Architecture

### Communication: Unix Domain Sockets

```
Karabiner Rule → Shell Command → Socket → Hammerspoon
                 echo '{"t":"scroll","stop":true}' | nc -U /tmp/hs.sock
```

**Why sockets over files:**
- Instant delivery (no polling delay)
- No file cleanup needed
- Proper message queueing
- Connection-oriented (know if Hammerspoon is alive)

**Why sockets over hs.ipc:**
- Simple protocol (JSON over Unix socket)
- Shell-native (`nc` or `socat`)
- No IPC module complexity/instability

### Type Safety: Discriminated Unions in Lua

Lua lacks native ADTs, but we can encode them as tables with a discriminant field and strict validation.

#### Message Schema

All messages are JSON objects with a `t` (type) field as discriminant:

```typescript
// TypeScript notation for documentation
type Message =
  | { t: "scroll", action: "start", dir: "up" | "down" }
  | { t: "scroll", action: "stop" }
  | { t: "click", variant: "left" | "right" | "double" | "shift" | "cmd" | "cmd_shift" }
  | { t: "label_click", variant: "right" | "double" | "shift" | "cmd" | "cmd_shift" }
  | { t: "overlay", action: "show", mode: "layer" | "permanent" }
  | { t: "overlay", action: "hide" }
  | { t: "overlay", action: "toggle" }
  | { t: "hover", action: "start" | "stop" | "enter" }
  | { t: "homerow", action: "dismiss" }
  | { t: "lmode", key: string }
  | { t: "window", action: "next_screen" | "arrange_debug" }
  | { t: "chrome", action: "tab", index: number }
  | { t: "chrome", action: "last_tab" }
  | { t: "chrome", action: "focus_profile", profile: "personal" | "work" }
  | { t: "switcher", action: "next_app" | "prev_app" | "next_window" | "prev_window" }
  | { t: "system", action: "reload" }
```

#### Lua Validation Module

```lua
-- schema.lua: Message validation with exhaustive pattern matching

local M = {}

-- Enum definitions (closed sets of valid values)
M.ScrollDir = { up = "up", down = "down" }
M.ClickVariant = { left = "left", right = "right", double = "double", shift = "shift", cmd = "cmd", cmd_shift = "cmd_shift" }
M.LabelClickVariant = { right = "right", double = "double", shift = "shift", cmd = "cmd", cmd_shift = "cmd_shift" }
M.OverlayMode = { layer = "layer", permanent = "permanent" }
M.OverlayAction = { show = "show", hide = "hide", toggle = "toggle" }
M.HoverAction = { start = "start", stop = "stop", enter = "enter" }
M.WindowAction = { next_screen = "next_screen", arrange_debug = "arrange_debug" }
M.ChromeProfile = { personal = "personal", work = "work" }
M.SwitcherAction = { next_app = "next_app", prev_app = "prev_app", next_window = "next_window", prev_window = "prev_window" }

-- Helper: assert value is in enum
local function assertEnum(value, enum, name)
    if not enum[value] then
        local valid = {}
        for k in pairs(enum) do table.insert(valid, k) end
        error(string.format("Invalid %s: %q. Valid: %s", name, tostring(value), table.concat(valid, ", ")))
    end
    return value
end

-- Helper: assert field exists and has type
local function assertField(msg, field, expectedType)
    local value = msg[field]
    if value == nil then
        error(string.format("Missing required field: %s", field))
    end
    if type(value) ~= expectedType then
        error(string.format("Field %s must be %s, got %s", field, expectedType, type(value)))
    end
    return value
end

-- Validate and normalize a message (returns validated copy or throws)
function M.validate(msg)
    if type(msg) ~= "table" then
        error("Message must be a table, got " .. type(msg))
    end

    local t = assertField(msg, "t", "string")

    -- Exhaustive match on message type
    if t == "scroll" then
        local action = assertField(msg, "action", "string")
        if action == "start" then
            return { t = "scroll", action = "start", dir = assertEnum(msg.dir, M.ScrollDir, "dir") }
        elseif action == "stop" then
            return { t = "scroll", action = "stop" }
        else
            error("scroll action must be 'start' or 'stop', got: " .. action)
        end

    elseif t == "click" then
        return { t = "click", variant = assertEnum(assertField(msg, "variant", "string"), M.ClickVariant, "variant") }

    elseif t == "label_click" then
        return { t = "label_click", variant = assertEnum(assertField(msg, "variant", "string"), M.LabelClickVariant, "variant") }

    elseif t == "overlay" then
        local action = assertField(msg, "action", "string")
        if action == "show" then
            return { t = "overlay", action = "show", mode = assertEnum(assertField(msg, "mode", "string"), M.OverlayMode, "mode") }
        elseif action == "hide" then
            return { t = "overlay", action = "hide" }
        elseif action == "toggle" then
            return { t = "overlay", action = "toggle" }
        else
            error("overlay action must be 'show', 'hide', or 'toggle', got: " .. action)
        end

    elseif t == "hover" then
        return { t = "hover", action = assertEnum(assertField(msg, "action", "string"), M.HoverAction, "action") }

    elseif t == "homerow" then
        local action = assertField(msg, "action", "string")
        if action ~= "dismiss" then
            error("homerow action must be 'dismiss', got: " .. action)
        end
        return { t = "homerow", action = "dismiss" }

    elseif t == "lmode" then
        local key = assertField(msg, "key", "string")
        if #key ~= 1 then
            error("lmode key must be single character, got: " .. key)
        end
        return { t = "lmode", key = key }

    elseif t == "window" then
        return { t = "window", action = assertEnum(assertField(msg, "action", "string"), M.WindowAction, "action") }

    elseif t == "chrome" then
        local action = assertField(msg, "action", "string")
        if action == "tab" then
            local index = assertField(msg, "index", "number")
            if index < 1 or index ~= math.floor(index) then
                error("chrome tab index must be positive integer, got: " .. index)
            end
            return { t = "chrome", action = "tab", index = index }
        elseif action == "last_tab" then
            return { t = "chrome", action = "last_tab" }
        elseif action == "focus_profile" then
            return { t = "chrome", action = "focus_profile", profile = assertEnum(assertField(msg, "profile", "string"), M.ChromeProfile, "profile") }
        else
            error("chrome action must be 'tab', 'last_tab', or 'focus_profile', got: " .. action)
        end

    elseif t == "switcher" then
        return { t = "switcher", action = assertEnum(assertField(msg, "action", "string"), M.SwitcherAction, "action") }

    elseif t == "system" then
        local action = assertField(msg, "action", "string")
        if action ~= "reload" then
            error("system action must be 'reload', got: " .. action)
        end
        return { t = "system", action = "reload" }

    else
        error("Unknown message type: " .. t)
    end
end

return M
```

#### Dispatch Module

```lua
-- dispatch.lua: Execute validated messages (pure pattern match)

local commands = require("commands")
local state = require("state")

local M = {}

-- Dispatch a validated message to its handler
-- This function assumes msg has already been validated by schema.validate()
function M.dispatch(msg)
    local t = msg.t

    if t == "scroll" then
        if msg.action == "start" then
            commands.startScroll(msg.dir)
        else
            commands.stopScroll()
        end

    elseif t == "click" then
        local handlers = {
            left = commands.clickLeft,
            right = commands.clickRight,
            double = commands.clickDouble,
            shift = commands.clickShift,
            cmd = commands.clickCmd,
            cmd_shift = commands.clickCmdShift,
        }
        handlers[msg.variant]()

    elseif t == "label_click" then
        local handlers = {
            right = commands.labelRightClick,
            double = commands.labelDoubleClick,
            shift = commands.labelShiftClick,
            cmd = commands.labelCmdClick,
            cmd_shift = commands.labelCmdShiftClick,
        }
        handlers[msg.variant]()

    elseif t == "overlay" then
        if msg.action == "show" then
            state.setOverlay(msg.mode)
        elseif msg.action == "hide" then
            state.setOverlay(nil)
        else -- toggle
            if state.getOverlayType() then
                state.setOverlay(nil)
            else
                state.setOverlay("layer")
            end
        end

    elseif t == "hover" then
        if msg.action == "start" then
            commands.startHoverMode()
        elseif msg.action == "stop" then
            commands.stopHoverMode()
        else -- enter
            commands.hoverAndEnter()
        end

    elseif t == "homerow" then
        commands.dismissHomerow()

    elseif t == "lmode" then
        commands.executeLModeKey(msg.key)

    elseif t == "window" then
        if msg.action == "next_screen" then
            commands.moveToNextScreen()
        else
            commands.arrangeDebugWindows()
        end

    elseif t == "chrome" then
        if msg.action == "tab" then
            commands.chromeTab(msg.index)
        elseif msg.action == "last_tab" then
            commands.chromeLastTab()
        else
            commands.focusChromeProfile(msg.profile)
        end

    elseif t == "switcher" then
        local deps = require("deps")
        local handlers = {
            next_app = function() deps.keyStroke({}, "tab", 0) end,
            prev_app = function() deps.keyStroke({"shift"}, "tab", 0) end,
            next_window = function() deps.keyStroke({}, "`", 0) end,
            prev_window = function() deps.keyStroke({"shift"}, "`", 0) end,
        }
        handlers[msg.action]()

    elseif t == "system" then
        if hs then hs.reload() end
    end
end

return M
```

### Socket Server

```lua
-- socket.lua: Unix domain socket server

local json = require("hs.json")
local schema = require("schema")
local dispatch = require("dispatch")

local M = {}

local SOCKET_PATH = "/tmp/hs.sock"
local server = nil

local function log(level, msg)
    local timestamp = os.date("%H:%M:%S")
    print(string.format("[%s] [socket] [%s] %s", timestamp, level, msg))
end

local function handleMessage(data)
    -- Parse JSON
    local ok, msg = pcall(json.decode, data)
    if not ok then
        log("ERROR", "Invalid JSON: " .. tostring(msg))
        return
    end

    -- Validate against schema
    local ok2, validated = pcall(schema.validate, msg)
    if not ok2 then
        log("ERROR", "Validation failed: " .. tostring(validated))
        return
    end

    -- Dispatch to handler
    local ok3, err = pcall(dispatch.dispatch, validated)
    if not ok3 then
        log("ERROR", "Dispatch failed: " .. tostring(err))
        return
    end

    log("INFO", "Handled: " .. data:gsub("%s+", " "))
end

function M.start()
    -- Remove stale socket file
    os.remove(SOCKET_PATH)

    server = hs.socket.server.new(function(data)
        -- Handle each line as a separate message (for nc compatibility)
        for line in data:gmatch("[^\r\n]+") do
            if line ~= "" then
                handleMessage(line)
            end
        end
    end)

    if server then
        server:listen(SOCKET_PATH)
        log("INFO", "Listening on " .. SOCKET_PATH)
    else
        log("ERROR", "Failed to create socket server")
    end
end

function M.stop()
    if server then
        server:disconnect()
        server = nil
    end
    os.remove(SOCKET_PATH)
end

return M
```

### Shell Helper

Create a shell function for sending messages:

```bash
# scripts/lib/hs-send.sh
# Usage: hs_send '{"t":"scroll","action":"stop"}'

hs_send() {
    echo "$1" | nc -U /tmp/hs.sock 2>/dev/null || true
}
```

Or inline in Karabiner rules:

```bash
echo '{"t":"overlay","action":"toggle"}' | nc -U /tmp/hs.sock
```

## Migration Plan

### Phase 1: Add Socket Server (Non-Breaking)

1. Create `schema.lua`, `dispatch.lua`, `socket.lua`
2. Add `socket.start()` to `init.lua`
3. Test with manual `nc` commands
4. Keep all existing `hs -c` calls working

**Verification:**
```bash
# Test socket is listening
echo '{"t":"overlay","action":"toggle"}' | nc -U /tmp/hs.sock

# Check Hammerspoon console for logs
```

### Phase 2: Migrate High-Frequency Commands

Replace `hs -c` calls that happen often (overlay, scroll):

| Old | New |
|-----|-----|
| `hs -c 'toggleLayerOverlay()'` | `echo '{"t":"overlay","action":"toggle"}' \| nc -U /tmp/hs.sock` |
| `hs -c 'showLayerOverlay()'` | `echo '{"t":"overlay","action":"show","mode":"layer"}' \| nc -U /tmp/hs.sock` |
| `hs -c 'hideOverlay()'` | `echo '{"t":"overlay","action":"hide"}' \| nc -U /tmp/hs.sock` |
| `hs -c 'scrollStop()'` | `echo '{"t":"scroll","action":"stop"}' \| nc -U /tmp/hs.sock` |

### Phase 3: Migrate Click Commands

```bash
# Old
hs -c 'labelRightClick()'

# New
echo '{"t":"label_click","variant":"right"}' | nc -U /tmp/hs.sock
```

### Phase 4: Migrate L-Mode

```bash
# Old
hs -c "executeLModeKey('a')"

# New
echo '{"t":"lmode","key":"a"}' | nc -U /tmp/hs.sock
```

### Phase 5: Remove hs.ipc

Once all commands are migrated:

1. Remove `require("hs.ipc")` from init.lua
2. Remove global function exports
3. Remove file-based command processing (superseded by socket)

## State Machine Refactor (Future)

The current state management in `state.lua` could also benefit from ADT treatment:

```lua
-- Current: scattered variables
local currentLayer = "norm"
local overlayType = nil
local scrollDirection = nil

-- Better: single state ADT
local State = {
    layer = "norm",  -- LayerName enum
    overlay = nil,   -- nil | {type: "layer"} | {type: "permanent"}
    scroll = nil,    -- nil | {dir: "up" | "down", timer: Timer}
    hover = nil,     -- nil | {tap: EventTap, timer: Timer}
}
```

This ensures:
- Can't have scroll timer without direction
- Can't have hover tap without timeout timer
- Layer is always a valid layer name

## Files to Create/Modify

**New files:**
- `~/.hammerspoon/schema.lua` - Message validation
- `~/.hammerspoon/dispatch.lua` - Message dispatch
- `~/.hammerspoon/socket.lua` - Socket server

**Modify:**
- `~/.hammerspoon/init.lua` - Start socket server, eventually remove hs.ipc
- `src/karabiner.edn` - Replace `hs -c` with socket sends
- `scripts/actions/*.sh` - Replace `hs -c` with socket sends

## Testing

```bash
# Test each message type
echo '{"t":"scroll","action":"start","dir":"down"}' | nc -U /tmp/hs.sock
echo '{"t":"scroll","action":"stop"}' | nc -U /tmp/hs.sock
echo '{"t":"click","variant":"left"}' | nc -U /tmp/hs.sock
echo '{"t":"overlay","action":"toggle"}' | nc -U /tmp/hs.sock

# Test validation errors (should log but not crash)
echo '{"t":"scroll"}' | nc -U /tmp/hs.sock  # missing action
echo '{"t":"scroll","action":"start"}' | nc -U /tmp/hs.sock  # missing dir
echo '{"t":"unknown"}' | nc -U /tmp/hs.sock  # unknown type
echo 'not json' | nc -U /tmp/hs.sock  # parse error
```

## Benefits

1. **No more freezes** - Socket is simple, no IPC complexity
2. **Type safety** - Invalid messages caught immediately with clear errors
3. **Exhaustive matching** - Adding new message type requires updating all switches
4. **Debuggable** - Every message logged with timestamp
5. **Testable** - Schema validation is pure functions, easy to unit test
