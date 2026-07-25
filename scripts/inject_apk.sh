#!/usr/bin/env bash
# ==============================================================================
# inject_apk.sh — Inject PineCone Launcher APK into a LineageOS ROM disk image
#
# Usage:
#   sudo ./inject_apk.sh <rom_zip> <apk_path> [output_zip]
#
# Arguments:
#   rom_zip    : Path to the LineageOS .zip (contains the .img disk image)
#   apk_path   : Path to the compiled PineCone Launcher APK
#   output_zip : Optional output path (default: <rom>-pinecone.zip)
#
# Requirements:
#   - Linux with root (losetup / mount -o loop,offset=N)
#   - Python 3 (for partition table parsing)
#   - unzip, e2fsck (optional)
#
# The ROM .img is a full MBR disk image (not a sparse system.img).
# System partition is located by parsing the MBR+EBR partition table.
# ==============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()  { echo -e "${RED}[ERR]${NC}   $*" >&2; }

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ---- Check root ----
check_root() {
    if [[ $EUID -ne 0 ]]; then
        err "Need root for mount. Run: sudo $0 $*"
        exit 1
    fi
}

# ---- Find system partition offset using Python ----
# Falls back to hardcoded known offset for KonstaKANG RPi5 ATV ROMs
find_system_offset() {
    local img="$1"

    # Try Python detection script first
    local py_script="$SCRIPT_DIR/detect_partitions.py"
    if [[ -f "$py_script" ]] && command -v python3 &>/dev/null; then
        local offset
        offset=$(python3 "$py_script" "$img" --system-only 2>/dev/null) || true
        if [[ -n "$offset" && "$offset" =~ ^[0-9]+$ && "$offset" -gt 0 ]]; then
            echo "$offset"
            return 0
        fi
    fi

    # Try 'python' (some distros)
    if [[ -f "$py_script" ]] && command -v python &>/dev/null; then
        local offset
        offset=$(python "$py_script" "$img" --system-only 2>/dev/null) || true
        if [[ -n "$offset" && "$offset" =~ ^[0-9]+$ && "$offset" -gt 0 ]]; then
            echo "$offset"
            return 0
        fi
    fi

    # Fallback: known offset for KonstaKANG LineageOS 23.2 RPi5 ATV image
    # system: ext4 at LBA=266240 -> byte offset = 266240 * 512 = 136314880
    local fallback=136314880
    warn "Python not available; using fallback offset: $fallback"
    warn "This only works for: lineage-23.2-*-KonstaKANG-rpi5-atv.img"
    echo "$fallback"
}

# ---- Remove competing launchers from the mounted system ----
remove_competing_launchers() {
    local mnt="$1"
    local launchers=(
        "Trebuchet"           # LineageOS default
        "Launcher3"           # AOSP Launcher3
        "Launcher3QuickStep"  # AOSP Launcher3 + QuickStep
        "LeanbackLauncher"    # Android TV default
        "TvLauncher"          # Alternative TV launcher
        "CustomTvLauncher"    # Another TV variant
        "LegacyLauncher"      # Old AOSP
        "Catapult"            # LineageOS TV home
    )

    log "Scanning for competing launchers ..."
    local found_any=false

    for dir in "$mnt/system/app" "$mnt/system/priv-app"; do
        if [[ ! -d "$dir" ]]; then continue; fi
        for name in "${launchers[@]}"; do
            local matches
            matches=$(find "$dir" -maxdepth 1 -iname "${name}*" -type d 2>/dev/null) || true
            if [[ -n "$matches" ]]; then
                while IFS= read -r match; do
                    log "Removing: $match"
                    rm -rf "$match"
                    found_any=true
                done <<< "$matches"
            fi
        done
    done

    if $found_any; then
        log "Competing launchers removed. PineCone will be the sole HOME app."
    else
        log "No competing launchers found (already clean)."
    fi
}

# ---- Inject APK and set permissions ----
inject_apk_to_system() {
    local mnt="$1"
    local apk_path="$2"
    local apk_name="PineConeLauncher.apk"
    local target_dir="$mnt/system/app/PineConeLauncher"

    log "Injecting APK -> $target_dir/"
    mkdir -p "$target_dir"
    cp "$apk_path" "$target_dir/$apk_name"

    chown -R 0:0 "$target_dir"
    chmod 755 "$target_dir"
    chmod 644 "$target_dir/$apk_name"

    # SELinux — try chcon; continue on failure
    if command -v chcon &>/dev/null; then
        chcon -R -u system_u -r object_r -t system_file:s0 "$target_dir" 2>/dev/null || {
            warn "chcon failed; SELinux context may need fix on first boot."
        }
    fi

    log "APK installed. Listing:"
    ls -la "$target_dir/"
}

