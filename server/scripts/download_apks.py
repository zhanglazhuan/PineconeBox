#!/usr/bin/env python3
"""
One-click APK download / update script.

Usage:
    python download_apks.py                  # Download all missing APKs
    python download_apks.py --all             # Force re-download all
    python download_apks.py --name <app>      # Download single app by name
    python download_apks.py --list            # List all APKs and download status
    python download_apks.py --dry-run         # Preview without downloading
"""

import json
import os
import sys
import urllib.request
import urllib.error
import ssl
from pathlib import Path

# Fix Windows console encoding for Chinese characters
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = Path(__file__).resolve().parent.parent
APKS_DIR = ROOT / "static" / "apks"
APPS_JSON = ROOT.parent / "resource" / "apk" / "apps.json"
URLS_JSON = ROOT / "apk_urls.json"


def load_json(path: Path) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def load_apps() -> list:
    """Extract all APK entries from apps.json as a flat list."""
    data = load_json(APPS_JSON)
    apps = []
    for category, entries in data.items():
        if category == "精选推荐":
            continue
        if isinstance(entries, list):
            for entry in entries:
                entry["_category"] = category
                apps.append(entry)
    return apps


def load_urls() -> dict:
    return load_json(URLS_JSON)


def check_exists(filename: str) -> bool:
    p = APKS_DIR / filename
    return p.exists() and p.stat().st_size > 0


def download_file(url: str, dest: Path) -> bool:
    """Download a file. Returns True on success."""
    if not url:
        return False
    try:
        ctx = ssl.create_default_context()
        req = urllib.request.Request(url, headers={
            "User-Agent": "PineConeBox/1.0"
        })
        with urllib.request.urlopen(req, context=ctx, timeout=120) as resp:
            data = resp.read()
            if data:
                dest.parent.mkdir(parents=True, exist_ok=True)
                with open(dest, "wb") as f:
                    f.write(data)
                size_mb = len(data) / (1024 * 1024)
                print(f"  [OK] {size_mb:.1f} MB")
                return True
    except urllib.error.HTTPError as e:
        print(f"  [FAIL] HTTP {e.code}: {e.reason}")
    except urllib.error.URLError as e:
        print(f"  [FAIL] Connection: {e.reason}")
    except Exception as e:
        print(f"  [FAIL] {e}")
    return False


def cmd_list():
    """List all APKs and their download status."""
    apps = load_apps()
    urls_data = load_urls()
    header = f"{'Status':<10} {'Name':<20} {'Category':<14} {'File'}"
    print(header)
    print("-" * len(header))
    for app in apps:
        path = app["path"]
        exists = check_exists(path)
        has_url = bool(urls_data.get(path, {}).get("url", ""))
        if exists:
            status = "[OK]"
        elif has_url:
            status = "[TODO]"
        else:
            status = "[NO URL]"
        print(f"{status:<10} {app['name']:<20} {app.get('_category',''):<14} {path}")


def cmd_download(dry_run: bool = False, force_all: bool = False,
                 target_name: str = None):
    """Download APKs that are missing or outdated."""
    apps = load_apps()
    urls_data = load_urls()

    if target_name:
        apps = [a for a in apps if a["name"] == target_name]
        if not apps:
            print(f"App not found: {target_name}")
            return

    to_download = []
    skipped = {}

    for app in apps:
        path = app["path"]
        exists = check_exists(path)
        url_entry = urls_data.get(path, {})
        url = url_entry.get("url", "")

        if not url:
            skipped["no URL configured"] = skipped.get("no URL configured", 0) + 1
        elif force_all:
            to_download.append((app, url))
        elif exists:
            skipped["already exists"] = skipped.get("already exists", 0) + 1
        else:
            to_download.append((app, url))

    if not to_download:
        print("All APKs are up to date.")
        return

    label = "[DRY RUN] " if dry_run else ""
    print(f"\n{label}To download: {len(to_download)} APK(s)")
    for reason, count in sorted(skipped.items()):
        print(f"  Skipped ({reason}): {count}")
    print()

    if dry_run:
        for app, url in to_download:
            print(f"  -> {app['name']}  [{app.get('_category','')}]")
            print(f"     {url}")
        return

    ok = 0
    for app, url in to_download:
        dest = APKS_DIR / app["path"]
        name = app["name"]
        cat = app.get("_category", "")
        print(f">> {name}  [{cat}]")
        print(f"   {url}")
        if download_file(url, dest):
            ok += 1
        print()

    print(f"Done: {ok}/{len(to_download)} succeeded")


def main():
    args = sys.argv[1:]
    dry_run = "--dry-run" in args
    force_all = "--all" in args
    do_list = "--list" in args

    target_name = None
    for i, a in enumerate(args):
        if a == "--name" and i + 1 < len(args):
            target_name = args[i + 1]
            break

    if do_list:
        cmd_list()
    else:
        cmd_download(dry_run=dry_run, force_all=force_all, target_name=target_name)

    print("\nTip: After downloading, verify APK signatures and update version in apk_urls.json.")


if __name__ == "__main__":
    main()
