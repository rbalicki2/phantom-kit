#!/bin/bash
# SwiftBar plugin to show current Karabiner layer

LAYER=""
if [ -f "/tmp/karabiner-layer" ]; then
    LAYER=$(tr -d '[:space:]' < /tmp/karabiner-layer)
fi

RHS=""
if [ -f "/tmp/karabiner-rhs" ] && [ -s "/tmp/karabiner-rhs" ]; then
    RHS="RHS-"
fi

# Read mode (iso/pin), default to iso
PROJECT="iso"
if [ -f "/tmp/karabiner-project" ]; then
    PROJECT=$(tr -d '[:space:]' < /tmp/karabiner-project)
fi
[ -z "$PROJECT" ] && PROJECT="iso"

case "$LAYER" in
    norm) NAME="Norm🏠" ;;
    ins) NAME="Ins✏️" ;;
    n) NAME="Nav🧭" ;;
    mouse) NAME="Mouse🖱️" ;;
    comma) NAME=",⌨️" ;;
    l) NAME="L🔧" ;;
    lC) NAME="L-C" ;;
    lTC) NAME="L-TC" ;;
    lT) NAME="L-T" ;;
    lTO) NAME="L-TO" ;;
    lO) NAME="L-O" ;;
    lOC) NAME="L-OC" ;;
    lCTO) NAME="L-CTO" ;;
    tmux) NAME="Tmux📺" ;;
    chrome) NAME="Chrm🌐" ;;
    vscode) NAME="VSC💻" ;;
    term) NAME="Term📟" ;;
    switch) NAME="Swtch" ;;
    winsw) NAME="WinSw" ;;
    *) NAME="Base" ;;
esac

# Combine mode, RHS prefix, and layer name
OUTPUT="${PROJECT}-${RHS}${NAME}"
# Pad to 16 chars minimum width for consistent menu bar space
printf "%-16s | font=Menlo trim=false\n" "$OUTPUT"
