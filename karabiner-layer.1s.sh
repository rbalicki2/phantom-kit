#!/bin/bash
# SwiftBar plugin to show current Karabiner layer

LAYER=""
if [ -f "/tmp/karabiner-layer" ]; then
    LAYER=$(tr -d '[:space:]' < /tmp/karabiner-layer)
fi

case "$LAYER" in
    n) echo "       N | font=Menlo trim=false" ;;
    m) echo "       M | font=Menlo trim=false" ;;
    tmux) echo "Tmux (J) | font=Menlo trim=false" ;;
    chrome) echo "Chrm (J) | font=Menlo trim=false" ;;
    *) echo "       - | font=Menlo trim=false" ;;
esac
