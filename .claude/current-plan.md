# Current Work Stack

Track ongoing tasks and side quests. When starting a side quest, push to top. When completing, pop.

## Active Stack (most recent first)

### 0. [Bug] Comma mode quick-entry + comma doesn't emit Ctrl+C to app
**Status**: Pending
- Karabiner EventViewer sees the Ctrl+C but frontmost app doesn't receive it
- Happens when entering comma mode fast and hitting comma again

### 0a. [Bug] Fn+Space then Fn+letter outputs lowercase instead of Shift+letter
**Status**: FIXED
- Added new block "[Desktop, Layer 1, Submode 2] Shift oneshot Fn+keys"
- Added 15 rules (R1500-R1514) for Fn+keys in submode 2
- Also removed 283 LHS key rules that were causing validation failures

### 0b. [Side Quest] Allow-list up/down keys globally
**Status**: Pending

### 0c. [Enhancement] list-rules.bb "match-state" mode
**Status**: Pending
- Add flag to show ALL rules matching a state (global + desktop + layer + submode)
- More useful than current hierarchical listing

### 0d. [Enhancement] Pretty-print karabiner.edn on modify
**Status**: Pending
- Always pretty-print when modifying the EDN file

### 1. [Side Quest] Make insert-rules.bb modify in place
**Status**: Pending
- Change script to take only input file (modify in place)
- Remove output file argument - we have git for safety

### 2. Add Admin mode key for window arrangement (iTerm 70% / EventViewer 30%)
**Status**: Completed

### 3. [Side Quest] Organize scripts folder
**Status**: Completed

Implemented structure:
- `scripts/actions/` - Shell scripts called during rules
- `scripts/query/` - Scripts for querying the EDN
- `scripts/edit/` - Scripts for modifying the EDN
- `scripts/validate/` - Validation scripts
- `scripts/generate/` - Rule generators
- `scripts/lib/` - Common library code
- `scripts/swiftbar/` - SwiftBar plugins
- `scripts/test/` - Unit tests
- `scripts/misc/` - One-off utilities

Created `scripts/README.md` with full documentation. Updated CLAUDE.md and package.json paths.

## Completed Today

- [x] Organize scripts folder into subfolders with README.md documentation
- [x] Investigate original oneshot bug cause (cannot reproduce - modifier shadowing doesn't occur in Karabiner 15.x)
- [x] Remove false-positive modifier ordering validation from validate-rules.bb
- [x] Add catch-all shadowing validation (##key rules must come after specific rules)
- [x] Document modifier mental model in karabiner-modifiers.md
- [x] Add LHS key validation to validate-extras.bb
- [x] Fix LHS key issues - removed 283 rules:
  - 266 from L Layer block (layers 14-27)
  - 8 from Oneshot submode 1
  - 8 from Oneshot submode 2
  - 1 from Ins passthrough (equal_sign)

## Validation Updates

### validate-rules.bb
- Catch-all shadowing: `##key` rules must come AFTER specific rules for same key

### validate-extras.bb
- LHS key check: Keys like q,w,e,r,t,a,s,d,f,g,z,x,c,v,b,1-5, backspace, delete, etc. can ONLY appear in:
  - `[Global] Block all unmapped keys`
  - `[Desktop] Disable backspace and delete keys`
- Note: backslash is RHS on Kinesis 360 (not LHS like standard keyboards)

## Context From Previous Sessions

- Enter passthrough added as catch-all
- Backslash blocks merged with correct ordering
