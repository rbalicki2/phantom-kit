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
    lCS) NAME="L-CS" ;;
    lT) NAME="L-T" ;;
    lTS) NAME="L-TS" ;;
    lTC) NAME="L-TC" ;;
    lTCS) NAME="L-TCS" ;;
    lTO) NAME="L-TO" ;;
    lTOS) NAME="L-TOS" ;;
    lO) NAME="L-O" ;;
    lOS) NAME="L-OS" ;;
    lOC) NAME="L-OC" ;;
    lOCS) NAME="L-OCS" ;;
    lCTO) NAME="L-CTO" ;;
    lCTOS) NAME="L-CTOS" ;;
    lentry) NAME="L-Ent" ;;
    lactive)
        # Show modifier code when in L-Active
        MOD=""
        if [ -f "/tmp/karabiner-lmode-modifier" ]; then
            MOD=$(tr -d '[:space:]' < /tmp/karabiner-lmode-modifier)
        fi
        if [ -n "$MOD" ]; then
            NAME="L-$MOD"
        else
            NAME="L-Act"
        fi
        ;;
    tmux) NAME="Tmux" ;;
    chrome) NAME="Chrm" ;;
    vscode) NAME="VSC" ;;
    term) NAME="Git" ;;
    label) NAME="Label" ;;
    grid) NAME="Grid" ;;
    inapp) NAME="InApp" ;;
    appsw) NAME="AppSw" ;;
    winsw) NAME="WinSw" ;;
    altins) NAME="AltIns" ;;
    *) NAME="Base" ;;
esac

# Combine mode and layer name
OUTPUT="${PROJECT}-${NAME}"
# Pad to 16 chars minimum width for consistent menu bar space
printf "%-16s | font=Menlo trim=false\n" "$OUTPUT"
