#!/bin/bash
# Type "gh pr create --base <branch>" into terminal with auto-detected parent branch.
# Finds the first named branch between HEAD and merge-base with master.
# Defaults to master if no intermediate branch is found.

# Get tmux pane's working directory
cwd=$(tmux display-message -p '#{pane_current_path}' 2>/dev/null)
if [ -n "$cwd" ]; then
  cd "$cwd" || true
fi

base="master"

# Find merge-base with master
merge_base=$(git merge-base HEAD master 2>/dev/null)

if [ -n "$merge_base" ]; then
  # Walk first-parent from HEAD to merge-base, find decorated commits.
  # Skip the first non-empty line (HEAD itself), take the next one.
  # Extract origin/<branch> name from the decoration string.
  candidate=$(git log --first-parent --decorate=short --simplify-by-decoration \
    --format='%D' "$merge_base"..HEAD 2>/dev/null |
    grep -v '^$' |
    tail -n +2 |
    head -1 |
    tr ',' '\n' |
    sed 's/^ *//' |
    grep '^origin/' |
    head -1 |
    sed 's|^origin/||')

  if [ -n "$candidate" ]; then
    base="$candidate"
  fi
fi

# Type the command into the terminal and press enter
osascript \
  -e 'delay 0.03' \
  -e 'tell app "System Events" to key code 8 using control down' \
  -e 'delay 0.05' \
  -e "tell app \"System Events\" to keystroke \"gh pr create --base $base\"" \
  -e 'tell app "System Events" to key code 36' &

echo altins > /tmp/karabiner-layer
