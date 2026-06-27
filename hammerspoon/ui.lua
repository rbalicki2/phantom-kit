-- UI Module
-- Handles all visual elements: borders, overlays
-- Purely reactive: renders based on state, no internal state

local deps = require("deps")
local state = require("state")

local M = {}

-- Configuration
local config = {
    borderThickness = 2,
    bottomBorderThickness = 5,
    indicatorAlpha = 1.0,
    overlayPadding = 24,
    overlayMaxWidthRatio = 0.8,
    overlayMaxHeightRatio = 0.8,
    overlayMargin = 20,
    briefFontSize = 64,
    layerFontSize = 28,
    briefDuration = 1,
    layerDuration = 20,
}

-- Colors
local colors = {
    green = { red = 0, green = 0.5, blue = 0 },
    yellow = { red = 0.6, green = 0.5, blue = 0 },
    purple = { red = 0.4, green = 0.1, blue = 0.5 },
    red = { red = 0.7, green = 0.1, blue = 0.1 },
    blue = { red = 0.1, green = 0.3, blue = 0.7 },
    cyan = { red = 0, green = 0.5, blue = 0.5 },
}

-- Layer to color mapping
local layerColors = {
    norm = colors.yellow,
    ins = colors.green,
    altins = colors.green,
    ["2h"] = colors.green,
    n = colors.red,
    label = colors.cyan,
    grid = colors.cyan,
    inapp = colors.blue,
    -- Everything else falls back to purple
}

-- Layer display names
local layerDisplayNames = {
    norm = "\u{1F7E2}",      -- green circle
    ins = "\u{270D}\u{FE0F}", -- writing hand
    altins = "\u{2328}\u{FE0F}", -- keyboard
    ["2h"] = "\u{1F91A}\u{1F91A}", -- two hands
    n = "\u{1F6B2}",         -- bicycle
    i = "\u{1FA9F}",         -- window
    comma = "COMMA",
    l = "L",
    lC = "L-Cmd",
    lTC = "L-Ctrl+Cmd",
    lT = "L-Ctrl",
    lTO = "L-Ctrl+Alt",
    lO = "L-Alt",
    lOC = "L-Alt+Cmd",
    lCTO = "L-Hyper",
    lentry = "L\u{23CE}",    -- L + return symbol
    lactive = "L\u{26A1}",   -- L + lightning
    term = "GIT",
    tmux = "TMUX",
    chrome = "CHROME",
    vscode = "VSCODE",
    label = "\u{1F42D}",     -- mouse
    grid = "\u{1F42D}\u{25A6}", -- mouse + grid
    inapp = "\u{1F4F1}",     -- phone
}

-- Layer files mapping
local layerFiles = {
    norm = "norm.txt",
    ins = "ins.txt",
    altins = "altins.txt",
    ["2h"] = "2h.txt",
    n = "nav.txt",
    i = "i.txt",
    comma = "comma.txt",
    l = "l.txt",
    lC = "l.txt",
    lTC = "l.txt",
    lT = "l.txt",
    lTO = "l.txt",
    lO = "l.txt",
    lOC = "l.txt",
    lCTO = "l.txt",
    lentry = "lmode.txt",
    lactive = "lmode.txt",
    term = "git.txt",
    tmux = "tmux.txt",
    chrome = "chrome.txt",
    vscode = "vscode.txt",
    label = "label.txt",
    grid = "grid.txt",
    inapp = "inapp.txt",
}

local layersPath = "/Users/rbalicki/code/voicemode/src/layers/"

-- Get color for layer (with fallback)
function M.getLayerColor(layer)
    return layerColors[layer] or colors.purple
end

-- Get display name for layer
function M.getDisplayName(layer)
    return layerDisplayNames[layer] or (layer and layer:upper()) or "?"
end

