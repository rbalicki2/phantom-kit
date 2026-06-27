#!/usr/bin/env bash
# One-time setup for a fresh clone of voicemode (Phantom Kit).
#
# Establishes the symlink that makes voicemode the source of truth for the
# Karabiner config:
#
#     ~/.config/karabiner/karabiner.json  ->  <repo>/generated/karabiner.json
#
# generated/karabiner.json is checked into the repo (already has the Default and
# None profiles goku needs to merge into), so this works immediately after clone
# without bootstrapping goku. Any existing real karabiner.json is backed up first.
#
# Idempotent: re-running when the symlink is already correct is a no-op. After
# this, `npm run sync` regenerates generated/karabiner.json in place and re-links
# if needed (see scripts/edit/relink-karabiner-json.bb).
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_JSON="$REPO/generated/karabiner.json"
CFG_DIR="$HOME/.config/karabiner"
DEPLOYED="$CFG_DIR/karabiner.json"

[ -f "$REPO_JSON" ] || { echo "error: $REPO_JSON missing — is this a complete clone?" >&2; exit 1; }

mkdir -p "$CFG_DIR"

# Also seed the EDN source goku reads, so `npm run sync` works right away.
if [ -f "$REPO/src/karabiner.edn" ]; then
  cp "$REPO/src/karabiner.edn" "$HOME/.config/karabiner.edn"
  echo "copied src/karabiner.edn -> ~/.config/karabiner.edn"
fi

# Already linked correctly?
if [ -L "$DEPLOYED" ] && [ "$(readlink "$DEPLOYED")" = "$REPO_JSON" ]; then
  echo "already linked: $DEPLOYED -> $REPO_JSON"
  exit 0
fi

# Back up an existing real file (or wrong symlink) before replacing it.
if [ -e "$DEPLOYED" ] || [ -L "$DEPLOYED" ]; then
  ts="$(date +%Y%m%d-%H%M%S)"
  bak="$DEPLOYED.pre-voicemode.$ts"
  mv "$DEPLOYED" "$bak"
  echo "backed up existing karabiner.json -> $bak"
fi

ln -s "$REPO_JSON" "$DEPLOYED"
echo "linked: $DEPLOYED -> $REPO_JSON"
echo
echo "Done. Run 'npm run sync' to regenerate, and select your Karabiner profile (kl/kd)."
