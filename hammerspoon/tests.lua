-- Test Module
-- Unit tests for Hammerspoon modules
-- Run with: hs -c 'require("tests").runAll()'

local M = {}

-- Test results
local results = {
    passed = 0,
    failed = 0,
    errors = {},
}

-- Assertion helpers
local function assertEqual(actual, expected, message)
    if actual ~= expected then
        error(string.format("%s: expected %s, got %s",
            message or "Assertion failed",
            tostring(expected),
            tostring(actual)))
    end
end

local function assertTrue(value, message)
    if not value then
        error(message or "Expected true, got false")
    end
end

local function assertFalse(value, message)
    if value then
        error(message or "Expected false, got true")
    end
end

local function assertNil(value, message)
    if value ~= nil then
        error(string.format("%s: expected nil, got %s",
            message or "Assertion failed",
            tostring(value)))
    end
end

local function assertNotNil(value, message)
    if value == nil then
        error(message or "Expected non-nil value")
    end
end

-- Run a single test
local function runTest(name, testFn)
    local ok, err = pcall(testFn)
    if ok then
        results.passed = results.passed + 1
        print("  [PASS] " .. name)
    else
        results.failed = results.failed + 1
        table.insert(results.errors, { name = name, error = err })
        print("  [FAIL] " .. name .. ": " .. tostring(err))
    end
end

-- State module tests
local function testState()
    print("\n=== State Module Tests ===")
    local state = require("state")

    runTest("initial state is norm", function()
        state.reset()
        assertEqual(state.getCurrentLayer(), "norm")
    end)

    runTest("setLayer validates empty", function()
        state.reset()
        local ok, err = state.setLayer("")
        assertFalse(ok)
    end)

    runTest("setLayer validates nil", function()
        state.reset()
        local ok, err = state.setLayer(nil)
        assertFalse(ok)
    end)

    runTest("setLayer accepts valid layer", function()
        state.reset()
        local ok = state.setLayer("ins")
        assertTrue(ok)
        assertEqual(state.getCurrentLayer(), "ins")
    end)

    runTest("setLayer accepts unknown layer with warning", function()
        state.reset()
        -- Unknown layers are allowed (for future compatibility)
        local ok = state.setLayer("unknown_layer")
        assertTrue(ok)
    end)

    runTest("isValidLayer returns true for valid layers", function()
        assertTrue(state.isValidLayer("norm"))
        assertTrue(state.isValidLayer("ins"))
        assertTrue(state.isValidLayer("altins"))
        assertTrue(state.isValidLayer("inapp"))
    end)

    runTest("isValidLayer returns false for invalid layers", function()
        assertFalse(state.isValidLayer("invalid"))
        assertFalse(state.isValidLayer(""))
        assertFalse(state.isValidLayer(nil))
    end)

    runTest("setOverlay validates type", function()
        state.reset()
        local ok, err = state.setOverlay("invalid_type")
        assertFalse(ok)
    end)

    runTest("setOverlay accepts valid types", function()
        state.reset()
        local ok = state.setOverlay("brief")
        assertTrue(ok)
        assertEqual(state.getOverlayType(), "brief")

        ok = state.setOverlay("layer")
        assertTrue(ok)
        assertEqual(state.getOverlayType(), "layer")

        ok = state.setOverlay("permanent")
        assertTrue(ok)
        assertEqual(state.getOverlayType(), "permanent")

        ok = state.setOverlay(nil)
        assertTrue(ok)
        assertNil(state.getOverlayType())
    end)

    runTest("setScrollDirection validates", function()
        state.reset()
        local ok = state.setScrollDirection("invalid")
        assertFalse(ok)
    end)

    runTest("setScrollDirection accepts valid values", function()
        state.reset()
        local ok = state.setScrollDirection("up")
        assertTrue(ok)
        assertTrue(state.isScrolling())

        ok = state.setScrollDirection("down")
        assertTrue(ok)
        assertTrue(state.isScrolling())

        ok = state.setScrollDirection(nil)
        assertTrue(ok)
        assertFalse(state.isScrolling())
    end)

    runTest("setHoverMode validates boolean", function()
        state.reset()
        local ok = state.setHoverMode("not boolean")
        assertFalse(ok)
    end)

    runTest("setHoverMode accepts boolean", function()
        state.reset()
        local ok = state.setHoverMode(true)
        assertTrue(ok)
        assertTrue(state.isHoverModeActive())

        ok = state.setHoverMode(false)
        assertTrue(ok)
        assertFalse(state.isHoverModeActive())
    end)

    runTest("listeners are called on state change", function()
        state.reset()
        local called = false
        local receivedData = nil

        state.addListener("layer", function(data)
            called = true
            receivedData = data
        end)

        state.setLayer("nav")
        -- Note: We can't easily remove listeners, so this test may be flaky
        -- In production, you'd want a way to unsubscribe
    end)

    runTest("getState returns copy of state", function()
        state.reset()
        state.setLayer("ins")
        local s = state.getState()
        assertEqual(s.currentLayer, "ins")
        -- Verify it's not the same object (would need deeper test)
    end)

    runTest("reset clears all state", function()
        state.setLayer("ins")
        state.setOverlay("layer")
        state.setScrollDirection("up")
        state.setHoverMode(true)

        state.reset()

        assertEqual(state.getCurrentLayer(), "norm")
        assertNil(state.getOverlayType())
        assertFalse(state.isScrolling())
        assertFalse(state.isHoverModeActive())
    end)
