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
MODE="iso"
if [ -f "/tmp/karabiner-mode" ]; then
    MODE=$(tr -d '[:space:]' < /tmp/karabiner-mode)
fi
[ -z "$MODE" ] && MODE="iso"

case "$LAYER" in
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
    term) NAME="Term" ;;
    switch) NAME="Swtch" ;;
    winsw) NAME="WinSw" ;;
    *) NAME="Base" ;;
esac

# Combine mode, RHS prefix, and layer name
OUTPUT="${MODE}-${RHS}${NAME}"
printf "%s | font=Menlo trim=false\n" "$OUTPUT"
