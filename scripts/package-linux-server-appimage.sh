#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "This script must be run on Linux." >&2
  exit 1
fi

APP_NAME="${APP_NAME:-HAFMessengerServer}"
MAIN_JAR="${MAIN_JAR:-server-1.0-SNAPSHOT.jar}"
MAIN_CLASS="${MAIN_CLASS:-com.haf.server.core.Main}"
ICON_PATH="${ICON_PATH:-${PROJECT_ROOT}/client/src/main/resources/images/logo/app_logo.png}"
OUTPUT_DIR="${OUTPUT_DIR:-${PROJECT_ROOT}/server/target/native}"
APPIMAGE_WORK_DIR="${APPIMAGE_WORK_DIR:-${PROJECT_ROOT}/server/target/appimage}"
TOOLS_DIR="${TOOLS_DIR:-${PROJECT_ROOT}/server/target/tools}"
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

if ! command -v zip >/dev/null 2>&1; then
  echo "zip is required but was not found in PATH." >&2
  exit 1
fi

echo "Building shared and server modules..."
BUILD_ARGS=(-pl shared,server -am package)
if [[ "${SKIP_TESTS}" == "true" ]]; then
  BUILD_ARGS+=(-DskipTests)
fi
"${MVNW}" "${BUILD_ARGS[@]}"

INPUT_DIR="${APPIMAGE_WORK_DIR}/input"
rm -rf "${APPIMAGE_WORK_DIR}"
mkdir -p "${INPUT_DIR}" "${OUTPUT_DIR}" "${TOOLS_DIR}"

SERVER_JAR_PATH="${PROJECT_ROOT}/server/target/${MAIN_JAR}"
SHARED_JAR_PATH="${PROJECT_ROOT}/shared/target/haf-shared.jar"

if [[ ! -f "${SERVER_JAR_PATH}" ]]; then
  echo "Expected server jar not found: ${SERVER_JAR_PATH}" >&2
  echo "Set MAIN_JAR explicitly if your build output name is different." >&2
  exit 1
fi

if [[ ! -f "${SHARED_JAR_PATH}" ]]; then
  echo "Expected shared jar not found: ${SHARED_JAR_PATH}" >&2
  exit 1
fi

cp "${SERVER_JAR_PATH}" "${INPUT_DIR}/${MAIN_JAR}"

# jpackage classpath mode can silently omit the launcher for modular jars.
# Strip module-info from the staged copy only (never from source artifacts).
if jar tf "${INPUT_DIR}/${MAIN_JAR}" | grep -q '^module-info.class$'; then
  zip -d "${INPUT_DIR}/${MAIN_JAR}" module-info.class >/dev/null
fi

echo "Copying runtime dependencies..."
DEP_ARGS=(
  -f "${PROJECT_ROOT}/server/pom.xml"
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

mkdir -p "${APP_DIR}/resources/config"
cp "${PROJECT_ROOT}/server/src/main/resources/dist/server-launcher.sh" "${APP_DIR}/resources/server-launcher.sh"
cp "${PROJECT_ROOT}/server/src/main/resources/dist/server.env.template" "${APP_DIR}/resources/server.env.template"
cp "${PROJECT_ROOT}/server/src/main/resources/config/server.p12" "${APP_DIR}/resources/config/server.p12"
cp "${PROJECT_ROOT}/server/src/main/resources/config/mysql-truststore.p12" "${APP_DIR}/resources/config/mysql-truststore.p12"
cp "${PROJECT_ROOT}/server/src/main/resources/config/admin_private_key.txt" "${APP_DIR}/resources/config/admin_private_key.txt"
chmod +x "${APP_DIR}/resources/server-launcher.sh"

cat > "${APP_DIR}/AppRun" <<EOF_APPRUN
#!/usr/bin/env bash
set -euo pipefail
HERE="\$(CDPATH= cd -- "\$(dirname -- "\$0")" && pwd)"
export APP_DIR="\${HERE}"
export APP_NAME="${APP_NAME}"
exec "\${HERE}/resources/server-launcher.sh" "\$@"
EOF_APPRUN
chmod +x "${APP_DIR}/AppRun"

if [[ -f "${APP_DIR}/lib/${APP_NAME}.png" ]]; then
  cp "${APP_DIR}/lib/${APP_NAME}.png" "${APP_DIR}/${APP_NAME}.png"
else
  cp "${ICON_PATH}" "${APP_DIR}/${APP_NAME}.png"
fi
ln -sfn "${APP_NAME}.png" "${APP_DIR}/.DirIcon"

cat > "${APP_DIR}/${APP_NAME}.desktop" <<EOF_DESKTOP
[Desktop Entry]
Type=Application
Name=${APP_NAME}
Comment=HAF Messenger server with Dev Tunnel host
Exec=AppRun
Icon=${APP_NAME}
Categories=Network;Utility;
Terminal=true
EOF_DESKTOP

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
    echo "Downloading appimagetool..." >&2
    curl -fL "${APPIMAGE_TOOL_URL}" -o "${downloaded_tool}"
    chmod +x "${downloaded_tool}"
  fi
  echo "${downloaded_tool}"
}

APPIMAGETOOL_BIN="$(resolve_appimagetool)"

echo "Building AppImage..."
ARCH="${APPIMAGE_ARCH}" "${APPIMAGETOOL_BIN}" "${APP_DIR}" "${APPIMAGE_PATH}"
chmod +x "${APPIMAGE_PATH}"

echo "Server AppImage created:"
echo "  ${APPIMAGE_PATH}"
