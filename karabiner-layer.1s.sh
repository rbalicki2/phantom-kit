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
    *) NAME="-" ;;
esac

# Combine RHS prefix with layer name, right-align to 8 chars
OUTPUT="${RHS}${NAME}"
printf "%8s | font=Menlo trim=false\n" "$OUTPUT"
