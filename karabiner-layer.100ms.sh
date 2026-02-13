#!/bin/bash
# SwiftBar plugin to show current Karabiner layer

LAYER=""
if [ -f "/tmp/karabiner-layer" ]; then
    LAYER=$(tr -d '[:space:]' < /tmp/karabiner-layer)
fi

# Read mode (iso/pin), default to iso
PROJECT="iso"
if [ -f "/tmp/karabiner-project" ]; then
    PROJECT=$(tr -d '[:space:]' < /tmp/karabiner-project)
fi
[ -z "$PROJECT" ] && PROJECT="iso"

case "$LAYER" in
    norm) NAME="Norm" ;;
    ins) NAME="Ins" ;;
    n) NAME="Nav" ;;
    i) NAME="Admin" ;;
    comma) NAME="Comma" ;;
    l) NAME="L" ;;
    lC) NAME="L-C" ;;
    lTC) NAME="L-TC" ;;
    lT) NAME="L-T" ;;
    lTO) NAME="L-TO" ;;
    lO) NAME="L-O" ;;
    lOC) NAME="L-OC" ;;
    lCTO) NAME="L-CTO" ;;
    tmux) NAME="Tmux" ;;
    chrome) NAME="Chrm" ;;
    vscode) NAME="VSC" ;;
    term) NAME="Git" ;;
    label) NAME="Label" ;;
    grid) NAME="Grid" ;;
    inapp) NAME="InApp" ;;
    *) NAME="Base" ;;
esac

# Combine mode and layer name
OUTPUT="${PROJECT}-${NAME}"
# Pad to 16 chars minimum width for consistent menu bar space
printf "%-16s | font=Menlo trim=false\n" "$OUTPUT"
