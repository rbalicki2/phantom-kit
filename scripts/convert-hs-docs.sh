#!/bin/bash
# Convert Hammerspoon HTML docs to markdown
# Extracts: module name, description, methods with signatures and descriptions

DOCS_DIR="/Users/rbalicki/code/voicemode/docs/hammerspoon"
OUTPUT_DIR="/Users/rbalicki/code/voicemode/docs/hammerspoon-md"

mkdir -p "$OUTPUT_DIR"

echo "Converting Hammerspoon docs to markdown..."

for html_file in "$DOCS_DIR"/hs.*.html; do
    [ -f "$html_file" ] || continue

    basename=$(basename "$html_file" .html)
    output_file="$OUTPUT_DIR/${basename}.md"

    echo "Converting $basename..."

    # Use sed/awk to extract key information from HTML
    # This is a simplified extraction - gets module name, methods, and descriptions

    cat > "$output_file" << 'HEADER'
HEADER

    # Extract module name from title
    title=$(grep -oP '(?<=<title>)[^<]+' "$html_file" | head -1)
    echo "# $title" >> "$output_file"
    echo "" >> "$output_file"

    # Extract module description (first <p> after Module header)
    grep -oP '(?<=<p>)[^<]+(?=</p>)' "$html_file" | head -3 | while read -r line; do
        echo "$line" >> "$output_file"
        echo "" >> "$output_file"
    done

    echo "## Methods" >> "$output_file"
    echo "" >> "$output_file"

    # Extract method signatures and descriptions
    # Look for patterns like: <code>hs.canvas:alpha([alpha])</code>
    grep -oP '(?<=<code>)hs\.[^<]+(?=</code>)' "$html_file" | sort -u | while read -r sig; do
        echo "### \`$sig\`" >> "$output_file"
        echo "" >> "$output_file"
    done

done

echo ""
echo "Done! Markdown docs saved to $OUTPUT_DIR"
echo "Total files: $(ls -1 "$OUTPUT_DIR"/*.md 2>/dev/null | wc -l | tr -d ' ')"
