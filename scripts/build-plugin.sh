#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WEBSTORM_APP="${WEBSTORM_APP:-/Applications/WebStorm.app}"
WEBSTORM_CONTENTS="$WEBSTORM_APP/Contents"
PRODUCT_INFO="$WEBSTORM_CONTENTS/Resources/product-info.json"
JBR_HOME="$WEBSTORM_CONTENTS/jbr/Contents/Home"
JAVAC="${JAVAC:-$JBR_HOME/bin/javac}"
JAR="${JAR:-jar}"
PLUGIN_ID="right-terminal"
BUILD_DIR="$ROOT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes"
PACKAGE_DIR="$BUILD_DIR/package/$PLUGIN_ID"
ZIP_FILE="$BUILD_DIR/distributions/$PLUGIN_ID.zip"

if [[ ! -d "$WEBSTORM_CONTENTS" ]]; then
  echo "WebStorm not found: $WEBSTORM_APP" >&2
  echo "Set WEBSTORM_APP=/path/to/WebStorm.app and rerun." >&2
  exit 1
fi

if [[ ! -x "$JAVAC" ]]; then
  echo "JBR javac not found: $JAVAC" >&2
  echo "Set JAVAC=/path/to/javac, or install a WebStorm build with bundled JBR." >&2
  exit 1
fi

if ! command -v "$JAR" >/dev/null 2>&1 && ! command -v zip >/dev/null 2>&1; then
  echo "jar command not found: $JAR" >&2
  echo "Set JAR=/path/to/jar, install a JDK that provides jar, or install zip." >&2
  exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$CLASSES_DIR" "$PACKAGE_DIR/lib" "$BUILD_DIR/resources/META-INF" "$(dirname "$ZIP_FILE")"

find "$WEBSTORM_CONTENTS/lib" "$WEBSTORM_CONTENTS/plugins/terminal/lib" -type f -name '*.jar' > "$BUILD_DIR/classpath.txt"
CLASSPATH="$(paste -sd: "$BUILD_DIR/classpath.txt")"

find "$ROOT_DIR/src/main/java" -type f -name '*.java' > "$BUILD_DIR/sources.txt"
"$JAVAC" --release 21 -encoding UTF-8 -classpath "$CLASSPATH" -d "$CLASSES_DIR" @"$BUILD_DIR/sources.txt"

cp "$ROOT_DIR/src/main/resources/META-INF/plugin.xml" "$BUILD_DIR/resources/META-INF/plugin.xml"
if "$JAR" --create --file "$PACKAGE_DIR/lib/right-terminal.jar" -C "$CLASSES_DIR" . -C "$BUILD_DIR/resources" . >/dev/null 2>&1; then
  :
elif command -v zip >/dev/null 2>&1; then
  (
    cd "$CLASSES_DIR"
    zip -qr "$PACKAGE_DIR/lib/right-terminal.jar" .
    cd "$BUILD_DIR/resources"
    zip -qr "$PACKAGE_DIR/lib/right-terminal.jar" .
  )
else
  echo "Failed to create plugin jar with $JAR, and zip is not available." >&2
  exit 1
fi

(
  cd "$BUILD_DIR/package"
  zip -qr "$ZIP_FILE" "$PLUGIN_ID"
)

if command -v jq >/dev/null 2>&1 && [[ -f "$PRODUCT_INFO" ]]; then
  echo "Built for $(jq -r '.name + " " + .version + " (" + .buildNumber + ")"' "$PRODUCT_INFO")"
fi
echo "$ZIP_FILE"
