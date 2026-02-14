#!/bin/bash
# Cycle through project modes: iso → pin → pk → iso
# Used by Comma layer M shortcut

PROJECT_FILE="/tmp/karabiner-project"
CURRENT=$(cat "$PROJECT_FILE" 2>/dev/null)

case "$CURRENT" in
    iso) echo pin ;;
    pin) echo pk ;;
    *)   echo iso ;;
esac > "$PROJECT_FILE"
