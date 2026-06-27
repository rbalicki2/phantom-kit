-- State Management Module
-- Single source of truth for all Hammerspoon state
-- Enforces valid state transitions and makes impossible states unrepresentable

local M = {}

-- Valid layer codes (from /tmp/karabiner-layer)
M.VALID_LAYERS = {
    "norm", "ins", "altins", "2h", "n", "i", "comma", "l",
    "lC", "lTC", "lT", "lTO", "lO", "lOC", "lCTO",
    "lentry", "lactive", "term", "tmux", "chrome", "vscode",
    "label", "grid", "inapp"
}

-- Create a set for O(1) lookup
local validLayerSet = {}
for _, layer in ipairs(M.VALID_LAYERS) do
    validLayerSet[layer] = true
end

-- State schema with defaults
-- All state lives here - no other globals
local state = {
    -- Layer state
    currentLayer = "norm",

    -- Overlay state (mutually exclusive: at most one can be active)
    overlayType = nil,  -- nil | "brief" | "layer" | "permanent"

    -- Scroll state
    scrollDirection = nil,  -- nil | "up" | "down"

    -- Hover mode state
    hoverModeActive = false,

    -- Resource handles (for cleanup)
    resources = {
        borders = {},       -- canvas objects
        overlayCanvas = nil,
        briefCanvas = nil,
        overlayTimer = nil,
        briefTimer = nil,
        scrollTimer = nil,
        hoverTimer = nil,
        hoverTap = nil,
        pollTimer = nil,
    },
}

-- Listeners for state changes (Observer pattern)
local listeners = {
    layer = {},
    overlay = {},
    scroll = {},
    hover = {},
}

-- Validate layer code
function M.isValidLayer(layer)
    return validLayerSet[layer] == true
end

-- Get current state (read-only copy)
function M.getState()
    return {
        currentLayer = state.currentLayer,
        overlayType = state.overlayType,
        scrollDirection = state.scrollDirection,
        hoverModeActive = state.hoverModeActive,
    }
end

-- Get specific state value
function M.getCurrentLayer()
    return state.currentLayer
end

function M.getOverlayType()
    return state.overlayType
end

function M.isScrolling()
    return state.scrollDirection ~= nil
end

function M.isHoverModeActive()
    return state.hoverModeActive
end

-- State setters with validation
function M.setLayer(layer)
    if not layer or layer == "" then
        return false, "Layer cannot be empty"
    end

    if not M.isValidLayer(layer) then
        -- Log unknown layer but don't fail - might be a new layer
        print("[state] Warning: Unknown layer code: " .. tostring(layer))
    end

    local oldLayer = state.currentLayer
    if oldLayer == layer then
        return true, "No change"
    end

    state.currentLayer = layer
    M.notifyListeners("layer", { old = oldLayer, new = layer })
    return true
end

function M.setOverlay(overlayType)
    -- Valid values: nil, "brief", "layer", "permanent"
    local validTypes = { brief = true, layer = true, permanent = true }
    if overlayType ~= nil and not validTypes[overlayType] then
        return false, "Invalid overlay type: " .. tostring(overlayType)
    end

    local oldType = state.overlayType
    if oldType == overlayType then
        return true, "No change"
    end

    state.overlayType = overlayType
    M.notifyListeners("overlay", { old = oldType, new = overlayType })
    return true
end

function M.setScrollDirection(direction)
    -- Valid values: nil, "up", "down"
    local validDirs = { up = true, down = true }
    if direction ~= nil and not validDirs[direction] then
        return false, "Invalid scroll direction: " .. tostring(direction)
    end

    local oldDir = state.scrollDirection
    if oldDir == direction then
        return true, "No change"
    end

    state.scrollDirection = direction
    M.notifyListeners("scroll", { old = oldDir, new = direction })
    return true
end

function M.setHoverMode(active)
    if type(active) ~= "boolean" then
        return false, "Hover mode must be boolean"
    end

    local oldActive = state.hoverModeActive
    if oldActive == active then
        return true, "No change"
    end

    state.hoverModeActive = active
    M.notifyListeners("hover", { old = oldActive, new = active })
    return true
end

-- Resource management
-- Resources are stored directly by name (not nested)
function M.setResource(name, value)
    state.resources[name] = value
end

function M.getResource(name)
    return state.resources[name]
end

function M.clearResource(name)
    state.resources[name] = nil
end

function M.getResources()
    return state.resources
end

-- Listener management
function M.addListener(event, callback)
    if listeners[event] then
        table.insert(listeners[event], callback)
    end
end

function M.notifyListeners(event, data)
    if not listeners[event] then return end
    for _, callback in ipairs(listeners[event]) do
        local ok, err = pcall(callback, data)
        if not ok then
            print("[state] Listener error for " .. event .. ": " .. tostring(err))
        end
    end
end

-- Reset all state (for testing or cleanup)
function M.reset()
    state.currentLayer = "norm"
    state.overlayType = nil
    state.scrollDirection = nil
    state.hoverModeActive = false
    -- Note: resources should be cleaned up by the cleanup module before reset
end

-- Debug: dump state
function M.dump()
    return string.format(
        "State: layer=%s overlay=%s scroll=%s hover=%s",
        state.currentLayer,
        tostring(state.overlayType),
        tostring(state.scrollDirection),
        tostring(state.hoverModeActive)
    )
end

return M
