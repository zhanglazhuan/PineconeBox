#!/bin/bash
# Scrape meta descriptions from websites in resource/web/*.json
# Usage: bash scripts/scrape_descriptions.sh

set -e
WEB_DIR="resource/web"
TMP_HTML="/tmp/pinecone_scrape.html"
TIMEOUT=10
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

cd "$(dirname "$0")/.."

for json_file in "$WEB_DIR"/*.json; do
    echo ""
    echo "=== $(basename "$json_file") ==="

    # Read each item and process
    python3 -c "
import json, sys, subprocess, re

with open('$json_file', 'r') as f:
    data = json.load(f)

added = 0
for cat_key, cat_obj in data.items():
    items = cat_obj.get('items', [])
    for item in items:
        # Skip if already has description
        if item.get('description', '').strip():
            continue
        url = item.get('url', '')
        if not url or not url.startswith('http'):
            continue

        name = item['name']
        print(f'  {name}: {url}', file=sys.stderr)

        # Fetch with curl
        try:
            result = subprocess.run([
                'curl', '-sL', '--max-time', '$TIMEOUT',
                '-A', '$UA', url
            ], capture_output=True, text=True, timeout=15)
            if result.returncode != 0:
                print(f'    curl failed', file=sys.stderr)
                continue
            html = result.stdout[:200000]
        except Exception as e:
            print(f'    error: {e}', file=sys.stderr)
            continue

        # Extract description
        desc = None
        # Try meta description
        m = re.search(r'<meta\s+name=[\"\\']description[\"\\']\s+content=[\"\\']([^\"\\']{10,300})[\"\\']', html, re.I)
        if m:
            desc = m.group(1).strip()
        if not desc:
            m = re.search(r'<meta\s+property=[\"\\']og:description[\"\\']\s+content=[\"\\']([^\"\\']{10,300})[\"\\']', html, re.I)
            if m:
                desc = m.group(1).strip()
        if not desc:
            m = re.search(r'<p[^>]*>([^<]{20,200})</p>', html)
            if m:
                text = re.sub(r'<[^>]+>', '', m.group(1)).strip()
                if len(text) >= 15:
                    desc = text[:200]

        if desc:
            # Clean up HTML entities
            desc = desc.replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>').replace('&quot;', '\"').replace('&#39;', \"'\")
            item['description'] = desc
            added += 1
            print(f'    OK: {desc[:80]}...', file=sys.stderr)
        else:
            print(f'    (no description)', file=sys.stderr)

if added > 0:
    with open('$json_file', 'w') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')
    print(f'  Saved {added} descriptions to $json_file', file=sys.stderr)
else:
    print(f'  No new descriptions for $json_file', file=sys.stderr)
"
    # Small delay between files
    sleep 1
done

echo ""
echo "Done."
