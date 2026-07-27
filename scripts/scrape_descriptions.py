#!/usr/bin/env python3
"""Scrape meta description from each website in resource/web/*.json and add a 'description' field."""

import json
import re
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

WEB_DIR = Path(__file__).parent.parent / "resource" / "web"
TIMEOUT = 10  # seconds per request
DELAY = 0.5   # seconds between requests (be polite)

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
}


def extract_description(html: str) -> str | None:
    """Extract the best available description from HTML."""
    # Try meta description
    m = re.search(
        r'<meta\s+name=["\']description["\']\s+content=["\']([^"\']{10,300})["\']',
        html, re.IGNORECASE
    )
    if m:
        return m.group(1).strip()

    # Try og:description
    m = re.search(
        r'<meta\s+property=["\']og:description["\']\s+content=["\']([^"\']{10,300})["\']',
        html, re.IGNORECASE
    )
    if m:
        return m.group(1).strip()

    # Try first meaningful <p> as fallback
    m = re.search(r'<p[^>]*>([^<]{20,200})</p>', html)
    if m:
        text = re.sub(r'<[^>]+>', '', m.group(1)).strip()
        if len(text) >= 15:
            return text[:200]

    return None


def fetch_description(url: str) -> str | None:
    """Fetch a URL and return its meta description, or None if unavailable."""
    if not url or not url.startswith("http"):
        return None

    try:
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            # Only parse HTML responses
            content_type = resp.headers.get("Content-Type", "")
            if "html" not in content_type:
                return None
            html = resp.read().decode("utf-8", errors="replace")[:200_000]
            return extract_description(html)
    except Exception as e:
        print(f"  ⚠ {url}: {e}", file=sys.stderr)
        return None


def process_file(filepath: Path) -> int:
    """Process one JSON file. Returns number of descriptions added."""
    with open(filepath, "r", encoding="utf-8") as f:
        data = json.load(f)

    added = 0
    for cat_key, cat_obj in data.items():
        items = cat_obj.get("items", [])
        for item in items:
            # Skip if already has a non-empty description
            if item.get("description", "").strip():
                continue
            url = item.get("url", "")
            if not url:
                continue

            print(f"  {item['name']}: {url}")
            desc = fetch_description(url)
            if desc:
                item["description"] = desc
                added += 1
                print(f"    → {desc[:80]}...")
            else:
                print(f"    → (no description found)")
            time.sleep(DELAY)

    if added > 0:
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")

    return added


def main():
    json_files = sorted(WEB_DIR.glob("*.json"))
    total = 0
    for fp in json_files:
        print(f"\n{'='*60}")
        print(f"Processing: {fp.name}")
        print(f"{'='*60}")
        n = process_file(fp)
        total += n
        print(f"  → {n} descriptions added to {fp.name}")

    print(f"\nDone. Total descriptions added: {total}")


if __name__ == "__main__":
    main()
