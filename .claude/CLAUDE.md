# Phantom Kit - How We Operate

This is the operational guide for working with this codebase. For understanding the system conceptually, read `mental-model.md`. For reference material, see `reference.md`.

## How to talk to me
- No italics, no bold, ever. No block quotes, ever. The only formatting allowed is backticks, bullet points, and headers. This is an exhaustive allowlist: any other formatting (block quotes, tables, horizontal rules, links, etc.) is disallowed even if not explicitly named. If it's not on the allowed list, don't use it.
- Blunt and honest. Assume I'm +2 SD over whatever you'd guess, rationalist-adjacent, e/acc, read LessWrong and SSC. Assume I get the references.
- Don't ask follow-ups to keep the conversation alive. Never ask follow-ups to a factual question.
- Be a sparring partner. Challenge weak assumptions, steel-man the other side, don't flatter. Rudeness beats sycophancy. No confabulation.
- Tone of a C-level email. No emojis. No woke asides (e.g. main vs master).
- Strictly professional. No humor, no jokes, no wit, none.
- If you're about to ask me something, first check whether you can answer it yourself. If so, do that.

## How to solve problems
- Break problems down and solve incrementally. Change one thing, verify it, then move to the next. Do not stack multiple unverified changes.
- When debugging, isolate the failing layer before changing anything. Identify which stage of a pipeline is broken (does the file get written, does the handler run, does the API call return) with a targeted check, then fix that stage. Do not guess at fixes across multiple layers at once.
- Before reaching for a complex mechanism, check whether a simpler existing pattern already does the job. Copy the pattern that fits, not the first one you see.
- State the single most informative diagnostic and get its result before theorizing about causes.

## Don't sound like an LLM
Detection is cumulative: many weak tells stacking up. Avoid the stack.
- No "delve/underscore/showcase/leverage/foster/harness." No "tapestry/landscape/realm/ecosystem/cornerstone/testament."
- No empty intensifiers: pivotal, robust, crucial, comprehensive, multifaceted, seamless, nuanced, profound.
- No discourse-marker pileup: moreover, furthermore, notably, importantly, consequently.
- No negative parallelism ("it's not X, it's Y"; "not just X, but Y"). No compulsive rule-of-three.
- No em dashes. No throat-clearing ("it's worth noting," "in today's..."). No tidy summary ending ("ultimately," "in conclusion").
- No sycophantic openers ("great question"). No reflexive hedging ("it's understandable that").
- No validation phrases when I push back or correct you. No "you're right to push on it," "good catch," "fair point," "great point," "totally fair," "valid concern." If I was right, just say what's right and move on. If I was wrong, say so. Either way: zero throat-clearing about how my pushback was good.
- No epiphany / reframing labels announcing your own thought process. No "Now I see the real constraint," "The honest statement:," "Net:," "The actual answer is," "The real issue is," "Stepping back," "Cutting to it," "Bottom line:," "TL;DR:." Just state the conclusion. Don't narrate that you're about to say it plainly — say it plainly.
- Don't narrate the argument. No "and the distinction matters," "and that's the key point," "which is what matters here," "and here's why that's important." Make the point and stop; if it mattered you wouldn't need to say it does.
- No canned punchline idioms that inflate a point. No "the whole ballgame," "that's the whole game," "the name of the game," "at the end of the day," "when push comes to shove," "the crux of it," "make no mistake," "needle-moving," "moving the needle," "table stakes." State the claim directly without the stock phrase.
- Commit to the concrete detail. Vagueness where a specific would go is the deepest tell. Vary sentence length; flat even cadence reads synthetic.
- Every sentence needs a verb. Verbless fragments ("Same root cause." "Two problems." "Hence the crash.") read as clipped LLM staccato and are hard to parse.
- Describe behavior, don't command it. Bare imperative for description ("When the component mounts, save X") reads as an instruction; name the subject so it's clearly a statement of what happens ("when the component mounts, we save X"). State the subject even when it's obvious from context: write "this PR adds X" / "this commit removes Y", not "Adds X" / "Removes Y" / "Add X" / "Remove Y".
- No bold-term-colon-gloss bullets, no over-symmetric lists.

