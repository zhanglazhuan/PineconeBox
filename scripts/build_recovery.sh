#!/bin/bash
# ============================================================================
# build_recovery.sh — Build the PineCone recovery initramfs for the target Pi.
#
# Run this ON THE PI after copying the installer files.
# It creates a minimal initramfs (~5 MB) that runs entirely in RAM.
#
# Usage (on the Pi):
#   sudo bash build_recovery.sh [--installer-dir /opt/pinecone-installer]
#
# Output:
#   /boot/firmware/recovery.gz    — initramfs image
#   /boot/firmware/config.txt     — updated with initramfs line
# ============================================================================

set -euo pipefail

INSTALLER_DIR=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --installer-dir) INSTALLER_DIR="$2"; shift 2 ;;
        *) echo "Usage: $0 [--installer-dir <path>]"; exit 1 ;;
    esac
done

# Auto-detect installer directory
if [ -z "$INSTALLER_DIR" ]; then
    for candidate in /opt/pinecone-installer /home/zhang/installer; do
        if [ -f "$candidate/recovery/init" ]; then
            INSTALLER_DIR="$candidate"
            break
        fi
    done
fi
if [ -z "$INSTALLER_DIR" ]; then
    echo "ERROR: installer directory not found. Use --installer-dir <path>"
    exit 1
fi

# Auto-detect boot partition (Bookworm uses /boot/firmware, Bullseye uses /boot)
if [ -d /boot/firmware ]; then
    BOOT="/boot/firmware"
else
    BOOT="/boot"
fi

WORK="/tmp/pinecone-initramfs"

echo "=== PineCone Recovery Initramfs Builder ==="
echo "  Installer: $INSTALLER_DIR"
echo "  Boot:      $BOOT"

# 1. Install prerequisites
echo "[1/5] Installing busybox-static..."
apt-get update -qq
apt-get install -y -qq busybox-static cpio gzip

# 2. Create initramfs directory structure
echo "[2/5] Creating initramfs layout..."
rm -rf "$WORK"
mkdir -p "$WORK"/{bin,boot,rootfs,mnt,proc,sys,dev,etc,opt}

# 3. Copy static busybox (all the tools we need)
echo "[3/5] Copying binaries..."
cp /usr/bin/busybox "$WORK/bin/busybox"
chmod +x "$WORK/bin/busybox"

# Test that busybox is actually static
echo "  busybox $(/usr/bin/busybox | head -1)"

# 4. Copy the recovery init script
echo "[4/5] Installing recovery init script..."
cp "$INSTALLER_DIR/recovery/init" "$WORK/init"
chmod +x "$WORK/init"

# 5. Package into gzipped cpio
echo "[5/5] Building initramfs image..."
cd "$WORK"
find . -print0 | cpio --null -ov --format=newc 2>/dev/null | gzip -9 > "$BOOT/recovery.gz"
SIZE=$(du -h "$BOOT/recovery.gz" | cut -f1)

# 6. Update config.txt to load the initramfs
echo ""
echo "Updating $BOOT/config.txt ..."
CONFIG="$BOOT/config.txt"

# Remove any existing initramfs line to avoid duplicates
sudo sed -i '/^initramfs /d' "$CONFIG" 2>/dev/null || true

# Add initramfs line after the kernel line
echo "initramfs recovery.gz followkernel" | sudo tee -a "$CONFIG" > /dev/null

echo ""
echo "============================================"
echo " Recovery initramfs built successfully!"
echo "   Image: $BOOT/recovery.gz ($SIZE)"
echo "   Config: $BOOT/config.txt updated"
echo "============================================"
echo ""
echo "To trigger recovery mode from the installer, create:"
echo "  $BOOT/pinecone-recovery"
echo ""
echo "Then reboot. The initramfs will detect the flag,"
echo "run the recovery script, and reboot into the new OS."
echo "============================================"

# Cleanup
rm -rf "$WORK"
