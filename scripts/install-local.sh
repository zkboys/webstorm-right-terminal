#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEBSTORM_APP="${WEBSTORM_APP:-/Applications/WebStorm.app}"
PRODUCT_INFO="$WEBSTORM_APP/Contents/Resources/product-info.json"
PLUGIN_ID="right-terminal"

"$ROOT_DIR/scripts/build-plugin.sh" >/tmp/right-terminal-build.log
cat /tmp/right-terminal-build.log

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to detect WebStorm config directory." >&2
  exit 1
fi

DATA_DIR="$(jq -r '.dataDirectoryName' "$PRODUCT_INFO")"
PLUGINS_DIR="$HOME/Library/Application Support/JetBrains/$DATA_DIR/plugins"
TARGET_DIR="$PLUGINS_DIR/$PLUGIN_ID"

mkdir -p "$PLUGINS_DIR"
rm -rf "$TARGET_DIR"
cp -R "$ROOT_DIR/build/package/$PLUGIN_ID" "$TARGET_DIR"

echo "Installed to: $TARGET_DIR"
echo "Restart WebStorm to load the plugin."