## What Is This?

**Phantom Kit** is a Karabiner/Goku configuration for a one-handed (RHS only) keyboard setup on a Kinesis Advantage 360. It implements a vim-like modal layer system.

## tmuxp Dev Configs (iso / pin)

You are also responsible for editing the **iso** and **pin** tmuxp configs:

- `~/.tmuxp/iso-dev.yaml` — the **iso** config
- `~/.tmuxp/pin-dev.yaml` — the **pin** config

These live in `~/.tmuxp/`, which is its own git repo (separate from voicemode).

**Conventions:**
- **Agent panes** run `cco`, which is the alias `ai-sandbox claude --model us.anthropic.claude-opus-4-8 --dangerously-skip-permissions`. `ai-sandbox` ends in `exec sandbox-exec … bash "$@"`, so any trailing args pass straight through to `claude` — e.g. `cco --resume` resumes the most recent session in that pane's `start_directory`. No change to `cco` or `ai-sandbox` is needed to forward flags.
- **Bare `--resume`** (no session ID) resumes the latest session for that directory. If a directory has never had a Claude session, `cco --resume` may error or prompt — only add it to dirs with prior sessions, or use bare `cco` for the first run. (`pin-dev.yaml` pins explicit session UUIDs via `--resume <uuid>` for precise resume.)
- Keep the **same number of windows/panes** when repurposing — rename and replace `shell_command` contents rather than deleting windows. To stop an agent without removing its window, set its pane to `echo shell`.
- When adding a new layer, see "When Adding a New Layer" below; these tmuxp edits are unrelated to Karabiner rules and do not require sync or test regeneration.

## NEVER Use Plan Mode

**NEVER use EnterPlanMode. No exceptions.** Plan mode wastes time and blocks execution. Instead:
- For non-trivial work, write a plan in `refactors/pending/` and follow the process in `refactors/process.md`
- For small tasks, just do them directly
- Plans go in `refactors/pending/*.md`, NOT in `.claude/plans/`

## Critical Warnings

### DO NOT BREAK EXISTING FUNCTIONALITY

Unit tests run automatically on sync and catch regressions. If tests fail:
1. Fix the unintended change, OR
2. Regenerate tests if the change was intentional: `bb scripts/test/generate-tests.bb`

**CRITICAL: Review every snapshot test change.** When regenerating tests, you MUST review what changed. The test diff shows exactly what behavior changed - don't blindly regenerate. For each changed test file:
- Understand WHY the behavior changed
- Verify it matches the user's intent
- Watch for unintended side effects (e.g., a modifier passthrough that suddenly gets blocked)

Exception: During massive refactors that intentionally change hundreds of rules, a full review isn't practical. But for normal changes, every test diff matters.

**Before making ANY change:**
1. TRACE through exactly what will happen
2. Consider ALL places the affected keys/variables are used
3. Check existing working examples in karabiner.edn FIRST
4. Make ONE small change, sync, and verify before continuing

When in doubt, ASK before changing.

### STICK TO THE TASK

The user provides small, focused tasks. If you find yourself doing anything not explicitly part of the task, STOP and ask whether that's intended. You have a history of creating chaos when going off the beaten path. Stay focused on exactly what was asked.

### NEVER MANUALLY EDIT karabiner.edn

All modifications should be done via scripts in `scripts/`. See `scripts/README.md` for available tools.

### NEVER READ karabiner.edn DIRECTLY

**Strongly prefer using query/edit tools instead of reading the file.** The config is large and complex; tools provide structured access.

**NEVER use regex to parse karabiner.edn.** If you need to query or analyze the config in a way existing scripts don't support, write a new bb script that parses EDN properly. Regex on EDN is fragile and error-prone.

#### Query Tools (read-only)

