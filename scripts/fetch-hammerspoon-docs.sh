#!/bin/bash
# Fetch Hammerspoon documentation for local reference
# Usage: ./scripts/fetch-hammerspoon-docs.sh

DOCS_DIR="/Users/rbalicki/code/voicemode/docs/hammerspoon"
BASE_URL="https://www.hammerspoon.org/docs"

mkdir -p "$DOCS_DIR"

echo "Fetching Hammerspoon docs index..."

# First, get the main index page
curl -s "$BASE_URL/" > "$DOCS_DIR/index.html"

# Extract all doc links (hs.*.html pattern)
echo "Extracting module links..."
MODULES=$(grep -oE 'href="(hs\.[^"]+\.html)"' "$DOCS_DIR/index.html" | sed 's/href="//g' | sed 's/"//g' | sort -u)

# Count modules
COUNT=$(echo "$MODULES" | wc -l | tr -d ' ')
echo "Found $COUNT module pages to download"

# Download each module page
CURRENT=0
for module in $MODULES; do
    CURRENT=$((CURRENT + 1))
    echo "[$CURRENT/$COUNT] Downloading $module..."
    curl -s "$BASE_URL/$module" > "$DOCS_DIR/$module"
    sleep 0.2  # Be polite
done

echo ""
echo "Done! Docs saved to $DOCS_DIR"
echo "Total files: $(ls -1 "$DOCS_DIR" | wc -l | tr -d ' ')"
echo ""
echo "Key files:"
echo "  - hs.canvas.html (for overlay transparency)"
echo "  - index.html (full module list)"
