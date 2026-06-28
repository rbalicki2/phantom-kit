-- Commands Module
-- Handles all commands from Karabiner and hotkeys
-- Pure functions where possible, side effects go through deps

local deps = require("deps")
local state = require("state")

local M = {}

-- File paths
local paths = {
    layer = "/tmp/karabiner-layer",
    command = "/tmp/karabiner-command",
    scrollDown = "/tmp/hs-scroll-down",
    scrollUp = "/tmp/hs-scroll-up",
    lmodeModifier = "/tmp/karabiner-lmode-modifier",
}

-- Scroll state (local to this module)
local scrollPixels = 30

-- Read current layer from file
function M.readLayerFromFile()
    local content, err = deps.readFile(paths.layer)
    if not content then
        return nil
    end
    return content:gsub("%s+", "")  -- trim whitespace
end

-- Process scroll flag files
function M.checkScrollFlags()
    local scrollingDown = deps.fileExists(paths.scrollDown)
    local scrollingUp = deps.fileExists(paths.scrollUp)

    local currentDir = state.getState().scrollDirection

    if scrollingDown and currentDir ~= "down" then
        M.startScroll("down")
    elseif scrollingUp and currentDir ~= "up" then
        M.startScroll("up")
    elseif not scrollingDown and not scrollingUp and currentDir ~= nil then
        M.stopScroll()
    end
end

-- Start continuous scrolling
function M.startScroll(direction)
    M.stopScroll()  -- Stop any existing scroll

    local pixels = direction == "down" and -scrollPixels or scrollPixels
    state.setScrollDirection(direction)

    -- Immediate first scroll
    deps.scrollEvent({0, pixels}, {}, "pixel")

    -- Continue at 60fps
    local timer = deps.doEvery(0.016, function()
        deps.scrollEvent({0, pixels}, {}, "pixel")
    end)
    state.setResource("scrollTimer", timer)
end

-- Stop scrolling
function M.stopScroll()
    local timer = state.getResource("scrollTimer")
    if timer then
        local ok, err = pcall(function() timer:stop() end)
        state.clearResource("scrollTimer")
    end
    state.setScrollDirection(nil)
end

-- Start hover mode (intercept next click)
function M.startHoverMode()
    M.stopHoverMode()  -- Clean up any existing

    state.setHoverMode(true)

    -- Create eventtap to intercept left click
    local tap = deps.newEventTap({hs.eventtap.event.types.leftMouseDown}, function(event)
        M.stopHoverMode()
        return true  -- block the click
    end)

    if tap then
        tap:start()
        state.setResource("hoverTap", tap)
    end

    -- Timeout after 10 seconds
    local timer = deps.doAfter(10, function()
        M.stopHoverMode()
    end)
    state.setResource("hoverTimer", timer)
end

-- Stop hover mode
function M.stopHoverMode()
    local tap = state.getResource("hoverTap")
    if tap then
        local ok, err = pcall(function() tap:stop() end)
        state.clearResource("hoverTap")
    end

    local timer = state.getResource("hoverTimer")
    if timer then
        local ok, err = pcall(function() timer:stop() end)
        state.clearResource("hoverTimer")
    end

    state.setHoverMode(false)
end

-- Hover and then send enter (for Homerow/warpd)
function M.hoverAndEnter()
    M.startHoverMode()
    deps.doAfter(0.01, function()
        deps.keyStroke({}, "return")
        deps.keyStroke({}, "n")
    end)
end

-- Dismiss Homerow labels
function M.dismissHomerow()
    local app = deps.getApplication("Homerow")
    if app then
        local ok, err = pcall(function()
            if app:isFrontmost() then
                deps.keyStroke({}, "escape", 0, app)
            end
        end)
    end
end