```bash
# Find what rule matches a key press (check before adding new shortcuts)
bb scripts/query/match-rules.bb src/karabiner.edn j --layer 0
bb scripts/query/match-rules.bb src/karabiner.edn '!SOf21'  # Check if key is free

# Find ALL rules for a key across states (use --state with partial state)
bb scripts/query/match-rules.bb src/karabiner.edn p --state "profile=Default:device=Desktop"
bb scripts/query/match-rules.bb src/karabiner.edn p --state "profile=Default:device=Desktop:layer=1"

# List rules in a layer/state
bb scripts/query/list-rules.bb src/karabiner.edn "profile=Default:device=Desktop:layer=9" --format summary
bb scripts/query/list-rules.bb src/karabiner.edn "profile=Default:device=Desktop:layer=1" --exact

# Analyze rule patterns and statistics
bb scripts/query/analyze-rules.bb src/karabiner.edn

# Get next available rule ID (for adding new rules)
bb scripts/query/next-id.bb src/karabiner.edn
```

#### Edit Tools (modify config)

```bash
# Set a rule by ID - the primary way to add/modify rules
# Reads the rule from stdin, places it in the correct block based on its condition
# ALWAYS use --no-clobber when adding NEW rules to prevent accidental overwrites
cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R1234 - --no-clobber
[{:key :j, :id "R1234 [profile=Default:device=Desktop:dsk_layer=7] j → t"} [:t ["dsk_layer" 7] ["dsk_ins_sub_mode" 0] ["dsk_return_to_layer" -1]] [["dsk_layer" 7]]]
EOFR

# Omit --no-clobber only when intentionally modifying an existing rule

# Delete a rule by ID
bb scripts/edit/delete-rule.bb src/karabiner.edn R0081
```


#### Validation Tools (run automatically on sync)

Validation runs automatically during `npm run sync`. Manual invocation rarely needed:
```bash
bb scripts/validate/validate-rules.bb src/karabiner.edn    # Core validation
bb scripts/validate/validate-extras.bb src/karabiner.edn   # Additional checks
```

See `scripts/README.md` for full documentation.

### If Tooling Returns Surprising Results

If any script returns unexpected or incorrect results, **investigate and fix the tooling** rather than working around it. The scripts are meant to be reliable; bugs should be fixed, not tolerated.

### Preferred Workflow for Fixing Rule Issues

When encountering a bug or issue with rules:

1. **Add a validation** - Create or enhance a validation in `scripts/validate/` to detect the issue
2. **Run that validation** - Execute it to surface all instances of the problem
3. **STOP and show the user the violations** - Before fixing anything, present the list of violations to the user so they can verify it matches their expectations. This catches misunderstandings early.
4. **Review the broken rules** - Let the validation output guide which rules need fixing
5. **Fix the rules systematically** - Address each broken rule identified by the validation

This workflow ensures:
- Issues are caught automatically in future changes
- All instances of the problem are found (not just the one that triggered investigation)
- The user can verify the validation matches their intent before changes are made
- The fix is verified by the validation passing

### NEVER Remove Shortcuts Without Permission

If implementing a new feature requires removing/changing an existing shortcut, STOP and ask first.

## User Interaction Notes

The user is using voice-to-text:
- Dictated letters are always uppercase; use judgment for actual case
- Reason about what the user is actually trying to accomplish
- Catch likely mistakes (e.g., "Shift+Tab" when they mean "Ctrl+Shift+Tab")
- If something can be verified mechanically, DO IT instead of asking the user
- Before adding a new variable, explicitly check with the user

**Terminology note**: "Fn+Shift" or "Function Shift" means the oneshot Shift submode (entered via Fn+]), NOT simultaneous Fn+Shift keys. Kinesis hardware prevents simultaneous Fn+Shift. See rhs-slots.md for the shift+fn column definition.

**Key notation**: When discussing keys in conversation, always specify BOTH the physical key and the AltIns output, formatted as `physical/altins`. For example: "h/o" means physical key h which outputs 'o' in AltIns mode. This helps the user who thinks in terms of what they type (AltIns output) while the config uses physical keys. Reference `plans/alt-insert-mode.md` for the full mapping table. Note: Layer overlay files (`src/layers/*.txt`) show only AltIns keys since they're for quick reference while typing.

