-- Dependency Injection Module
-- Wraps all Hammerspoon APIs for testability
-- In production: uses real hs APIs
-- In tests: can be replaced with mocks

local M = {}

-- Default to real Hammerspoon APIs (if available)
local deps = {
    -- Canvas/UI
    canvas = hs and hs.canvas,
    screen = hs and hs.screen,
    drawing = hs and hs.drawing,
    styledtext = hs and hs.styledtext,
    alert = hs and hs.alert,

    -- Events
    eventtap = hs and hs.eventtap,
    mouse = hs and hs.mouse,

    -- Timers
    timer = hs and hs.timer,

    -- Applications
    application = hs and hs.application,
    osascript = hs and hs.osascript,

    -- Window management
    window = hs and hs.window,

    -- File I/O (wrapped for testability)
    io = io,
    os = os,
}

-- Get a dependency
function M.get(name)
    return deps[name]
end

-- Set a dependency (for testing)
function M.set(name, impl)
    deps[name] = impl
end

-- Reset to real dependencies
function M.reset()
    if hs then
        deps.canvas = hs.canvas
        deps.screen = hs.screen
        deps.drawing = hs.drawing
        deps.styledtext = hs.styledtext
        deps.alert = hs.alert
        deps.eventtap = hs.eventtap
        deps.mouse = hs.mouse
        deps.timer = hs.timer
        deps.application = hs.application
        deps.osascript = hs.osascript
        deps.window = hs.window
    end
    deps.io = io
    deps.os = os
end

-- Safe wrappers that handle errors gracefully

-- Safe file read
function M.readFile(path)
    local f = deps.io.open(path, "r")
    if not f then
        return nil, "Could not open file: " .. path
    end
    local content = f:read("*all")
    f:close()
    return content
end

-- Safe file write
function M.writeFile(path, content)
    local f = deps.io.open(path, "w")
    if not f then
        return false, "Could not open file for writing: " .. path
    end
    f:write(content)
    f:close()
    return true
end

-- Safe file exists check
function M.fileExists(path)
    local f = deps.io.open(path, "r")
    if f then
        f:close()
        return true
    end
    return false
end

-- Safe file remove
function M.removeFile(path)
    local ok, err = pcall(function()
        deps.os.remove(path)
    end)
    return ok, err
end

-- Safe timer creation with automatic tracking
function M.doAfter(seconds, callback)
    if not deps.timer then
        return nil, "Timer not available"
    end
    return deps.timer.doAfter(seconds, function()
        local ok, err = pcall(callback)
        if not ok then
            print("[deps] Timer callback error: " .. tostring(err))
        end
    end)
end

function M.doEvery(seconds, callback)
    if not deps.timer then
        return nil, "Timer not available"
    end
    return deps.timer.doEvery(seconds, function()
        local ok, err = pcall(callback)
        if not ok then
            print("[deps] Timer callback error: " .. tostring(err))
        end
    end)
end

-- Safe canvas operations
function M.newCanvas(frame)
    if not deps.canvas then
        return nil, "Canvas not available"
    end
    local ok, result = pcall(function()
        return deps.canvas.new(frame)
    end)
    if ok then
        return result
    else
        return nil, result
    end
end

-- Safe screen operations
function M.mainScreen()
    if not deps.screen then
        return nil, "Screen not available"
    end
    local ok, result = pcall(function()
        return deps.screen.mainScreen()
    end)
    if ok then
        return result
    else
        return nil, result
    end
end

function M.allScreens()
    if not deps.screen then
        return {}, "Screen not available"
    end
    local ok, result = pcall(function()
        return deps.screen.allScreens()
    end)
    if ok then
        return result
    else
        return {}, result
    end
end

-- Safe eventtap operations
function M.newEventTap(types, callback)
    if not deps.eventtap then
        return nil, "Eventtap not available"
    end
    local ok, result = pcall(function()
        return deps.eventtap.new(types, function(event)
            local cbOk, cbResult = pcall(callback, event)
            if cbOk then
                return cbResult
            else
                print("[deps] Eventtap callback error: " .. tostring(cbResult))
                return false
            end
        end)
    end)
    if ok then
        return result
    else
        return nil, result
    end
end

function M.keyStroke(mods, key, delay, app)
    if not deps.eventtap then
        return false, "Eventtap not available"
    end
    local ok, err = pcall(function()
        deps.eventtap.keyStroke(mods, key, delay or 0, app)
    end)
    return ok, err
end

function M.scrollEvent(offsets, mods, unit)
    if not deps.eventtap then
        return false, "Eventtap not available"
    end
    local ok, err = pcall(function()
        deps.eventtap.event.newScrollEvent(offsets, mods or {}, unit or "pixel"):post()
    end)
    return ok, err
end

-- Safe application operations
function M.getApplication(name)
    if not deps.application then
        return nil
    end
    local ok, result = pcall(function()
        return deps.application.get(name)
    end)
    if ok then
        return result
    else
        return nil
    end
end

function M.launchOrFocus(name)
    if not deps.application then
        return false
    end
    local ok, err = pcall(function()
        deps.application.launchOrFocus(name)
    end)
    return ok, err
end

-- Safe osascript
function M.applescript(script)
    if not deps.osascript then
        return false, nil, "osascript not available"
    end
    local ok, success, output, rawOutput = pcall(function()
        return deps.osascript.applescript(script)
    end)
    if ok then
        return success, output, rawOutput
    else
        return false, nil, success  -- success contains error message
    end
end

function M.javascript(script)
    if not deps.osascript then
        return false, nil, "osascript not available"
    end
    local ok, success, output, rawOutput = pcall(function()
        return deps.osascript.javascript(script)
    end)
    if ok then
        return success, output, rawOutput
    else
        return false, nil, success
    end
end

return M
