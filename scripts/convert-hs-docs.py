#!/usr/bin/env python3
"""Convert Hammerspoon HTML docs to markdown.

Extracts module name, description, and method signatures with descriptions.
"""

import os
import re
from html.parser import HTMLParser
from pathlib import Path

DOCS_DIR = Path("/Users/rbalicki/code/voicemode/docs/hammerspoon")
OUTPUT_DIR = Path("/Users/rbalicki/code/voicemode/docs/hammerspoon-md")


class HSDocParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.markdown = []
        self.current_section = None
        self.in_code = False
        self.in_td = False
        self.in_th = False
        self.in_p = False
        self.in_li = False
        self.current_text = ""
        self.current_th = ""
        self.method_info = {}
        self.in_method_section = False
        self.current_method_id = None

    def handle_starttag(self, tag, attrs):
        attrs_dict = dict(attrs)

        if tag == "section":
            section_id = attrs_dict.get("id", "")
            if section_id and not section_id.startswith("_"):
                self.in_method_section = True
                self.current_method_id = section_id
                self.method_info = {"id": section_id}

        elif tag == "code":
            self.in_code = True
        elif tag == "td":
            self.in_td = True
            self.current_text = ""
        elif tag == "th":
            self.in_th = True
            self.current_th = ""
        elif tag == "p":
            self.in_p = True
            self.current_text = ""
        elif tag == "li":
            self.in_li = True
            self.current_text = ""

    def handle_endtag(self, tag):
        if tag == "section" and self.in_method_section:
            if self.method_info.get("signature"):
                self.markdown.append(f"\n### `{self.method_info['signature']}`\n")
                if self.method_info.get("type"):
                    self.markdown.append(f"**Type:** {self.method_info['type']}\n")
                if self.method_info.get("description"):
                    self.markdown.append(f"\n{self.method_info['description']}\n")
                if self.method_info.get("parameters"):
                    self.markdown.append(f"\n**Parameters:**\n{self.method_info['parameters']}\n")
                if self.method_info.get("returns"):
                    self.markdown.append(f"\n**Returns:**\n{self.method_info['returns']}\n")
            self.in_method_section = False
            self.current_method_id = None
            self.method_info = {}

        elif tag == "code":
            self.in_code = False
        elif tag == "td":
            self.in_td = False
            if self.in_method_section and self.current_th:
                key = self.current_th.lower().strip()
                if key == "signature":
                    self.method_info["signature"] = self.current_text.strip()
                elif key == "type":
                    self.method_info["type"] = self.current_text.strip()
                elif key == "description":
                    self.method_info["description"] = self.current_text.strip()
        elif tag == "th":
            self.in_th = False
            self.current_th = self.current_text.strip()
        elif tag == "p":
            self.in_p = False
        elif tag == "li":
            self.in_li = False
            if self.in_method_section:
                # Accumulate list items for parameters/returns
                pass

    def handle_data(self, data):
        if self.in_td or self.in_th or self.in_p or self.in_li:
            self.current_text += data


def convert_file(html_path: Path, output_path: Path):
    """Convert a single HTML file to markdown."""
    content = html_path.read_text(encoding="utf-8")

    # Extract title
    title_match = re.search(r"<title>([^<]+)</title>", content)
    title = title_match.group(1) if title_match else html_path.stem

    # Extract module description (first few paragraphs)
    desc_matches = re.findall(r"<p>([^<]+)</p>", content[:2000])
    description = "\n\n".join(desc_matches[:2]) if desc_matches else ""

    # Parse methods
    parser = HSDocParser()
    try:
        parser.feed(content)
    except Exception as e:
        print(f"  Warning: Parse error in {html_path.name}: {e}")

    # Build markdown
    md = [f"# {title}\n"]
    if description:
        md.append(f"{description}\n")
    md.append("\n## API Reference\n")
    md.extend(parser.markdown)

    output_path.write_text("".join(md), encoding="utf-8")


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    html_files = sorted(DOCS_DIR.glob("hs.*.html"))
    print(f"Converting {len(html_files)} Hammerspoon doc files...")

    for html_file in html_files:
        output_file = OUTPUT_DIR / f"{html_file.stem}.md"
        print(f"  {html_file.name} -> {output_file.name}")
        try:
            convert_file(html_file, output_file)
        except Exception as e:
            print(f"    Error: {e}")

    print(f"\nDone! Markdown docs saved to {OUTPUT_DIR}")
    print(f"Total files: {len(list(OUTPUT_DIR.glob('*.md')))}")


if __name__ == "__main__":
    main()
