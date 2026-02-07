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
    norm) NAME="Norm" ;;
    ins) NAME="Ins" ;;
    n) NAME="Nav" ;;
    m) NAME="M" ;;
    h) NAME="H" ;;
    hC) NAME="H-C" ;;
    hTC) NAME="H-TC" ;;
    hT) NAME="H-T" ;;
    hTO) NAME="H-TO" ;;
    hO) NAME="H-O" ;;
    hOC) NAME="H-OC" ;;
    hCTO) NAME="H-CTO" ;;
    tmux) NAME="Tmux" ;;
    chrome) NAME="Chrm" ;;
    vscode) NAME="VSC" ;;
    term) NAME="Term" ;;
    switch) NAME="Swtch" ;;
    winsw) NAME="WinSw" ;;
    *) NAME="Base" ;;
esac

# Combine mode, RHS prefix, and layer name
OUTPUT="${PROJECT}-${RHS}${NAME}"
# Pad to 16 chars minimum width for consistent menu bar space
printf "%-16s | font=Menlo trim=false\n" "$OUTPUT"