-- Process command file
function M.processCommandFile()
    local content, err = deps.readFile(paths.command)
    if not content or content == "" then
        return
    end

    -- Remove the file
    deps.removeFile(paths.command)

    -- Process each command
    for cmd in content:gmatch("[^\r\n]+") do
        cmd = cmd:gsub("%s+", "")
        if cmd ~= "" then
            M.executeCommand(cmd)
        end
    end
end

-- Execute a single command
function M.executeCommand(cmd)
    -- Handle parameterized commands like lmode_exec:c
    local lmodeKey = cmd:match("^lmode_exec:(.+)$")
    if lmodeKey then
        M.executeLModeKey(lmodeKey)
        return
    end

    local handlers = {
        scroll_stop = M.stopScroll,
        hover_mode_stop = M.stopHoverMode,
        hide_overlay = function()
            state.setOverlay(nil)
        end,
        dismiss_homerow = M.dismissHomerow,
        reload = function()
            if hs then hs.reload() end
        end,
        switcher_next_app = function()
            deps.keyStroke({}, "tab", 0)
        end,
        switcher_prev_app = function()
            deps.keyStroke({"shift"}, "tab", 0)
        end,
        switcher_next_window = function()
            deps.keyStroke({}, "`", 0)
        end,
        switcher_prev_window = function()
            deps.keyStroke({"shift"}, "`", 0)
        end,
    }

    local handler = handlers[cmd]
    if handler then
        local ok, err = pcall(handler)
        if not ok then
            print("[commands] Error executing " .. cmd .. ": " .. tostring(err))
        end
    else
        print("[commands] Unknown command: " .. cmd)
    end
end

-- L-mode key execution
function M.executeLModeKey(key)
    local content = deps.readFile(paths.lmodeModifier)
    local modCode = content and content:gsub("%s+", "") or ""
    deps.removeFile(paths.lmodeModifier)

    local mods = {}
    if modCode:find("C") then table.insert(mods, "cmd") end
    if modCode:find("S") then table.insert(mods, "shift") end
    if modCode:find("T") then table.insert(mods, "ctrl") end
    if modCode:find("O") then table.insert(mods, "alt") end

    deps.doAfter(0, function()
        deps.keyStroke(mods, key)
    end)
end

-- Click helpers
function M.clickLeft()
    local ok, err = pcall(function()
        hs.eventtap.leftClick(hs.mouse.absolutePosition())
    end)
end

function M.clickRight()
    local ok, err = pcall(function()
        local pos = hs.mouse.absolutePosition()
        local event = hs.eventtap.event
        event.newMouseEvent(event.types.rightMouseDown, pos):post()
        event.newMouseEvent(event.types.rightMouseUp, pos):post()
    end)
end

function M.clickDouble()
    local ok, err = pcall(function()
        local pos = hs.mouse.absolutePosition()
        local event = hs.eventtap.event
        event.newMouseEvent(event.types.leftMouseDown, pos):setProperty(event.properties.mouseEventClickState, 2):post()
        event.newMouseEvent(event.types.leftMouseUp, pos):setProperty(event.properties.mouseEventClickState, 2):post()
    end)
end

function M.clickShift()
    local ok, err = pcall(function()
        local pos = hs.mouse.absolutePosition()
        local event = hs.eventtap.event
        event.newMouseEvent(event.types.leftMouseDown, pos, {"shift"}):post()
        event.newMouseEvent(event.types.leftMouseUp, pos, {"shift"}):post()
    end)
end

function M.clickCmd()
    local ok, err = pcall(function()
        local pos = hs.mouse.absolutePosition()
        local event = hs.eventtap.event
        event.newMouseEvent(event.types.leftMouseDown, pos, {"cmd"}):post()
        event.newMouseEvent(event.types.leftMouseUp, pos, {"cmd"}):post()
    end)
end

function M.clickCmdShift()
    local ok, err = pcall(function()
        local pos = hs.mouse.absolutePosition()
        local event = hs.eventtap.event
        event.newMouseEvent(event.types.leftMouseDown, pos, {"cmd", "shift"}):post()
        event.newMouseEvent(event.types.leftMouseUp, pos, {"cmd", "shift"}):post()
    end)
