#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "This script must be run on Linux." >&2
  exit 1
fi

APP_NAME="${APP_NAME:-HAFMessenger}"
MAIN_JAR="${MAIN_JAR:-haf-client.jar}"
MAIN_CLASS="${MAIN_CLASS:-com.haf.client.core.Launcher}"
ICON_PATH="${ICON_PATH:-${PROJECT_ROOT}/client/src/main/resources/images/logo/app_logo.png}"
OUTPUT_DIR="${OUTPUT_DIR:-${PROJECT_ROOT}/client/target/native}"
APPIMAGE_WORK_DIR="${APPIMAGE_WORK_DIR:-${PROJECT_ROOT}/client/target/appimage}"
TOOLS_DIR="${TOOLS_DIR:-${PROJECT_ROOT}/client/target/tools}"
MVNW="${MVNW:-${PROJECT_ROOT}/mvnw}"
SKIP_TESTS="${SKIP_TESTS:-true}"
APPIMAGE_TOOL_URL="${APPIMAGE_TOOL_URL:-https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage}"
APPIMAGE_TOOL="${APPIMAGE_TOOL:-}"

ARCH_RAW="$(uname -m)"
case "${ARCH_RAW}" in
  x86_64|amd64)
    APPIMAGE_ARCH="x86_64"
    ;;
  aarch64|arm64)
    APPIMAGE_ARCH="aarch64"
    ;;
  *)
    echo "Unsupported architecture: ${ARCH_RAW}" >&2
    exit 1
    ;;
esac

if [[ ! -x "${MVNW}" ]]; then
  echo "Maven wrapper not found or not executable: ${MVNW}" >&2
  exit 1
fi

if [[ ! -f "${ICON_PATH}" ]]; then
  echo "Icon file not found: ${ICON_PATH}" >&2
  exit 1
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage is required but was not found in PATH." >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required but was not found in PATH." >&2
  exit 1
fi

echo "Building shared and client modules..."
BUILD_ARGS=(-pl shared,client -am package)
if [[ "${SKIP_TESTS}" == "true" ]]; then
  BUILD_ARGS+=(-DskipTests)
fi
"${MVNW}" "${BUILD_ARGS[@]}"

INPUT_DIR="${APPIMAGE_WORK_DIR}/input"
rm -rf "${APPIMAGE_WORK_DIR}"
mkdir -p "${INPUT_DIR}" "${OUTPUT_DIR}" "${TOOLS_DIR}"

CLIENT_JAR_PATH="${PROJECT_ROOT}/client/target/${MAIN_JAR}"
SHARED_JAR_PATH="${PROJECT_ROOT}/shared/target/haf-shared.jar"

if [[ ! -f "${CLIENT_JAR_PATH}" ]]; then
  echo "Expected client jar not found: ${CLIENT_JAR_PATH}" >&2
  exit 1
fi

if [[ ! -f "${SHARED_JAR_PATH}" ]]; then
  echo "Expected shared jar not found: ${SHARED_JAR_PATH}" >&2
  exit 1
fi

cp "${CLIENT_JAR_PATH}" "${INPUT_DIR}/${MAIN_JAR}"

echo "Copying runtime dependencies..."
DEP_ARGS=(
  -f "${PROJECT_ROOT}/client/pom.xml"
  dependency:copy-dependencies
  -DincludeScope=runtime
  -DoutputDirectory=target/appimage/input
)
if [[ "${SKIP_TESTS}" == "true" ]]; then
  DEP_ARGS+=(-DskipTests)
fi
"${MVNW}" "${DEP_ARGS[@]}"

# Ensure the AppImage always contains the freshly built local shared module.
rm -f "${INPUT_DIR}"/shared-*.jar
cp "${SHARED_JAR_PATH}" "${INPUT_DIR}/shared-1.0-SNAPSHOT.jar"

APP_IMAGE_DIR="${OUTPUT_DIR}/${APP_NAME}"
APP_DIR="${OUTPUT_DIR}/${APP_NAME}.AppDir"
APPIMAGE_PATH="${OUTPUT_DIR}/${APP_NAME}-${APPIMAGE_ARCH}.AppImage"

rm -rf "${APP_IMAGE_DIR}" "${APP_DIR}" "${APPIMAGE_PATH}"

echo "Creating Linux app image with jpackage..."
jpackage \
  --type app-image \
  --name "${APP_NAME}" \
  --dest "${OUTPUT_DIR}" \
  --input "${INPUT_DIR}" \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --icon "${ICON_PATH}" \
  --java-options "--enable-native-access=ALL-UNNAMED"

if [[ ! -d "${APP_IMAGE_DIR}" ]]; then
  echo "Expected jpackage output not found: ${APP_IMAGE_DIR}" >&2
  exit 1
fi

cp -a "${APP_IMAGE_DIR}" "${APP_DIR}"

cat > "${APP_DIR}/AppRun" <<EOF
#!/bin/sh
HERE="\$(CDPATH= cd -- "\$(dirname -- "\$0")" && pwd)"
exec "\${HERE}/bin/${APP_NAME}" "\$@"
EOF
chmod +x "${APP_DIR}/AppRun"

if [[ -f "${APP_DIR}/lib/${APP_NAME}.png" ]]; then
  cp "${APP_DIR}/lib/${APP_NAME}.png" "${APP_DIR}/${APP_NAME}.png"
else
  cp "${ICON_PATH}" "${APP_DIR}/${APP_NAME}.png"
fi
ln -sfn "${APP_NAME}.png" "${APP_DIR}/.DirIcon"

cat > "${APP_DIR}/${APP_NAME}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=${APP_NAME}
Comment=HAF Secure Messenger client
Exec=AppRun
Icon=${APP_NAME}
Categories=Network;InstantMessaging;
Terminal=false
EOF

resolve_appimagetool() {
  if [[ -n "${APPIMAGE_TOOL}" ]]; then
    if command -v "${APPIMAGE_TOOL}" >/dev/null 2>&1; then
      command -v "${APPIMAGE_TOOL}"
      return 0
    fi
    if [[ -x "${APPIMAGE_TOOL}" ]]; then
      echo "${APPIMAGE_TOOL}"
      return 0
    fi
    echo "Configured APPIMAGE_TOOL is not executable: ${APPIMAGE_TOOL}" >&2
    exit 1
  fi

  if command -v appimagetool >/dev/null 2>&1; then
    command -v appimagetool
    return 0
  fi

  if [[ "${APPIMAGE_ARCH}" != "x86_64" ]]; then
    echo "appimagetool not found in PATH for architecture ${APPIMAGE_ARCH}." >&2
    echo "Install appimagetool and set APPIMAGE_TOOL to its path." >&2
    exit 1
  fi

  local downloaded_tool="${TOOLS_DIR}/appimagetool-x86_64.AppImage"
  if [[ ! -x "${downloaded_tool}" ]]; then
    echo "Downloading appimagetool..."
    curl -fL "${APPIMAGE_TOOL_URL}" -o "${downloaded_tool}"
    chmod +x "${downloaded_tool}"
  fi
  echo "${downloaded_tool}"
}

APPIMAGETOOL_BIN="$(resolve_appimagetool)"

echo "Building AppImage..."
ARCH="${APPIMAGE_ARCH}" "${APPIMAGETOOL_BIN}" "${APP_DIR}" "${APPIMAGE_PATH}"
chmod +x "${APPIMAGE_PATH}"

echo "AppImage created:"
echo "  ${APPIMAGE_PATH}"
