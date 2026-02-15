#!/bin/bash

# Backup existing karabiner config with timestamp
BACKUP_DIR="/Users/rbalicki/.config/karabiner/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

mkdir -p "$BACKUP_DIR"
cp /Users/rbalicki/.config/karabiner/karabiner.json "$BACKUP_DIR/karabiner_$TIMESTAMP.json"
echo "Backed up to: $BACKUP_DIR/karabiner_$TIMESTAMP.json"

# Copy new config
cp /Users/rbalicki/code/voicemode/karabiner.json /Users/rbalicki/.config/karabiner/karabiner.json
echo "Copied new config to ~/.config/karabiner/karabiner.json"
