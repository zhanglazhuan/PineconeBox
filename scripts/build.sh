#!/usr/bin/env bash
# ==============================================================================
# build.sh — Build the PineCone Launcher APK (works on Windows Git Bash too)
#
# Usage:
#   bash build.sh              # Build debug APK
#   bash build.sh --release    # Build release APK
#
# Output:
#   launcher/app/build/outputs/apk/{debug,release}/app-{debug,release}.apk
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LAUNCHER_DIR="$(dirname "$SCRIPT_DIR")/launcher"
BUILD_TYPE="${1:-debug}"
BUILD_TYPE="${BUILD_TYPE#--}"

# Auto-detect JAVA_HOME on Windows if not set
if [[ -z "${JAVA_HOME:-}" ]]; then
    # Android Studio bundled JBR
    for candidate in \
        "/c/Program Files/Android/Android Studio/jbr" \
        "/c/Program Files/Java/jdk-17" \
        "/c/Program Files/Java/jdk-11" \
        "/c/Program Files/Eclipse Adoptium/jdk-17.0.0.35-hotspot"; do
        if [[ -d "$candidate" ]]; then
            export JAVA_HOME="$candidate"
            echo "Auto-detected JAVA_HOME=$JAVA_HOME"
            break
        fi
    done
fi

case "$BUILD_TYPE" in
    debug|release) ;;
    *) echo "Usage: bash build.sh [--debug|--release]"; exit 1 ;;
esac

echo "Building Launcher APK ($BUILD_TYPE) ..."
cd "$LAUNCHER_DIR"

# Capitalize first letter for Gradle task name
TASK="assemble$(echo "${BUILD_TYPE:0:1}" | tr '[:lower:]' '[:upper:]')${BUILD_TYPE:1}"
./gradlew "$TASK" 2>&1 | tail -5

# Find output APK
APK_DIR="$LAUNCHER_DIR/app/build/outputs/apk/$BUILD_TYPE"
APK=$(ls "$APK_DIR"/*.apk 2>/dev/null | head -1)

if [[ -z "$APK" ]]; then
    echo "ERROR: APK not found. Build may have failed."
    exit 1
fi

echo ""
echo "============================================"
echo " Build successful!"
echo " APK: $APK"
echo "============================================"
echo ""
echo "Next step — inject into ROM:"
echo "  python $(dirname "$0")/inject_apk.py --apk '$APK'"