# ---- Main ----
main() {
    if [[ $# -lt 2 ]]; then
        echo "Usage: $0 <rom_zip> <apk_path> [output_zip]"
        echo ""
        echo "Example:"
        echo "  sudo $0 ../baseOS/lineage-*-atv.zip ../launcher/app/build/outputs/apk/debug/app-debug.apk"
        exit 1
    fi

    check_root "$@"

    local rom_zip;   rom_zip=$(realpath "$1")
    local apk_path;  apk_path=$(realpath "$2")
    local output_zip="${3:-${rom_zip%.zip}-pinecone.zip}"

    [[ -f "$rom_zip" ]] || { err "ROM not found: $rom_zip"; exit 1; }
    [[ -f "$apk_path" ]] || { err "APK not found: $apk_path"; exit 1; }

    log "ROM    : $rom_zip"
    log "APK    : $apk_path"
    log "Output : $output_zip"

    local work_dir
    work_dir=$(mktemp -d -t pinecone-inject-XXXXXX)
    trap 'log "Cleaning up..."; umount "${work_dir:-}/mnt" 2>/dev/null || true; rm -rf "${work_dir:-}"' EXIT

    # ---- Step 1: Extract .img from zip ----
    log "[1/5] Extracting .img from ROM zip ..."
    unzip -o "$rom_zip" -d "$work_dir/rom" | tail -1
    local img_file
    img_file=$(ls "$work_dir/rom"/*.img 2>/dev/null | head -1)
    [[ -n "$img_file" ]] || { err "No .img found in zip"; exit 1; }
    log "  -> $(basename "$img_file")"

    # ---- Step 2: Detect system partition offset ----
    log "[2/5] Detecting system partition offset ..."
    local sys_offset
    sys_offset=$(find_system_offset "$img_file")
    log "  -> offset = $sys_offset bytes ($((sys_offset / 1048576)) MB)"

    # ---- Step 3: Mount system partition ----
    log "[3/5] Mounting system partition ..."
    mkdir -p "$work_dir/mnt"
    # Try loop mount with explicit offset
    if mount -o loop,offset="$sys_offset" "$img_file" "$work_dir/mnt" 2>/dev/null; then
        log "  -> mounted directly (mount -o loop,offset=...)"
    else
        # Fall back to losetup + mount
        warn "Direct mount failed; trying losetup ..."
        local loop_dev
        loop_dev=$(losetup -f --show -o "$sys_offset" "$img_file")
        e2fsck -p "$loop_dev" 2>/dev/null || true
        mount -t ext4 "$loop_dev" "$work_dir/mnt"
        log "  -> mounted via $loop_dev"
    fi
    log "  -> mount point: $work_dir/mnt"

    # ---- Step 4: Inject APK + remove competing launchers ----
    log "[4/5] Injecting APK and removing stock launchers ..."
    inject_apk_to_system "$work_dir/mnt" "$apk_path"
    remove_competing_launchers "$work_dir/mnt"

    # ---- Step 5: Unmount and repack ----
    log "[5/5] Unmounting and repacking ..."
    umount "$work_dir/mnt"
    # Detach loop device if used
    if [[ -n "${loop_dev:-}" ]]; then
        losetup -d "$loop_dev" 2>/dev/null || true
    fi
    log "  -> unmounted"

    # Repack: zip on WSL's own filesystem first, then copy to final destination.
    #   -1 = fastest compression (the 15GB .img is mostly incompressible raw data)
    #   Cross-filesystem /mnt writes via 9p are slow, so we zip locally then cp.
    local tmp_zip="$work_dir/pinecone-os.zip"
    (cd "$work_dir/rom" && zip -1 "$tmp_zip" ./*.img)
    cp "$tmp_zip" "$output_zip"
    log "  -> repacked: $output_zip"

    log "Done! PineCone Launcher pre-installed as system app."
}

main "$@"
