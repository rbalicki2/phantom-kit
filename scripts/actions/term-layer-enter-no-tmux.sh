#!/bin/bash
# Enter term layer without TMUX navigation
# Just update the layer indicator and show overlay - doesn't change focus or send keys

echo term > /tmp/karabiner-layer
/opt/homebrew/bin/hs -c 'showLayerOverlay()' &
