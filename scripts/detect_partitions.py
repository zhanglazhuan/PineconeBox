#!/usr/bin/env python3
"""
Detect the system partition offset in a LineageOS disk image.

Parses MBR and EBR chain to find all partitions and prints their offsets.
Used by inject_apk.sh or standalone for debugging.

Usage:
  python detect_partitions.py <image_file_or_zip>
  python detect_partitions.py <image_file_or_zip> --system-only

Output (JSON):
  {
    "partitions": [
      {"name": "boot", "type": "fat32", "lba": 2048, "offset": 1048576, "size_mb": 128},
      {"name": "system", "type": "ext4", "lba": 266240, "offset": 136314880, "size_mb": 3072},
      {"name": "vendor", "type": "ext4", "lba": 6559744, "offset": 3358588928, "size_mb": 384},
      {"name": "userdata", "type": "ext4", "lba": 7383040, "offset": 3780116480, "size_mb": 11043}
    ],
    "system_offset": 136314880
  }

Works on both Windows and Linux. No root required.
"""

import json
import struct
import sys
import zipfile
from pathlib import Path

# Known partition type labels
MBR_TYPES = {
    0x0C: ("fat32", "boot"),
    0x0B: ("fat32", "boot"),
    0x0E: ("fat16", "boot"),
    0x05: ("extended", None),
    0x0F: ("extended", None),
    0x83: ("ext4", None),  # Could be system/vendor/userdata
}

# Logical ext4 partitions, in order on KonstaKANG RPi5 ROMs
LOGICAL_EXt4_NAMES = ["system", "vendor", "misc"]


def parse_entry(data: bytes, base_lba: int = 0) -> dict | None:
    """Parse a 16-byte MBR/EBR partition entry."""
    if len(data) < 16:
        return None
    status, _, part_type, _, start_lba, num_sectors = struct.unpack(
        "<B3sB3sII", data
    )
    if part_type == 0:
        return None
    return {
        "type_code": part_type,
        "type_name": MBR_TYPES.get(part_type, ("unknown", None))[0],
        "lba_abs": base_lba + start_lba,
        "lba_rel": start_lba,
        "num_sectors": num_sectors,
        "size_bytes": num_sectors * 512,
        "size_mb": round((num_sectors * 512) / (1024 * 1024), 1),
    }


def find_partitions(img_path: str) -> list[dict]:
    """Find all partitions in the disk image."""
    parts = []
    ext_start = 0
    ext_sectors = 0

    p = Path(img_path)
    if p.suffix.lower() == ".zip":
        zf = zipfile.ZipFile(img_path, "r")
        names = zf.namelist()
        img_name = next((n for n in names if n.endswith(".img")), names[0])
        f = zf.open(img_name)
    else:
        f = open(img_path, "rb")

    try:
        # Read MBR (sector 0)
        mbr = f.read(512)

        # Parse 4 primary entries at offset 446
        for i in range(4):
            off = 446 + i * 16
            p = parse_entry(mbr[off : off + 16])
            if p:
                if p["type_name"] == "extended":
                    ext_start = p["lba_abs"]
                    ext_sectors = p["num_sectors"]
                else:
                    p["name"] = MBR_TYPES.get(p["type_code"], ("unknown", None))[1] or f"primary_{i}"
                    parts.append(p)

        # Follow EBR chain
        if ext_start > 0:
            current = ext_start
            vol_idx = 0

            for _ in range(10):  # Safety limit
                f.seek(current * 512)
                ebr = f.read(512)

                # Entry 1 (offset 446): logical partition
                e1 = parse_entry(ebr[446:462], base_lba=current)
                if e1:
                    name = LOGICAL_EXt4_NAMES[vol_idx] if vol_idx < len(LOGICAL_EXt4_NAMES) else f"logical_{vol_idx}"
                    e1["name"] = name
                    parts.append(e1)
                    vol_idx += 1

                # Entry 2 (offset 462): next EBR
                e2 = parse_entry(ebr[462:478], base_lba=ext_start)
                if e2 and e2["type_name"] == "extended":
                    current = e2["lba_abs"]
                else:
                    break
    finally:
        f.close()

    # Sort by LBA
    parts.sort(key=lambda p: p["lba_abs"])
    return parts


def main():
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <image_file_or_zip> [--system-only]", file=sys.stderr)
        sys.exit(1)

    img_path = sys.argv[1]
    system_only = "--system-only" in sys.argv

    parts = find_partitions(img_path)

    system_part = next((p for p in parts if p.get("name") == "system"), None)
    system_offset = system_part["lba_abs"] * 512 if system_part else None

    if system_only:
        if system_offset:
            print(system_offset)
        else:
            print("ERROR: system partition not found", file=sys.stderr)
            sys.exit(1)
    else:
        output = {
            "partitions": [
                {
                    "name": p.get("name", "unknown"),
                    "type": p["type_name"],
                    "lba": p["lba_abs"],
                    "offset": p["lba_abs"] * 512,
                    "size_mb": p["size_mb"],
                }
                for p in parts
            ],
            "system_offset": system_offset,
        }
        print(json.dumps(output, indent=2))


if __name__ == "__main__":
    main()
