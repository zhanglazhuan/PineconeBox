#!/usr/bin/env node
/** Scrape meta description from each website in resource/web/*.json */

import { readFileSync, writeFileSync } from 'fs';
import { resolve, dirname, join } from 'path';
import { fileURLToPath } from 'url';
import https from 'https';
import http from 'http';

const __dirname = dirname(fileURLToPath(import.meta.url));
const WEB_DIR = resolve(__dirname, '..', 'resource', 'web');
const TIMEOUT = 10_000;
const DELAY = 600;

const HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36',
  'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8',
};

function fetchText(url) {
  return new Promise((resolve, reject) => {
    const mod = url.startsWith('https') ? https : http;
    const req = mod.get(url, { headers: HEADERS, timeout: TIMEOUT }, (res) => {
      // Follow redirects up to 3 times
      if ([301, 302, 303, 307, 308].includes(res.statusCode)) {
        const loc = res.headers.location;
        if (loc) return resolve(fetchText(loc.startsWith('http') ? loc : new URL(loc, url).href));
      }
      if (res.statusCode !== 200) { res.resume(); return resolve(null); }
      const ct = res.headers['content-type'] || '';
      if (!ct.includes('html')) { res.resume(); return resolve(null); }
      let body = '';
      res.on('data', (chunk) => { body += chunk; if (body.length > 200_000) res.destroy(); });
      res.on('end', () => resolve(body));
      res.on('error', () => resolve(null));
    });
    req.on('error', () => resolve(null));
    req.on('timeout', () => { req.destroy(); resolve(null); });
  });
}

function extractDescription(html) {
  // Try meta description
  let m = html.match(/<meta\s+name=["']description["']\s+content=["']([^"']{10,300})["']/i);
  if (m) return clean(m[1]);
  // Try og:description
  m = html.match(/<meta\s+property=["']og:description["']\s+content=["']([^"']{10,300})["']/i);
  if (m) return clean(m[1]);
  // Try first meaningful paragraph
  m = html.match(/<p[^>]*>([^<]{20,200})<\/p>/);
  if (m) { const t = m[1].replace(/<[^>]+>/g, '').trim(); if (t.length >= 15) return t.slice(0, 200); }
  return null;
}

function clean(s) {
  return s.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").trim();
}

async function processFile(filepath) {
  const data = JSON.parse(readFileSync(filepath, 'utf-8'));
  let added = 0;

  for (const [catKey, catObj] of Object.entries(data)) {
    const items = catObj.items || [];
    for (const item of items) {
      if (item.description && item.description.trim()) continue;
      const url = item.url;
      if (!url || !url.startsWith('http')) continue;

      console.log(`  ${item.name}: ${url}`);
      try {
        const html = await fetchText(url);
        if (!html) { console.log('    → (no response)'); continue; }
        const desc = extractDescription(html);
        if (desc) {
          item.description = desc;
          added++;
          console.log(`    → ${desc.slice(0, 80)}...`);
        } else {
          console.log('    → (no description found)');
        }
      } catch (e) {
        console.log(`    → error: ${e.message}`);
      }
      // Be polite between requests
      await new Promise(r => setTimeout(r, DELAY));
    }
  }

  if (added > 0) {
    writeFileSync(filepath, JSON.stringify(data, null, 2) + '\n', 'utf-8');
  }
  return added;
}

async function main() {
  const fs = await import('fs');
  const files = fs.readdirSync(WEB_DIR).filter(f => f.endsWith('.json')).sort();
  let total = 0;
  for (const f of files) {
    console.log(`\n=== ${f} ===`);
    const n = await processFile(join(WEB_DIR, f));
    total += n;
    console.log(`  → ${n} descriptions added`);
  }
  console.log(`\nDone. Total: ${total} descriptions.`);
}

main().catch(console.error);