## Tool Limitations

**WebFetch is sandboxed**: The WebFetch tool cannot fetch external URLs due to sandbox restrictions. For Goku documentation, use the local copies in `docs/goku/` instead of fetching from GitHub.

**Hammerspoon CLI hangs**: NEVER call `/opt/homebrew/bin/hs -c '...'` directly - it hangs indefinitely. Always wrap in a timeout:
```bash
timeout 2 /opt/homebrew/bin/hs -c 'someFunction()' 2>/dev/null || true
```

**Hammerspoon is usually the culprit**: When keyboard shortcuts mysteriously stop working, ALWAYS suspect Hammerspoon first. It has complex state, side effects, and `hs.ipc` can interfere with system keyboard handling. Common symptoms:
- Shortcuts work when Hammerspoon is not running, fail when it's running
- Intermittent failures that seem timing-related
- Keys being "swallowed" or not reaching applications

Debug steps:
1. Quit Hammerspoon and test if the issue persists
2. Check if `hs.ipc` is interfering (comment it out temporarily)
3. Check if poll timers or eventtaps are causing issues
4. Look for `hs -c` calls in Karabiner rules that might hang

## On Startup

Previous sessions sometimes leave things broken. Verify:

```bash
# 1. Check if karabiner.edn is in sync
diff /Users/rbalicki/code/voicemode/src/karabiner.edn ~/.config/karabiner.edn

# 2. Check git status
git -C /Users/rbalicki/code/voicemode status
git -C ~/.config status
```

If they differ, the voicemode version is source of truth. Run `npm run sync`.

## Workflow After Changes

### COMMIT EARLY AND OFTEN

**This is critical.** Do NOT let changes pile up. Commit after EVERY logical change:
- Added a new script? Commit immediately.
- Fixed a rule? Commit immediately.
- Updated documentation? Commit immediately.

Uncommitted changes get lost, cause confusion, and make debugging harder. There is NO reason to wait.

**Commit frequently and atomically.** Each commit should represent ONE logical change (a single feature, fix, or refactor). Don't batch unrelated changes together - this makes it hard to understand history and revert specific changes.

**When committing a logical change:**
1. Commit locally in voicemode repo (include ALL files related to that change)
2. Run `npm run sync` (validates, copies to ~/.config, runs goku)
3. Commit in ~/.config repo with matching message

```bash
git add -A && git commit -m "message"
npm run sync
cd ~/.config && git add -A && git commit -m "message"
```

**Important:** Write commit messages that accurately describe what changed. Don't reference old changes that were already committed. Do NOT add Co-Authored-By lines.

To reload Hammerspoon: `npm run hs`

## Task Tracking & Refactors

### Folder Structure

```
refactors/
├── pending/           # Active work
│   ├── todos.md       # General todo list
│   └── *.md           # Detailed plans for specific refactors
└── past/              # Completed refactors (for reference)
    └── *.md           # Archived plans
```

### Workflow

1. **New refactor**: Create a detailed plan in `refactors/pending/`
2. **Implementation**: Follow the plan step by step, verify at each stage
3. **Completion**: Move the plan to `refactors/past/`

### Plan Requirements

Plans must include:
- **Goal**: What we're trying to achieve
- **Current state**: Relevant existing rules/behavior
- **Implementation steps**: Exact commands to run
- **Verification**: How to check each stage worked
- **Expected test changes**: What should change vs. what would be surprising

**Task descriptions must be detailed enough for a new session with no context to complete the task.** Include relevant rule IDs, key mappings, expected behavior, and what was already tried.

## When Adding a New Layer

- [ ] Add case to `scripts/swiftbar/karabiner-layer.100ms.sh`
- [ ] Create `src/layers/*.txt` file for Hammerspoon overlay
- [ ] Update `layerFiles` map in `~/.hammerspoon/init.lua`

## Key Documentation Files