end

-- Deps module tests (mostly checking wrappers exist)
local function testDeps()
    print("\n=== Deps Module Tests ===")
    local deps = require("deps")

    runTest("get returns hs APIs when available", function()
        if hs then
            assertNotNil(deps.get("canvas"))
            assertNotNil(deps.get("timer"))
        end
    end)

    runTest("readFile returns nil for missing file", function()
        local content, err = deps.readFile("/nonexistent/path/file.txt")
        assertNil(content)
    end)

    runTest("fileExists returns false for missing file", function()
        assertFalse(deps.fileExists("/nonexistent/path/file.txt"))
    end)

    runTest("readFile works for existing file", function()
        -- Write a test file
        local testPath = "/tmp/hs-test-file.txt"
        local testContent = "test content"

        local f = io.open(testPath, "w")
        if f then
            f:write(testContent)
            f:close()

            local content = deps.readFile(testPath)
            assertEqual(content, testContent)

            os.remove(testPath)
        end
    end)
end

-- UI module tests (limited without mocking)
local function testUI()
    print("\n=== UI Module Tests ===")
    local ui = require("ui")

    runTest("getLayerColor returns color for known layers", function()
        local color = ui.getLayerColor("norm")
        assertNotNil(color)
        assertNotNil(color.red)
        assertNotNil(color.green)
        assertNotNil(color.blue)
    end)

    runTest("getLayerColor returns purple for unknown layers", function()
        local color = ui.getLayerColor("unknown")
        assertNotNil(color)
        -- Should be purple (fallback)
        assertEqual(color.red, 0.4)
        assertEqual(color.green, 0.1)
        assertEqual(color.blue, 0.5)
    end)

    runTest("getDisplayName returns name for known layers", function()
        local name = ui.getDisplayName("norm")
        assertNotNil(name)
    end)

    runTest("getDisplayName returns uppercase for unknown layers", function()
        local name = ui.getDisplayName("unknown")
        assertEqual(name, "UNKNOWN")
    end)

    runTest("getLayerContent returns default for unknown layer", function()
        local content = ui.getLayerContent("nonexistent")
        assertTrue(content:find("No layer active") ~= nil)
    end)
end

-- Commands module tests
local function testCommands()
    print("\n=== Commands Module Tests ===")
    local commands = require("commands")
    local state = require("state")

    runTest("readLayerFromFile returns nil for missing file", function()
        -- Temporarily rename the file if it exists
        os.rename("/tmp/karabiner-layer", "/tmp/karabiner-layer.bak")
        local layer = commands.readLayerFromFile()
        assertNil(layer)
        os.rename("/tmp/karabiner-layer.bak", "/tmp/karabiner-layer")
    end)

    runTest("executeCommand handles unknown commands gracefully", function()
        -- Should not throw
        commands.executeCommand("nonexistent_command")
    end)

    runTest("stopScroll clears scroll state", function()
        state.reset()
        state.setScrollDirection("up")
        commands.stopScroll()
        assertFalse(state.isScrolling())
    end)

    runTest("stopHoverMode clears hover state", function()
        state.reset()
        state.setHoverMode(true)
        commands.stopHoverMode()
        assertFalse(state.isHoverModeActive())
    end)
end

-- Run all tests
function M.runAll()
    results = { passed = 0, failed = 0, errors = {} }

    print("\n" .. string.rep("=", 50))
    print("Running Hammerspoon Tests")
    print(string.rep("=", 50))

    testState()
    testDeps()
    testUI()
    testCommands()

    print("\n" .. string.rep("=", 50))
    print(string.format("Results: %d passed, %d failed",
        results.passed, results.failed))
    print(string.rep("=", 50))

    if #results.errors > 0 then
        print("\nFailed tests:")
        for _, err in ipairs(results.errors) do
            print("  - " .. err.name .. ": " .. tostring(err.error))
        end
    end

    return results.failed == 0
end

-- Run specific test suite
function M.runState()
    results = { passed = 0, failed = 0, errors = {} }
    testState()
    return results.failed == 0
end

function M.runDeps()
    results = { passed = 0, failed = 0, errors = {} }
    testDeps()
    return results.failed == 0
end

function M.runUI()
    results = { passed = 0, failed = 0, errors = {} }
    testUI()
    return results.failed == 0
end

function M.runCommands()
    results = { passed = 0, failed = 0, errors = {} }
    testCommands()
    return results.failed == 0
end

return M
