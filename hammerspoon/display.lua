-- Display Management Module
-- Automatically disables the built-in laptop display whenever an external
-- monitor is connected. This is ONE-DIRECTIONAL: we never re-enable the
-- built-in ourselves -- BetterDisplay / macOS already restores the laptop
-- screen when the external is unplugged, and fighting that would cause flapping.
--
-- Uses BetterDisplay's `connected` feature (Pro) to logically disconnect the
-- built-in display, so the desktop only spans the external monitor(s) -- the
-- same end state you get by toggling "connected" off manually in BetterDisplay.
--
-- Trigger: hs.screen.watcher fires on any display configuration change.
-- The action is computed purely from "is a non-built-in screen present?":
--   external present -> confirm it's actually displaying, then connected=off
--   no external      -> do nothing (let existing behavior restore the built-in)
-- This makes the logic idempotent and loop-safe: disconnecting the built-in
-- changes the screen config and re-fires the watcher, but the computed action
-- is unchanged, so we issue no redundant command.
--
-- DDC-CONFIRM GATE: enumeration (hs.screen.allScreens) only proves macOS sees a
-- display, not that the panel is actually lit (it could be powered off or on the
-- wrong input -- "enumerated but dark"). Before disabling the built-in we poll
-- the external's DDC power-mode control (VCP 0xD6) until it confirms "on". This
-- avoids disabling the laptop screen while the external shows nothing.
--
-- FAIL-SAFE: if DDC never confirms within the timeout, we do NOT disable the
-- built-in. Leaving the user with no visible screen is the one outcome we must
-- never produce, so when in doubt we keep the laptop display on.
--
-- ANY-MONITOR SUPPORT: not every external supports DDC. A monitor with no DDC
-- channel returns "Failed." to the power-mode read; we treat that as "can't
-- verify" and fall back to disabling anyway (the user wants this for any
-- monitor). Only a monitor that answers DDC but reports a non-on power state
-- keeps us waiting -- that genuinely is plugged-in-but-dark.

local deps = require("deps")
local state = require("state")

local M = {}

-- Path to the BetterDisplay binary. We call the in-app binary directly via its
-- `cli` subcommand, so no Homebrew install / symlink is required. (If you later
-- `brew install betterdisplaycli`, you could switch BD_BIN to that wrapper and
-- drop the "cli" first argument.)
local BD_BIN = "/Applications/BetterDisplay.app/Contents/MacOS/BetterDisplay"

-- Names by which macOS / BetterDisplay may report the built-in panel.
-- `hs.screen:name()` typically returns "Built-in Display" or "Color LCD".
local BUILTIN_NAMES = {
    ["Built-in Display"] = true,
    ["Built-in Retina Display"] = true,
    ["Color LCD"] = true,
    ["Liquid Retina Display"] = true,
}

-- The name we pass to BetterDisplay's -name= parameter to target the built-in.
-- (From `BetterDisplay cli get -identifiers`, the built-in's name is
-- "Built-in Display".)
local BUILTIN_BD_NAME = "Built-in Display"

-- Debounce: macOS often emits several rapid screen-change events for a single
-- physical connect/disconnect. Coalesce them so we evaluate once things settle.
local DEBOUNCE_SECONDS = 1.5

-- DDC power-mode VCP code. Per DDC/MCCS, a read of 0xD6 returns the display's
-- power state; 0x01 means "on". Other values (0x04/0x05 = off/standby) or a
-- non-numeric "Failed." mean not-confirmed-on.
local VCP_POWER_MODE = "0xD6"
local POWER_ON_VALUE = "1"

