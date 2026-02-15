#!/usr/bin/env python3
"""
Scrape Karabiner documentation from https://karabiner-elements.pqrs.org/docs/

Usage:
    python3 scrape-karabiner-docs.py <output-dir>

This will download all pages under /docs/ and save them as HTML files.
"""

import sys
import os
import re
import time
from urllib.parse import urljoin, urlparse
from urllib.request import urlopen, Request
from html.parser import HTMLParser

BASE_URL = "https://karabiner-elements.pqrs.org/docs/"
VISITED = set()

class LinkExtractor(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []

    def handle_starttag(self, tag, attrs):
        if tag == 'a':
            for name, value in attrs:
                if name == 'href' and value:
                    self.links.append(value)

def is_valid_url(url):
    """Check if URL is under the docs path"""
    parsed = urlparse(url)
    return (parsed.netloc == "karabiner-elements.pqrs.org" and
            parsed.path.startswith("/docs/"))

def fetch_page(url):
    """Fetch a page and return its content"""
    try:
        req = Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urlopen(req, timeout=10) as response:
            return response.read().decode('utf-8')
    except Exception as e:
        print(f"  Error fetching {url}: {e}")
        return None

def extract_links(html, base_url):
    """Extract all links from HTML"""
    parser = LinkExtractor()
    parser.feed(html)
    links = []
    for href in parser.links:
        full_url = urljoin(base_url, href)
        # Remove fragment
        full_url = full_url.split('#')[0]
        if is_valid_url(full_url) and full_url not in VISITED:
            links.append(full_url)
    return links

def url_to_filename(url, output_dir):
    """Convert URL to local filename"""
    parsed = urlparse(url)
    path = parsed.path.rstrip('/')
    if not path or path == '/docs':
        path = '/docs/index'
    # Replace / with _ for filename
    filename = path.replace('/', '_') + '.html'
    return os.path.join(output_dir, filename)

def scrape(start_url, output_dir):
    """Recursively scrape all pages"""
    queue = [start_url]

    while queue:
        url = queue.pop(0)
        if url in VISITED:
            continue

        VISITED.add(url)
        print(f"Fetching: {url}")

        html = fetch_page(url)
        if html is None:
            continue

        # Save the page
        filename = url_to_filename(url, output_dir)
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(html)
        print(f"  Saved: {filename}")

        # Extract and queue new links
        new_links = extract_links(html, url)
        for link in new_links:
            if link not in VISITED:
                queue.append(link)

        # Be polite
        time.sleep(0.5)

    print(f"\nDone! Scraped {len(VISITED)} pages to {output_dir}")

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 scrape-karabiner-docs.py <output-dir>")
        sys.exit(1)

    output_dir = sys.argv[1]
    os.makedirs(output_dir, exist_ok=True)

    scrape(BASE_URL, output_dir)

if __name__ == "__main__":
    main()
