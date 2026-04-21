#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_NAME="${APP_NAME:-HAFMessenger}"
MAIN_JAR="${MAIN_JAR:-haf-client.jar}"
MAIN_CLASS="${MAIN_CLASS:-com.haf.client.core.Launcher}"
PACKAGE_TYPE="${PACKAGE_TYPE:-app-image}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/client/target/native}"
STAGING_DIR="$ROOT_DIR/client/target/package-input"
RUNTIME_DIR="$ROOT_DIR/client/target/runtime"
ICON_DEFAULT="$ROOT_DIR/client/src/main/resources/images/logo/app_logo.icns"
ICON_PATH="${ICON_PATH:-$ICON_DEFAULT}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script builds a native macOS app and must run on macOS."
  exit 1
fi

for cmd in java jlink jpackage; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd"
    exit 1
  fi
done

if [[ ! -x "$ROOT_DIR/mvnw" ]]; then
  echo "Could not find executable Maven wrapper at: $ROOT_DIR/mvnw"
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_HOME="$(/usr/libexec/java_home)"
fi

if [[ ! -d "$JAVA_HOME/jmods" ]]; then
  echo "JAVA_HOME does not point to a full JDK (missing jmods): $JAVA_HOME"
  exit 1
fi

echo "[1/5] Build client + shared jars..."
if [[ -f "$ROOT_DIR/pom.xml" ]] && grep -q "<module>client</module>" "$ROOT_DIR/pom.xml"; then
  "$ROOT_DIR/mvnw" -f "$ROOT_DIR/pom.xml" -pl client -am -DskipTests clean package
else
  echo "Reactor module 'client' not found in root pom.xml; using direct module builds."
  if [[ -f "$ROOT_DIR/shared/pom.xml" ]]; then
    "$ROOT_DIR/mvnw" -f "$ROOT_DIR/shared/pom.xml" -DskipTests clean install
  fi
  "$ROOT_DIR/mvnw" -f "$ROOT_DIR/client/pom.xml" -DskipTests clean package
fi

echo "[2/5] Copy client runtime dependencies..."
"$ROOT_DIR/mvnw" -f "$ROOT_DIR/client/pom.xml" -DskipTests dependency:copy-dependencies \
  -DincludeScope=runtime \
  -DoutputDirectory=target/deps

echo "[3/5] Prepare jpackage input folder..."
rm -rf "$STAGING_DIR" "$RUNTIME_DIR" "$OUTPUT_DIR"
mkdir -p "$STAGING_DIR" "$OUTPUT_DIR"
cp "$ROOT_DIR/client/target/$MAIN_JAR" "$STAGING_DIR/"
cp "$ROOT_DIR/client/target/deps/"*.jar "$STAGING_DIR/"

# Use the freshly built reactor jar for the shared module.
rm -f "$STAGING_DIR"/shared-*.jar
cp "$ROOT_DIR/shared/target/haf-shared.jar" "$STAGING_DIR/"

# Remove JavaFX aggregator jars (without OS classifier) to avoid jlink/jpackage confusion.
shopt -s nullglob
for jar in "$STAGING_DIR"/javafx-*.jar; do
  jar_name="$(basename "$jar")"
  if [[ "$jar_name" =~ ^javafx-(base|controls|fxml|graphics)-[0-9][0-9.]*\.jar$ ]]; then
    rm -f "$jar"
  fi
done
shopt -u nullglob

echo "[4/5] Build custom runtime with jlink..."
jlink \
  --module-path "$JAVA_HOME/jmods" \
  --add-modules ALL-MODULE-PATH \
  --output "$RUNTIME_DIR" \
  --strip-debug \
  --compress=2 \
  --no-header-files \
  --no-man-pages

echo "[5/5] Build native app image with jpackage..."
jpackage_args=(
  --type "$PACKAGE_TYPE"
  --name "$APP_NAME"
  --input "$STAGING_DIR"
  --dest "$OUTPUT_DIR"
  --main-jar "$MAIN_JAR"
  --main-class "$MAIN_CLASS"
  --runtime-image "$RUNTIME_DIR"
  --java-options "--enable-native-access=ALL-UNNAMED"
  --mac-package-identifier "com.haf.messenger"
)

if [[ -f "$ICON_PATH" ]]; then
  jpackage_args+=(--icon "$ICON_PATH")
else
  echo "Icon not found at '$ICON_PATH'. Building with default icon."
fi

jpackage "${jpackage_args[@]}"

if [[ "$PACKAGE_TYPE" == "app-image" ]]; then
  APP_PATH="$OUTPUT_DIR/${APP_NAME}.app"
  echo
  echo "Done."
  echo "App path: $APP_PATH"
else
  echo
  echo "Done."
  echo "Output folder: $OUTPUT_DIR"
fi