-- DDC-confirm polling: after an external appears, poll its power mode this many
-- times, this many seconds apart, before giving up (fail-safe = don't disable).
-- 10 attempts * 1.0s ~= 10s of grace for the monitor to come up.
local DDC_POLL_ATTEMPTS = 10
local DDC_POLL_INTERVAL = 1.0

-- Tracks whether we have already disabled the built-in for the current
-- external-connected session. Reset to false once no external is present, so
-- the next time an external connects we re-apply the disable. Avoids issuing
-- redundant BetterDisplay commands while an external stays connected.
local disabledForExternal = false

-- Return a list of the names of all external (non-built-in) displays present.
local function externalNames()
    local names = {}
    local screens = deps.allScreens()
    for _, screen in ipairs(screens) do
        local ok, name = pcall(function() return screen:name() end)
        if ok and name and not BUILTIN_NAMES[name] then
            table.insert(names, name)
        end
    end
    return names
end

-- Send the actual `set -connected=off` command to the built-in.
local function sendDisableCommand(reason)
    local args = { "cli", "set", "-name=" .. BUILTIN_BD_NAME, "-connected=off" }
    local task = hs.task.new(BD_BIN, function(exitCode, stdOut, stdErr)
        if exitCode == 0 then
            print("[display] Built-in display disabled (" .. tostring(reason) .. ")")
        else
            -- Reset our flag so the next watcher pass retries.
            disabledForExternal = false
            print(string.format(
                "[display] BetterDisplay disable failed (exit %s): %s %s",
                tostring(exitCode), tostring(stdOut), tostring(stdErr)))
        end
    end, args)
    if task then
        task:start()
    else
        print("[display] Failed to create BetterDisplay task")
    end
end

-- Turn the built-in display OFF, but first check it isn't already off:
-- `set -connected=off` on an already-disconnected display returns "Failed.",
-- which is not a real error -- the goal is already met. So we read the current
-- connected state and only issue the command when the built-in is actually on.
-- Runs asynchronously via hs.task so it never blocks Hammerspoon's main loop.
local function disableBuiltin(reason)
    if not hs or not hs.task then
        print("[display] hs.task unavailable; cannot disable built-in display")
        return
    end

    local getArgs = { "cli", "get", "-name=" .. BUILTIN_BD_NAME, "-connected" }
    local task = hs.task.new(BD_BIN, function(_, stdOut, _)
        local cur = (stdOut or ""):gsub("%s+", "")
        if cur == "off" then
            -- Already in the desired state; nothing to do.
            print("[display] Built-in already off (" .. tostring(reason) .. ")")
            return
        end
        sendDisableCommand(reason)
    end, getArgs)
    if task then
        task:start()
    else
        print("[display] Failed to create BetterDisplay task")
    end
end

-- Async DDC read of an external's power mode. Calls back with a classification:
--   "on"      -> monitor answered and reports powered on (VCP 0xD6 == 1)
--   "nodd c"  -> monitor has no usable DDC channel (read returned "Failed.")
--   "wait"    -> monitor answered but is not (yet) on; keep polling
local function readPowerMode(name, callback)
    if not hs or not hs.task then
        callback("nodc")  -- Can't read; treat as unverifiable -> fall back.
        return
    end
    local args = { "cli", "get", "-name=" .. name, "-ddc", "-vcp=" .. VCP_POWER_MODE }
    local task = hs.task.new(BD_BIN, function(_, stdOut, _)
        local out = (stdOut or ""):gsub("%s+", "")
        if out == POWER_ON_VALUE then
            callback("on")
        elseif out:match("^Failed") then
            callback("nodc")
        else
            callback("wait")
        end
    end, args)
    if task then
        task:start()
    else
        callback("nodc")
    end
end

-- Poll the present external(s) until one confirms it is on (-> disable built-in),
-- or all DDC channels are unusable (-> fall back and disable), or we run out of
-- attempts (-> fail-safe: leave the built-in on).
local function confirmAndDisable(attempt)
    attempt = attempt or 1

    local names = externalNames()
    if #names == 0 then
        -- External vanished mid-poll; abort and let the next event re-evaluate.
        disabledForExternal = false
        return
    end

    -- Probe each external once this round. We resolve the round as soon as we
    -- get a decisive answer, tracking whether every probe came back "nodc".
    local pending = #names
    local allNoDdc = true
    local decided = false

    local function finishRound()
        if decided then return end
        if allNoDdc then
            -- No external offers DDC verification -> can't confirm pixels for
            -- any monitor. Honor "works for any monitor" by disabling anyway.
            decided = true
            disableBuiltin("external present, no DDC to verify")
            return
        end
        -- Some monitor answered DDC but none reported "on" yet. Keep waiting.
        if attempt >= DDC_POLL_ATTEMPTS then
            -- Fail-safe: never disable the built-in if we couldn't confirm.
            disabledForExternal = false
            print(string.format(
                "[display] DDC never confirmed external on after %d attempts; "
                .. "leaving built-in display ON (fail-safe)", DDC_POLL_ATTEMPTS))
            return
        end
        local timer = deps.doAfter(DDC_POLL_INTERVAL, function()
            confirmAndDisable(attempt + 1)
        end)
        state.setResource("displayDdcTimer", timer)
    end

    for _, name in ipairs(names) do
        readPowerMode(name, function(result)
            if decided then return end
            if result == "on" then
                decided = true
                disableBuiltin("external '" .. name .. "' confirmed on via DDC")
                return
            end
            if result ~= "nodc" then
                allNoDdc = false  -- This monitor answered the bus.
            end
            pending = pending - 1
            if pending == 0 then
                finishRound()
            end
        end)
    end
end

-- Redraw the layer borders for the CURRENT screen geometry. The borders are
-- canvases sized to each screen's fullFrame(); ui.drawBorders only re-runs on a
-- layer change, so after a display topology change (e.g. external connected +
-- built-in disabled) the old canvases keep the previous screen's dimensions and
-- look wrong. Re-issue drawBorders so they're re-sized to the new screen(s).
-- Lazy require avoids a load-order dependency between display.lua and ui.lua.
local function redrawBorders()
    local ok, ui = pcall(require, "ui")
    if ok and ui and ui.drawBorders then
        pcall(function() ui.drawBorders(state.getCurrentLayer()) end)
    end
end

-- Evaluate current display topology. One-directional: when an external is
-- present, run the DDC-confirm gate before disabling the built-in; otherwise do
-- nothing (existing behavior re-enables the built-in on unplug).
local function evaluate()
    -- Borders are sized to screen geometry, so redraw them on every topology
    -- change -- independent of whether we end up disabling the built-in.
    redrawBorders()

    local names = externalNames()
    if #names > 0 then
        if not disabledForExternal then
            disabledForExternal = true  -- Claim the session so we gate only once.
            confirmAndDisable(1)
        end
    else
        -- No external: don't touch the built-in. Reset the flag so the next
        -- external connect re-runs the confirm gate. Cancel any pending poll.
        disabledForExternal = false
        local ddcTimer = state.getResource("displayDdcTimer")
        if ddcTimer then
            pcall(function() ddcTimer:stop() end)
            state.clearResource("displayDdcTimer")
        end
    end
end

-- Debounced wrapper around evaluate(), reset on every screen-change event.
local function scheduleEvaluate()
    local existing = state.getResource("displayDebounceTimer")
    if existing then
        pcall(function() existing:stop() end)
    end
    local timer = deps.doAfter(DEBOUNCE_SECONDS, evaluate)
    state.setResource("displayDebounceTimer", timer)
end

-- Initialize: start the screen watcher and do an initial evaluation.
function M.init()
    if not hs or not hs.screen or not hs.screen.watcher then
        print("[display] hs.screen.watcher unavailable; display automation disabled")
        return
    end

    -- Tear down any previous watcher (defensive, e.g. on reload before cleanup).
    M.cleanup()

    local watcher = hs.screen.watcher.new(function()
        scheduleEvaluate()
    end)
    watcher:start()
    state.setResource("displayWatcher", watcher)

    -- Run an initial evaluation so the correct state is applied at load time.
    -- Slight delay lets displays settle after a Hammerspoon reload.
    deps.doAfter(0.5, evaluate)

    print("[display] Display automation initialized")
end

-- Cleanup: stop the watcher and debounce timer. Called before reload.
function M.cleanup()
    local watcher = state.getResource("displayWatcher")
    if watcher then
        pcall(function() watcher:stop() end)
        state.clearResource("displayWatcher")
    end

    local timer = state.getResource("displayDebounceTimer")
    if timer then
        pcall(function() timer:stop() end)
        state.clearResource("displayDebounceTimer")
    end

    local ddcTimer = state.getResource("displayDdcTimer")
    if ddcTimer then
        pcall(function() ddcTimer:stop() end)
        state.clearResource("displayDdcTimer")
    end
end

-- Manual trigger (handy for testing from `hs -c 'require("display").evaluate()'`).
-- Bypasses debounce. Resets the flag so it re-applies the disable if needed.
function M.evaluate()
    disabledForExternal = false
    evaluate()
end

return M