-- Get layer content from file
function M.getLayerContent(layer)
    local filename = layerFiles[layer]
    if not filename then
        return "No layer active\n\nPress right_control + N/M/H/U/J to enter a layer"
    end

    local content, err = deps.readFile(layersPath .. filename)
    if not content then
        return "Layer file not found: " .. filename
    end
    return content
end

-- Clear all border canvases
function M.clearBorders()
    local borders = state.getResource("borders") or {}
    for _, border in ipairs(borders) do
        if border then
            local ok, err = pcall(function() border:delete() end)
            if not ok then
                print("[ui] Error deleting border: " .. tostring(err))
            end
        end
    end
    state.setResource("borders", {})
end

-- Draw borders on all screens
function M.drawBorders(layer)
    M.clearBorders()

    local color = M.getLayerColor(layer)
    if not color then return end

    color = { red = color.red, green = color.green, blue = color.blue, alpha = config.indicatorAlpha }

    local borders = {}
    local screens = deps.allScreens()

    for _, screen in ipairs(screens) do
        local ok, result = pcall(function()
            local frame = screen:fullFrame()
            local border = deps.newCanvas({
                x = frame.x,
                y = frame.y,
                w = frame.w,
                h = frame.h
            })

            if not border then return nil end

            border:appendElements({
                -- Top border
                {
                    type = "rectangle",
                    action = "fill",
                    fillColor = color,
                    frame = { x = 0, y = 0, w = frame.w, h = config.borderThickness },
                },
                -- Left border
                {
                    type = "rectangle",
                    action = "fill",
                    fillColor = color,
                    frame = { x = 0, y = 0, w = config.borderThickness, h = frame.h },
                },
                -- Right border
                {
                    type = "rectangle",
                    action = "fill",
                    fillColor = color,
                    frame = { x = frame.w - config.borderThickness, y = 0, w = config.borderThickness, h = frame.h },
                },
                -- Bottom border (thicker)
                {
                    type = "rectangle",
                    action = "fill",
                    fillColor = color,
                    frame = { x = 0, y = frame.h - config.bottomBorderThickness, w = frame.w, h = config.bottomBorderThickness },
                },
            })
            border:level(hs.canvas.windowLevels.overlay)
            border:behavior(hs.canvas.windowBehaviors.canJoinAllSpaces)
            border:clickActivating(false)
            border:canvasMouseEvents(false)
            border:show()
            return border
        end)

        if ok and result then
            table.insert(borders, result)
        elseif not ok then
            print("[ui] Error creating border: " .. tostring(result))
        end
    end

    state.setResource("borders", borders)
end

-- Clear overlay canvas
function M.clearOverlay()
    local canvas = state.getResource("overlayCanvas")
    if canvas then
        local ok, err = pcall(function() canvas:delete() end)
        if not ok then
            print("[ui] Error deleting overlay: " .. tostring(err))
        end
        state.clearResource("overlayCanvas")
    end

    local timer = state.getResource("overlayTimer")
    if timer then
        local ok, err = pcall(function() timer:stop() end)
        state.clearResource("overlayTimer")
    end
end

-- Clear brief overlay canvas
function M.clearBrief()
    local canvas = state.getResource("briefCanvas")
    if canvas then
        local ok, err = pcall(function() canvas:delete() end)
        if not ok then
            print("[ui] Error deleting brief overlay: " .. tostring(err))
        end
        state.clearResource("briefCanvas")
    end

    local timer = state.getResource("briefTimer")
    if timer then
        local ok, err = pcall(function() timer:stop() end)
        state.clearResource("briefTimer")
    end
end

