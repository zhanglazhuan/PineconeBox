#!/bin/bash
# ============================================================================
# build_installer_img.sh — Build PineCone installer factory SD card image
#
# Usage:
#   sudo bash build_installer_img.sh \
#       --pi-img 2024-03-15-raspios-bookworm-arm64-lite.img \
#       --installer-dir ../installer \
#       --output pinecone-installer-v1.0.img
# ============================================================================

set -euo pipefail

PI_IMG=""
INSTALLER_DIR=""
OUTPUT="pinecone-installer-v1.0.img"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --pi-img)       PI_IMG="$2"; shift 2 ;;
        --installer-dir) INSTALLER_DIR="$2"; shift 2 ;;
        --output)       OUTPUT="$2"; shift 2 ;;
        *) echo "Unknown: $1"; exit 1 ;;
    esac
done

[[ -f "$PI_IMG" ]]         || { echo "ERROR: Pi OS img not found: $PI_IMG"; exit 1; }
[[ -d "$INSTALLER_DIR" ]]  || { echo "ERROR: installer dir not found: $INSTALLER_DIR"; exit 1; }

echo "============================================"
echo " PineCone Installer — Build Factory Image"
echo "============================================"
echo "  Pi OS image:  $PI_IMG"
echo "  Installer:    $INSTALLER_DIR"
echo "  Output:       $OUTPUT"
echo ""

LOOP_DEV=$(losetup -f)
echo "[1/5] Setting up loop device: $LOOP_DEV"
losetup -P "$LOOP_DEV" "$PI_IMG"

echo "[2/5] Mounting partitions..."
mkdir -p /tmp/pinecone-build/{boot,rootfs}
mount "${LOOP_DEV}p1" /tmp/pinecone-build/boot
mount "${LOOP_DEV}p2" /tmp/pinecone-build/rootfs
echo "  /boot  <- ${LOOP_DEV}p1"
echo "  /      <- ${LOOP_DEV}p2"

echo "[3/5] Copying installer + installing dependencies..."
TARGET="/tmp/pinecone-build/rootfs/opt/pinecone-installer"
mkdir -p "$TARGET"
cp -r "$INSTALLER_DIR"/* "$TARGET"/

mount --bind /dev /tmp/pinecone-build/rootfs/dev
mount --bind /proc /tmp/pinecone-build/rootfs/proc
mount --bind /sys /tmp/pinecone-build/rootfs/sys

chroot /tmp/pinecone-build/rootfs apt update
chroot /tmp/pinecone-build/rootfs apt install -y \
    python3-pygame python3-requests unzip e2fsprogs curl gzip

umount /tmp/pinecone-build/rootfs/{dev,proc,sys}

echo "[4/5] Configuring systemd..."
chroot /tmp/pinecone-build/rootfs systemctl disable bluetooth avahi-daemon 2>/dev/null || true

mkdir -p /tmp/pinecone-build/rootfs/etc/systemd/system/getty@tty1.service.d
cat > /tmp/pinecone-build/rootfs/etc/systemd/system/getty@tty1.service.d/autologin.conf << 'EOF'
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin pi --noclear %I $TERM
EOF

cat >> /tmp/pinecone-build/rootfs/home/pi/.bash_profile << 'EOF'
if [ -z "$DISPLAY" ] && [ "$XDG_VTNR" -eq 1 ]; then
    echo "Starting PineCone OS Installer..."
    python3 /opt/pinecone-installer/main.py
fi
EOF

echo "[5/5] Unmounting and creating factory image..."
umount /tmp/pinecone-build/boot
umount /tmp/pinecone-build/rootfs
losetup -d "$LOOP_DEV"

cp "$PI_IMG" "$OUTPUT"

echo ""
echo "============================================"
echo " Factory image created: $OUTPUT"
echo " Size: $(du -h "$OUTPUT" | cut -f1)"
echo "============================================"
echo ""
echo "To flash to SD cards:"
echo "  sudo dd if=$OUTPUT of=/dev/sdX bs=4M status=progress"
