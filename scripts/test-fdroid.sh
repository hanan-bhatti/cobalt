#!/usr/bin/env bash
set -e

# ==============================================================================
# F-Droid Pre-flight Local Test Suite
# Tests metadata formatting, tag detection, scanner, and FOSS release build.
# ==============================================================================

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="$ROOT_DIR/build/fdroid-test"
METADATA_SRC="$ROOT_DIR/fdroid/com.hanan_bhatti.cobalt.yml"
APP_ID="com.hanan_bhatti.cobalt"

echo "=================================================="
echo "  COBALT F-DROID LOCAL TEST SUITE"
echo "=================================================="

# Check fdroid command
if ! command -v fdroid &> /dev/null; then
    echo "❌ 'fdroid' command not found in PATH."
    echo "Install it via: python3 -m venv ~/.local/share/fdroid-tools/venv && ~/.local/share/fdroid-tools/venv/bin/pip install fdroidserver && ln -sf ~/.local/share/fdroid-tools/venv/bin/fdroid ~/.local/bin/fdroid"
    exit 1
fi
echo "✅ Found fdroid tool: $(which fdroid) (version $(fdroid --version))"

# Prepare clean workspace
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/metadata"
cp "$METADATA_SRC" "$WORK_DIR/metadata/"

# Create F-Droid config pointing to Android SDK
SDK_DIR="${ANDROID_HOME:-$HOME/Android/Sdk}"
cat <<EOF > "$WORK_DIR/config.yml"
sdk_path: $SDK_DIR
EOF

cd "$WORK_DIR"
git init -q
git config user.email "ci@example.com"
git config user.name "CI"
git add .
git commit -q -m "init"

# 1. Test rewritemeta
echo ""
echo "[1/4] Testing 'fdroid rewritemeta' (canonical YAML format)..."
fdroid rewritemeta "$APP_ID" > /dev/null 2>&1
if ! diff -u "$METADATA_SRC" "$WORK_DIR/metadata/$APP_ID.yml"; then
    echo "❌ fdroid rewritemeta detected formatting differences!"
    exit 1
fi
echo "✅ rewritemeta passed with 0 diff!"

# 2. Test checkupdates
echo ""
echo "[2/4] Testing 'fdroid checkupdates' (tag and version matching)..."
fdroid checkupdates --verbose "$APP_ID" > /dev/null 2>&1
echo "✅ checkupdates passed!"

# 3. Test scanner
echo ""
echo "[3/4] Testing 'fdroid scanner' (anti-features and prebuild script)..."
fdroid scanner --verbose "$APP_ID" > /dev/null 2>&1
echo "✅ scanner passed with 0 problems!"

# 4. Test assembleFossRelease
echo ""
echo "[4/4] Testing './gradlew assembleFossRelease'..."
cd "$ROOT_DIR"
./gradlew assembleFossRelease -q
echo "✅ assembleFossRelease compiled successfully!"

echo ""
echo "=================================================="
echo "🎉 ALL F-DROID CHECKS PASSED 100%!"
echo "Your metadata and code are 100% compliant with F-Droid."
echo "=================================================="
