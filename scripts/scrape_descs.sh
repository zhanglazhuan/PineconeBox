#!/bin/bash
# Scrape meta descriptions from resource/web/*.json using curl
# Usage: bash scripts/scrape_descs.sh
set -e
cd "$(dirname "$0")/.."

TMP="/tmp/pinecone_scrape.json"
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/125"

for json_file in resource/web/*.json; do
    echo "=== $(basename $json_file) ==="

    # Use jq to iterate items, curl to fetch, python just for regex extraction
    items=$(python3 -c "
import json, sys
with open('$json_file') as f:
    data = json.load(f)
for ck, cv in data.items():
    for i, item in enumerate(cv.get('items',[])):
        url = item.get('url','')
        if url and item.get('description','').strip()=='' and url.startswith('http'):
            print(f'{ck}|{i}|{item[\"name\"]}|{url}')
" 2>/dev/null)

    if [ -z "$items" ]; then
        echo "  (all have descriptions, skipping)"
        continue
    fi

    while IFS='|' read -r cat_key idx name url; do
        [ -z "$url" ] && continue
        echo "  $name: $url"
        desc=$(curl -sL --max-time 10 -A "$UA" "$url" 2>/dev/null | \
            python3 -c "
import re, sys
html = sys.stdin.read()[:200000]
m = re.search(r'<meta\s+name=[\"\\']description[\"\\']\s+content=[\"\\']([^\"\\']{10,300})[\"\\']', html, re.I)
if not m:
    m = re.search(r'<meta\s+property=[\"\\']og:description[\"\\']\s+content=[\"\\']([^\"\\']{10,300})[\"\\']', html, re.I)
if not m:
    m = re.search(r'<p[^>]*>([^<]{20,200})</p>', html)
    if m:
        txt = re.sub(r'<[^>]+>', '', m.group(1)).strip()
        if len(txt) >= 15: print(txt[:200]); sys.exit(0)
if m:
    d = m.group(1).replace('&amp;','&').replace('&lt;','<').replace('&gt;','>').replace('&quot;','\"').replace('&#39;',\"'\").strip()
    print(d)
" 2>/dev/null)

        if [ -n "$desc" ]; then
            echo "    OK: ${desc:0:80}..."
            # Update JSON using python
            python3 -c "
import json
with open('$json_file') as f: d = json.load(f)
d['$cat_key']['items'][$idx]['description'] = '''${desc//\'/\'\\\'\'}'''
with open('$json_file','w') as f: json.dump(d, f, ensure_ascii=False, indent=2); f.write('\n')
" 2>/dev/null
        else
            echo "    (no description)"
        fi
        sleep 0.5
    done <<< "$items"
done

echo ""
echo "Done."
