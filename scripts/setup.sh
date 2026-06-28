#!/usr/bin/env bash
# One-time setup for a fresh clone of voicemode (Phantom Kit).
#
# Makes voicemode the source of truth for both configs it owns, by symlinking the
# real locations to the checked-in copies:
#
#     ~/.config/karabiner/karabiner.json  ->  <repo>/generated/karabiner.json
#     ~/.hammerspoon/<file>.lua           ->  <repo>/hammerspoon/<file>.lua
#
# generated/karabiner.json is checked into the repo (already has the Default and
# None profiles goku needs to merge into), so it works immediately after clone
# without bootstrapping goku. Any existing real files are backed up first.
#
# Idempotent: re-running when a symlink is already correct is a no-op. After
# linking, this restarts Hammerspoon and runs `npm run sync` to regenerate
# generated/karabiner.json from src and reload Karabiner, so the deployed config
# matches the source you pulled rather than a stale committed copy.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Symlink $2 -> $1 (target -> real), backing up anything already at $2.
link() {
  local target="$1" real="$2"
  [ -e "$target" ] || { echo "error: $target missing — incomplete clone?" >&2; return 1; }
  if [ -L "$real" ] && [ "$(readlink "$real")" = "$target" ]; then
    echo "already linked: $real -> $target"
    return 0
  fi
  mkdir -p "$(dirname "$real")"
  if [ -e "$real" ] || [ -L "$real" ]; then
    local bak="$real.pre-voicemode.$(date +%Y%m%d-%H%M%S)"
    mv "$real" "$bak"
    echo "backed up existing $(basename "$real") -> $bak"
  fi
  ln -s "$target" "$real"
  echo "linked: $real -> $target"
}

echo "== Karabiner =="
# Seed the EDN source goku reads, so `npm run sync` works right away.
if [ -f "$REPO/src/karabiner.edn" ]; then
  cp "$REPO/src/karabiner.edn" "$HOME/.config/karabiner.edn"
  echo "copied src/karabiner.edn -> ~/.config/karabiner.edn"
fi
link "$REPO/generated/karabiner.json" "$HOME/.config/karabiner/karabiner.json"

echo
echo "== Hammerspoon =="
for f in "$REPO"/hammerspoon/*.lua; do
  [ -e "$f" ] || { echo "no hammerspoon/*.lua in repo — skipping"; break; }
  link "$f" "$HOME/.hammerspoon/$(basename "$f")"
done

echo
echo "== Hammerspoon (re)start =="
# Quit and reopen so a fresh copy of the symlinked lua is loaded. npm run hs only
# reloads a LIVE instance via hs.ipc, so it can't start a dead app.
osascript -e 'tell application "Hammerspoon" to quit' 2>/dev/null || true
sleep 1
open -a Hammerspoon 2>/dev/null || true

echo
echo "== Karabiner sync =="
# Regenerate generated/karabiner.json from src (don't trust the committed copy to
# be current) and reload Karabiner. This is what makes the deployed rules match
# the source you just pulled.
npm --prefix "$REPO" run sync

echo
echo "Done."
