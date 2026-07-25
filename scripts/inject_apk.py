#!/usr/bin/env python3
"""
inject_apk.py — Inject PineCone Launcher APK into LineageOS ROM (cross-platform)

Usage:
  python inject_apk.py --apk <path> [--rom <rom.zip>] [--output <out.zip>]

Platform behavior:
  - Linux (with root):   runs native mount/umount directly
  - Windows (with WSL):  auto-launches WSL to run the Linux inject steps
  - Windows (no WSL):    reports what needs to be done manually

The ROM .img is a full MBR disk image (NOT a sparse system.img).
System partition is at byte offset 136,314,880 (LBA 266240) for KonstaKANG RPi5.
"""

import argparse
import json
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

# ---- Partition offset for KonstaKANG LineageOS 23.2 RPi5 ATV ----
SYSTEM_OFFSET = 266240 * 512  # = 136,314,880 bytes

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_DIR = SCRIPT_DIR.parent
LAUNCHER_DIR = PROJECT_DIR / "launcher"
BASEOS_DIR = PROJECT_DIR / "baseOS"

# Default APK (debug build output)
DEFAULT_APK = LAUNCHER_DIR / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"

# Default ROM (ATV variant)
DEFAULT_ROM = BASEOS_DIR / "lineage-23.2-20260520-UNOFFICIAL-KonstaKANG-rpi5-atv.zip"

# Launcher packages to remove from system image
REMOVE_LAUNCHERS = [
    "Trebuchet",
    "Launcher3",
    "Launcher3QuickStep",
    "LeanbackLauncher",
    "TvLauncher",
    "CustomTvLauncher",
    "LegacyLauncher",
]


def is_wsl_available() -> bool:
    """Check if WSL is installed and has a running distro."""
    try:
        # wsl.exe outputs UTF-16LE on Windows; try multiple encodings
        result = subprocess.run(
            ["wsl", "--list", "--verbose"],
            capture_output=True, timeout=10,
        )
        # Try UTF-16LE first (Windows wsl.exe default), then UTF-8
        for encoding in ["utf-16-le", "utf-8", "gbk"]:
            try:
                stdout = result.stdout.decode(encoding)
                if "Running" in stdout:
                    return True
            except (UnicodeDecodeError, LookupError):
                continue
        return False
    except Exception:
        return False


def is_linux() -> bool:
    return platform.system() == "Linux"


def is_root() -> bool:
    try:
        return os.geteuid() == 0
    except AttributeError:
        return False


def find_rom(default_dir: Path) -> Path | None:
    """Find the ATV ROM zip file, preferring the KonstaKANG ATV variant."""
    # 1. Exact default
    if DEFAULT_ROM.exists():
        return DEFAULT_ROM
    # 2. Any -atv.zip in BaseOS/
    import glob
    pattern = str(default_dir / "baseOS" / "*-atv.zip")
    matches = glob.glob(pattern)
    if matches:
        return Path(matches[0])
    # 3. Any .zip in BaseOS/
    pattern2 = str(default_dir / "baseOS" / "*.zip")
    matches2 = glob.glob(pattern2)
    if matches2:
        return Path(matches2[0])
    return None


def run_wsl_inject(rom_zip: str, apk_path: str, output_zip: str) -> bool:
    """
    Print the WSL command for the user to run manually.
    sudo needs a TTY for password input, which subprocess cannot provide.
    """
    def to_wsl_path(win_path: str) -> str:
        """Convert C:\\foo\\bar -> /mnt/c/foo/bar"""
        p = Path(win_path).resolve()
        drive = p.drive[0].lower()
        rest = str(p).replace("\\", "/")[2:]
        return f"/mnt/{drive}{rest}"

    wsl_rom = to_wsl_path(rom_zip)
    wsl_apk = to_wsl_path(apk_path)
    wsl_out = to_wsl_path(output_zip)
    wsl_script = to_wsl_path(str(SCRIPT_DIR / "inject_apk.sh"))

    cmd = f"sudo bash {wsl_script} {wsl_rom} {wsl_apk} {wsl_out}"

    print(f"""
┌──────────────────────────────────────────────────────────────┐
│  sudo needs a TTY for password input.                       │
│  Copy-paste into your WSL terminal:                          │
│                                                              │
│    wsl
│    {cmd}
│                                                              │
└──────────────────────────────────────────────────────────────┘
""")
    return False  # Don't report success/failure; user handles it manually


def run_linux_inject(rom_zip: str, apk_path: str, output_zip: str) -> bool:
    """Run injection natively on Linux via the bash script."""
    script = SCRIPT_DIR / "inject_apk.sh"
    if not script.exists():
        print(f"ERROR: inject_apk.sh not found at {script}")
        return False

    cmd = ["sudo", "bash", str(script), rom_zip, apk_path, output_zip]
    print(f"[Linux] Running: {' '.join(cmd)}")
    result = subprocess.run(cmd)
    return result.returncode == 0