-- Show brief layer name (upper right, auto-hide)
function M.showBrief(layer)
    M.clearBrief()

    local displayName = M.getDisplayName(layer)
    local screen = deps.mainScreen()
    if not screen then return end

    local ok, err = pcall(function()
        local frame = screen:frame()

        local styledText = hs.styledtext.new(displayName, {
            font = { name = "Menlo-Bold", size = config.briefFontSize },
            color = { white = 1, alpha = 1 },
        })

        local textSize = hs.drawing.getTextDrawingSize(styledText)
        local padding = config.overlayPadding
        local width = textSize.w + padding * 2
        local height = textSize.h + padding * 2

        local margin = config.overlayMargin
        local x = frame.x + frame.w - width - margin
        local y = frame.y + margin

        local canvas = deps.newCanvas({ x = x, y = y, w = width, h = height })
        if not canvas then return end

        canvas:appendElements({
            {
                type = "rectangle",
                fillColor = { black = 0, alpha = 0.8 },
                roundedRectRadii = { xRadius = 8, yRadius = 8 },
            },
            {
                type = "text",
                text = styledText,
                frame = { x = padding, y = padding, w = width - padding * 2, h = height - padding * 2 },
            },
        })
        canvas:level(hs.canvas.windowLevels.overlay)
        canvas:show()

        state.setResource("briefCanvas", canvas)

        local timer = deps.doAfter(config.briefDuration, function()
            M.clearBrief()
            state.setOverlay(nil)
        end)
        state.setResource("briefTimer", timer)
    end)

    if not ok then
        print("[ui] Error showing brief: " .. tostring(err))
    end
end

-- Show layer overlay (right side, content from file)
function M.showLayerOverlay(permanent)
    M.clearOverlay()

    local layer = state.getCurrentLayer()
    local content = M.getLayerContent(layer)
    local screen = deps.mainScreen()
    if not screen then return end

    local ok, err = pcall(function()
        local frame = screen:frame()

        local styledText = hs.styledtext.new(content, {
            font = { name = "Menlo", size = config.layerFontSize },
            color = { white = 1, alpha = 1 },
            paragraphStyle = { lineSpacing = 4 },
        })

        local textSize = hs.drawing.getTextDrawingSize(styledText)
        local padding = config.overlayPadding
        local width = math.min(textSize.w + padding * 2, frame.w * config.overlayMaxWidthRatio)
        local height = math.min(textSize.h + padding * 2, frame.h * config.overlayMaxHeightRatio)

        local margin = config.overlayMargin
        local x = frame.x + frame.w - width - margin
        local y = frame.y + (frame.h - height) / 2

        local canvas = deps.newCanvas({ x = x, y = y, w = width, h = height })
        if not canvas then return end

        canvas:appendElements({
            {
                type = "rectangle",
                fillColor = { black = 0, alpha = 0.85 },
                roundedRectRadii = { xRadius = 10, yRadius = 10 },
            },
            {
                type = "text",
                text = styledText,
                frame = { x = padding, y = padding, w = width - padding * 2, h = height - padding * 2 },
            },
        })
        canvas:level(hs.canvas.windowLevels.overlay)
        canvas:show()

        state.setResource("overlayCanvas", canvas)

        if not permanent then
            local timer = deps.doAfter(config.layerDuration, function()
                M.clearOverlay()
                state.setOverlay(nil)
            end)
            state.setResource("overlayTimer", timer)
        end
    end)

    if not ok then
        print("[ui] Error showing layer overlay: " .. tostring(err))
    end
end

-- Clean up all UI resources
function M.cleanup()
    M.clearBorders()
    M.clearOverlay()
    M.clearBrief()
end

-- Initialize: subscribe to state changes
function M.init()
    -- React to layer changes
    state.addListener("layer", function(data)
        M.drawBorders(data.new)
        M.showBrief(data.new)
        state.setOverlay("brief")
    end)

    -- React to overlay changes
    state.addListener("overlay", function(data)
        if data.new == nil then
            M.clearOverlay()
            M.clearBrief()
        elseif data.new == "layer" then
            M.showLayerOverlay(false)
        elseif data.new == "permanent" then
            M.showLayerOverlay(true)
        elseif data.new == "brief" then
            -- Brief is already shown by layer change handler
        end
    end)
end

return M