end

-- Label mode: hover-then-click pattern
local function hoverThenClick(clickFn)
    M.stopHoverMode()

    local tap = deps.newEventTap({hs.eventtap.event.types.leftMouseDown}, function(event)
        M.stopHoverMode()
        clickFn()
        return true
    end)

    if tap then
        tap:start()
        state.setResource("hoverTap", tap)
    end

    local timer = deps.doAfter(10, M.stopHoverMode)
    state.setResource("hoverTimer", timer)

    deps.doAfter(0.01, function()
        deps.keyStroke({}, "return")
    end)
end

function M.labelRightClick()
    hoverThenClick(M.clickRight)
end

function M.labelDoubleClick()
    hoverThenClick(M.clickDouble)
end

function M.labelShiftClick()
    hoverThenClick(M.clickShift)
end

function M.labelCmdClick()
    hoverThenClick(M.clickCmd)
end

function M.labelCmdShiftClick()
    hoverThenClick(M.clickCmdShift)
end

function M.focusChromeProfile(profile)
    local chrome = deps.getApplication("Google Chrome")
    if not chrome then return false end

    local ok, result = pcall(function()
        local windows = chrome:allWindows()
        for _, win in ipairs(windows) do
            local title = win:title() or ""
            if profile == "personal" then
                if title:find("%(Personal%)") then
                    win:focus()
                    return true
                end
            else
                if title:find("Robert") and not title:find("%(Personal%)") then
                    win:focus()
                    return true
                end
            end
        end
        return false
    end)

    return ok and result
end

-- Window management
function M.moveToNextScreen()
    local ok, err = pcall(function()
        local win = hs.window.focusedWindow()
        if win then
            local screen = win:screen()
            local nextScreen = screen:next()
            if nextScreen then
                win:moveToScreen(nextScreen)
            end
        end
    end)
end

-- Resize the focused window to a unit rect of its screen (BTT-style tiling).
-- rect is {x, y, w, h} in 0..1 screen fractions.
local function moveFocusedToUnit(rect, label)
    pcall(function()
        local win = hs.window.focusedWindow()
        if not win then return end
        local f = win:screen():frame()
        win:setFrame({
            x = f.x + f.w * rect[1],
            y = f.y + f.h * rect[2],
            w = f.w * rect[3],
            h = f.h * rect[4],
        })
    end)
end

function M.windowMaximize()   moveFocusedToUnit({0, 0, 1,   1},   "max") end
function M.windowLeftHalf()   moveFocusedToUnit({0, 0, 0.5, 1},   "left") end
function M.windowRightHalf()  moveFocusedToUnit({0.5, 0, 0.5, 1}, "right") end
function M.windowBottomHalf() moveFocusedToUnit({0, 0.5, 1, 0.5}, "bottom") end

function M.arrangeDebugWindows()
    local ok, err = pcall(function()
        local screen = hs.screen.mainScreen()
        local frame = screen:frame()
        local splitX = frame.x + (frame.w * 0.70)

        local iterm = hs.application.get("iTerm2") or hs.application.get("iTerm")
        if iterm then
            local win = iterm:mainWindow()
            if win then
                win:setFrame({x = frame.x, y = frame.y, w = splitX - frame.x, h = frame.h})
            end
        end

        local eventViewer = hs.application.get("org.pqrs.Karabiner-EventViewer")
        if eventViewer then
            local win = eventViewer:mainWindow()
            if win then
                win:setFrame({x = splitX, y = frame.y, w = frame.x + frame.w - splitX, h = frame.h})
            end
        end

        hs.application.launchOrFocusByBundleID("org.pqrs.Karabiner-EventViewer")
        hs.timer.doAfter(0.1, function()
            if iterm then iterm:activate() end
        end)
    end)
end

return M