**Essential reading** (in `.claude/`):
- **`mental-model.md`** - State variables, invariants, layer behavior. Read when adding layers or debugging state issues.
- **`reference.md`** - Layer shortcuts, mode values, Goku modifier syntax. **Keep this updated** when adding/changing layers or shortcuts.
- **`goku-lessons.md`** - Syntax pitfalls, debugging workflow, things that don't work
- **`rhs-slots.md`** - Complete key mapping grid for Ins mode (bare/fn/shift/shift+fn columns)
- **`hardware.md`** - Kinesis Fn layer mappings, physical key layout
- **`todos.md`** - Pending work and feature ideas

**Plan documents** (in `.claude/plans/`):
- **`alt-insert-mode.md`** - **SOURCE OF TRUTH** for AltIns mode (layer 7) key mappings. Always consult this when fixing or adding AltIns rules.

**Source files** (in `src/`):
- `karabiner.edn` - Main Goku config (source of truth)
- `layers/*.txt` - Hammerspoon overlay content per layer
- `kinesis-layout1.txt` - Kinesis firmware Fn layer configuration
- `kinesis-keycodes.txt` - Key code reference for Kinesis macros

**Test files** (in `tests/`):
- `inputs.json` - Keys, modifiers, and fixed combos to test
- `unit/*.json` - Generated snapshot tests

**IMPORTANT**: If you modify `kinesis-layout1.txt`, you MUST also update `tests/inputs.json` to reflect the new Fn key mappings. The `fixed_combos` section defines what keycodes the Kinesis Fn layer produces.

**External files**:
- `~/.config/karabiner.edn` - Deployed config (goku reads from here)
- `~/.hammerspoon/init.lua` - Layer overlay config
- `/tmp/karabiner-layer` - Current layer (runtime)

## Double-Tap Key Pattern (AltIns Mode)

To implement a double-tap key (e.g., comma-comma → period):

1. **Add submode to state.bb**: Add a new submode value (10+) to `valid-submodes` in `scripts/lib/state.bb`

2. **Modify the base rule**: Change the key's rule to enter the new submode instead of clearing to 0
   ```bash
   # R2014: slash → comma, enters submode 10
   cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R2014 -
   [{:key :slash, :modi {:optional [:caps_lock]}, :id "R2014 ..."} [:comma ["dsk_layer" 7] ["dsk_ins_sub_mode" 10] ["dsk_return_to_layer" -1]] [["dsk_layer" 7]]]
   EOFR
   ```

3. **Add the double-tap rule**: In the new submode, output backspace + replacement + clear submode
   ```bash
   # R2101: slash in submode 10 → backspace + period, clears to submode 0
   cat << 'EOFR' | bb scripts/edit/set-rule.bb src/karabiner.edn R2101 -
   [{:key :slash, :modi {:optional [:caps_lock]}, :id "R2101 ..."} [:delete_or_backspace :period ["dsk_layer" 7] ["dsk_ins_sub_mode" 0] ["dsk_return_to_layer" -1]] [["dsk_layer" 7] ["dsk_ins_sub_mode" 10]]]
   EOFR
   ```

4. **Regenerate states**: Run `bb scripts/generate/states.bb` (happens automatically on sync)

**Key insight**: Other keys in AltIns already set submode to 0, so pressing any other key after the first tap clears the pending state automatically.

Current double-tap implementations:
- Submode 10: comma → period (slash key in AltIns)

## Scripts Overview

See `scripts/README.md` for complete documentation.

- **`scripts/query/`** - Read-only tools to inspect rules
- **`scripts/edit/`** - Tools to modify karabiner.edn
- **`scripts/validate/`** - Validation (runs automatically on sync)
- **`scripts/generate/`** - Generate state graphs and valid state lists
- **`scripts/actions/`** - Shell scripts called by rules at runtime
- **`scripts/swiftbar/`** - SwiftBar menu bar plugins

## Network Operations Are Sandboxed

The sandbox blocks network commands (anything touching `gironde`, `ssh`, `git push`, `gh`, etc.). When you need one of these, write a self-contained script and have the user run it via `! <command>` in the prompt, so its output lands back in the session. Don't try to work around the sandbox directly.
