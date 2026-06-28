-- Hammerspoon Configuration
-- Modular architecture with proper state management
--
-- Modules:
--   state.lua    - Single source of truth for all state
--   deps.lua     - Dependency injection for testability
--   ui.lua       - Visual elements (borders, overlays)
--   commands.lua - Command handlers
--   tests.lua    - Unit tests
--
-- Run tests: hs -c 'require("tests").runAll()'

-- Enable CLI access (required for Karabiner shell commands)
-- Note: hs.ipc can cause issues - if you experience hangs, try commenting this out
require("hs.ipc")

-- Load modules
local state = require("state")
local deps = require("deps")
local ui = require("ui")
local commands = require("commands")
local display = require("display")

-- Global cleanup function (call before reload)
local function cleanup()
    print("[init] Running cleanup...")

    -- Stop poll timer
    local pollTimer = state.getResource("pollTimer")
    if pollTimer then
        pcall(function() pollTimer:stop() end)
    end

    -- Stop scroll timer
    commands.stopScroll()

    -- Stop hover mode
    commands.stopHoverMode()

    -- Clean up UI resources
    ui.cleanup()

    -- Stop display watcher
    display.cleanup()

    print("[init] Cleanup complete")
end

-- Initialize modules
local function init()
    print("[init] Initializing Hammerspoon...")

    -- Initialize UI (sets up listeners)
    ui.init()

    -- Read initial layer and set up state
    local initialLayer = commands.readLayerFromFile()
    if initialLayer and initialLayer ~= "" then
        state.setLayer(initialLayer)
    else
        state.setLayer("norm")
    end

    -- Draw initial borders
    ui.drawBorders(state.getCurrentLayer())

    -- Set up poll timer for layer changes and commands
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
    state.setResource("pollTimer", pollTimer)

    -- Start display automation (disable built-in when external is connected)
    display.init()

    print("[init] Initialization complete")
end

-- Export global functions for Karabiner hs -c calls
-- These maintain backward compatibility with existing rules

function scroll(pixels)
    deps.scrollEvent({0, pixels}, {}, "pixel")
end

function scrollStart(pixels)
    if pixels < 0 then
        commands.startScroll("down")
    else
        commands.startScroll("up")
    end
end

function scrollStop()
    commands.stopScroll()
end

function hoverModeStart()
    commands.startHoverMode()
end

function hoverModeStop()
    commands.stopHoverMode()
end

function hoverAndEnter()
    commands.hoverAndEnter()
end

function dismissHomerow()
    commands.dismissHomerow()
end

function clickLeft()
    commands.clickLeft()
end

function clickRight()
    commands.clickRight()
end

function clickDouble()
    commands.clickDouble()
end

function clickShift()
    commands.clickShift()
end

function clickCmd()
    commands.clickCmd()
end

function clickCmdShift()
    commands.clickCmdShift()
end

function labelRightClick()
    commands.labelRightClick()
end

function labelDoubleClick()
    commands.labelDoubleClick()
end

function labelShiftClick()
    commands.labelShiftClick()
end

function labelCmdClick()
    commands.labelCmdClick()
end

function labelCmdShiftClick()
    commands.labelCmdShiftClick()
end

function executeLModeKey(key)
    commands.executeLModeKey(key)
end

function moveToNextScreen()
    commands.moveToNextScreen()
end

function arrangeDebugWindows()
    commands.arrangeDebugWindows()
end

function focusChromeProfile(profile)
    return commands.focusChromeProfile(profile)
end

function switcherNextApp()
    deps.keyStroke({}, "tab", 0)
end

function switcherPrevApp()
    deps.keyStroke({"shift"}, "tab", 0)
end

function switcherNextWindow()
    deps.keyStroke({}, "`", 0)
end

function switcherPrevWindow()
    deps.keyStroke({"shift"}, "`", 0)
end

-- Overlay functions
function showLayerOverlay()
    state.setOverlay("layer")
end

function showPermanentLayerOverlay()
    state.setOverlay("permanent")
end

function hideOverlay()
    state.setOverlay(nil)
end

function toggleLayerOverlay()
    if state.getOverlayType() then
        state.setOverlay(nil)
    else
        state.setOverlay("layer")
    end
end

-- Legacy compatibility
function getCurrentLayer()
    return state.getCurrentLayer()
end

-- Hotkey: Cmd+Ctrl+Alt+O triggers Homerow
hs.hotkey.bind({"cmd", "ctrl", "alt"}, "O", function()
    hs.application.launchOrFocus("Homerow")
    hs.osascript.applescript([[
        tell application "Homerow" to search
    ]])
end)

-- BTT replacement: window tiling. The keys are caught by Karabiner on the
-- built-in keyboard (Fn+Shift+Up/Left/Right) -- fn-flagged arrows can't be bound
-- via hs.hotkey here -- which writes window_maximize/left_half/right_half to
-- /tmp/karabiner-command; the handlers live in commands.lua.

-- Run cleanup before reload
hs.shutdownCallback = cleanup

-- Run initialization
init()

-- Show alert
hs.alert.show("Hammerspoon loaded")
