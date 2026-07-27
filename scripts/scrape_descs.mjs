#!/usr/bin/env node
/** Scrape meta descriptions using curl (which is more reliable) */
import { readFileSync, writeFileSync, readdirSync } from 'fs';
import { resolve, join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));
const WEB_DIR = resolve(__dirname, '..', 'resource', 'web');
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/125';

function fetchViaCurl(url) {
  try {
    return execSync(`curl -sL --max-time 10 -A "${UA}" "${url}"`, {
      encoding: 'utf-8', maxBuffer: 500 * 1024, timeout: 12000,
      stdio: ['ignore', 'pipe', 'ignore']
    }).slice(0, 200000);
  } catch { return null; }
}

function extract(html) {
  if (!html) return null;
  // meta description
  let m = html.match(/<meta\s+name=["']description["']\s+content=["']([^"']{10,300})["']/i);
  if (m) return clean(m[1]);
  // og:description
  m = html.match(/<meta\s+property=["']og:description["']\s+content=["']([^"']{10,300})["']/i);
  if (m) return clean(m[1]);
  // first paragraph fallback
  m = html.match(/<p[^>]*>([^<]{20,200})<\/p>/);
  if (m) { const t = m[1].replace(/<[^>]+>/g, '').trim(); if (t.length >= 15) return t.slice(0, 200); }
  return null;
}

function clean(s) {
  return s.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").trim();
}

async function main() {
  const files = readdirSync(WEB_DIR).filter(f => f.endsWith('.json')).sort();
  let total = 0;

  for (const f of files) {
    const fp = join(WEB_DIR, f);
    const data = JSON.parse(readFileSync(fp, 'utf-8'));
    let added = 0;

    for (const [catKey, catObj] of Object.entries(data)) {
      for (let i = 0; i < (catObj.items || []).length; i++) {
        const item = catObj.items[i];
        if (item.description?.trim()) continue;
        const url = item.url;
        if (!url?.startsWith('http')) continue;

        console.log(`  ${item.name}: ${url}`);
        const html = fetchViaCurl(url);
        const desc = extract(html);
        if (desc) {
          item.description = desc;
          added++;
          console.log(`    → ${desc.slice(0, 80)}...`);
        } else {
          console.log(`    → (none)`);
        }
        // Be polite
        await new Promise(r => setTimeout(r, 500));
      }
    }

    if (added > 0) {
      writeFileSync(fp, JSON.stringify(data, null, 2) + '\n', 'utf-8');
    }
    total += added;
    console.log(`  ${f}: +${added} descriptions\n`);
  }
  console.log(`Done. Total: ${total} descriptions added.`);
}

main().catch(console.error);
