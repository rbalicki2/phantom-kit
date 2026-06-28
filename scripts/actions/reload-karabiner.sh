#!/usr/bin/env bash
# Force Karabiner-Elements to reload karabiner.json.
#
# goku writes a fresh karabiner.json on every `npm run sync`, but Karabiner does
# not reliably re-read it live: the running grabber keeps the previous rules in
# memory, so freshly-synced rules silently go stale until the profile is
# reselected. That is what made Fn+Shift+arrow fall through to the terminal after
# a sync. Reselecting the active profile forces a clean reload.
#
# We reselect whatever profile is currently default in the config (Default here).
# Toggling through None first guarantees a state change even if Default was
# already selected, so the reload actually fires.
set -euo pipefail

KCLI='/Library/Application Support/org.pqrs/Karabiner-Elements/bin/karabiner_cli'
[ -x "$KCLI" ] || { echo "karabiner_cli not found — skipping reload"; exit 0; }

"$KCLI" --select-profile 'None'    >/dev/null 2>&1 || true
sleep 0.5
"$KCLI" --select-profile 'Default' >/dev/null 2>&1 || true
echo "reloaded Karabiner (reselected Default profile)"