def print_manual_instructions(rom_zip: str, apk_path: str):
    """Print manual steps when no automation is possible."""
    print("""
============================================
 MANUAL INJECTION REQUIRED
============================================

You need a Linux environment (Raspberry Pi, WSL, VM) to inject the APK.
Copy these files to a Linux machine, then run:

  sudo bash inject_apk.sh <rom.zip> <PineConeLauncher.apk>

Or follow these manual steps:

  # 1. Extract the .img from the ROM zip
  unzip {rom} -d /tmp/rom

  # 2. Mount system partition at offset 136314880
  sudo mount -o loop,offset=136314880 /tmp/rom/*.img /mnt/system

  # 3. Copy APK + remove competing launchers
  sudo mkdir -p /mnt/system/system/app/PineConeLauncher
  sudo cp {apk} /mnt/system/system/app/PineConeLauncher/
  sudo chmod 755 /mnt/system/system/app/PineConeLauncher
  sudo chmod 644 /mnt/system/system/app/PineConeLauncher/PineConeLauncher.apk
  sudo rm -rf /mnt/system/system/priv-app/Trebuchet*
  sudo rm -rf /mnt/system/system/priv-app/Launcher3*

  # 4. Unmount and repack
  sudo umount /mnt/system
  (cd /tmp/rom && zip -9 pinecone-os.zip *.img)

============================================
""".format(rom=rom_zip, apk=apk_path))


def main():
    parser = argparse.ArgumentParser(description="Inject PineCone Launcher into LineageOS ROM")
    parser.add_argument("--apk", nargs="?", const="default",
                        help=f"Path to APK (default: {DEFAULT_APK})")
    parser.add_argument("--rom", nargs="?", const="default",
                        help=f"Path to ROM .zip (default: {DEFAULT_ROM})")
    parser.add_argument("--output", help="Output path (default: <rom>-pinecone.zip)")
    args = parser.parse_args()

    # Resolve APK path
    if args.apk and args.apk != "default":
        apk_path = Path(args.apk).resolve()
    else:
        apk_path = DEFAULT_APK.resolve()
        if args.apk != "default":
            print(f"APK default: {apk_path}")

    if not apk_path.exists():
        print(f"ERROR: APK not found: {apk_path}")
        print(f"  Build it first: cd scripts && bash build.sh")
        sys.exit(1)

    # Resolve ROM path
    if args.rom and args.rom != "default":
        rom_path = Path(args.rom).resolve()
    else:
        rom_path = find_rom(PROJECT_DIR)
        if rom_path is None:
            print(f"ERROR: No ROM found. Specify with --rom or place ROM in baseOS/")
            sys.exit(1)

    if not rom_path.exists():
        print(f"ERROR: ROM not found: {rom_path}")
        sys.exit(1)

    # Determine output path
    if args.output:
        output_path = Path(args.output).resolve()
    else:
        stem = rom_path.stem
        output_path = rom_path.parent / f"{stem}-pinecone.zip"

    print("=" * 50)
    print(" PineCone OS — APK Injector")
    print("=" * 50)
    print(f"  APK    : {apk_path}")
    print(f"  ROM    : {rom_path}")
    print(f"  Output : {output_path}")
    print(f"  System partition offset: {SYSTEM_OFFSET} bytes")
    print("=" * 50)

    # Choose injection strategy based on platform
    success = None  # None = manual, True = auto-success, False = auto-fail
    if is_linux() and is_root():
        print("\n[Mode: Linux native]")
        success = run_linux_inject(str(rom_path), str(apk_path), str(output_path))
    elif is_linux():
        print("\n[Mode: Linux — re-launching with sudo]")
        success = run_linux_inject(str(rom_path), str(apk_path), str(output_path))
    elif is_wsl_available():
        print("\n[Mode: Windows → WSL (Ubuntu)]")
        success = run_wsl_inject(str(rom_path), str(apk_path), str(output_path))
    else:
        print("\n[Mode: Manual — no automation available]")
        print("WSL not available. Install WSL or copy files to Linux.")
        print_manual_instructions(str(rom_path), str(apk_path))
        sys.exit(1)

    if success is True:
        print(f"\nSUCCESS! Modified ROM: {output_path}")
    elif success is False:
        print(f"\nFAILED! Check error messages above.")
        sys.exit(1)
    else:
        # Manual mode — command was printed, user runs it themselves
        print(f"\nCommand printed above — run it in WSL to complete injection.")


if __name__ == "__main__":
    main()
