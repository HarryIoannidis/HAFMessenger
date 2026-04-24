#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script must be run on macOS." >&2
  exit 1
fi

APP_NAME="${APP_NAME:-HAFMessenger}"
MAIN_JAR="${MAIN_JAR:-haf-client.jar}"
MAIN_CLASS="${MAIN_CLASS:-com.haf.client.core.Launcher}"
OUTPUT_DIR="${OUTPUT_DIR:-${PROJECT_ROOT}/client/target/native}"
PACKAGE_TYPE="${PACKAGE_TYPE:-app-image}"
APP_VERSION="${APP_VERSION:-1.0}"
PACKAGE_WORK_DIR="${PACKAGE_WORK_DIR:-${PROJECT_ROOT}/client/target/macos-package}"
MVNW="${MVNW:-${PROJECT_ROOT}/mvnw}"
SKIP_TESTS="${SKIP_TESTS:-true}"

DEFAULT_ICON_ICNS="${PROJECT_ROOT}/client/src/main/resources/images/logo/app_logo.icns"
if [[ -n "${ICON_PATH:-}" ]]; then
  if [[ ! -f "${ICON_PATH}" ]]; then
    echo "ICON_PATH was provided but file not found: ${ICON_PATH}" >&2
    exit 1
  fi
  ICON_OPTION=(--icon "${ICON_PATH}")
elif [[ -f "${DEFAULT_ICON_ICNS}" ]]; then
  ICON_OPTION=(--icon "${DEFAULT_ICON_ICNS}")
else
  ICON_OPTION=()
  echo "Warning: No .icns icon found at ${DEFAULT_ICON_ICNS}; building without custom icon."
fi

case "${PACKAGE_TYPE}" in
  app-image|dmg|pkg)
    ;;
  *)
    echo "Unsupported PACKAGE_TYPE: ${PACKAGE_TYPE}" >&2
    echo "Use one of: app-image, dmg, pkg" >&2
    exit 1
    ;;
esac

if [[ ! -x "${MVNW}" ]]; then
  echo "Maven wrapper not found or not executable: ${MVNW}" >&2
  exit 1
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage is required but was not found in PATH." >&2
  exit 1
fi

echo "Building shared and client modules..."
BUILD_ARGS=(-pl shared,client -am package)
if [[ "${SKIP_TESTS}" == "true" ]]; then
  BUILD_ARGS+=(-DskipTests)
fi
"${MVNW}" "${BUILD_ARGS[@]}"

INPUT_DIR="${PACKAGE_WORK_DIR}/input"
rm -rf "${PACKAGE_WORK_DIR}"
mkdir -p "${INPUT_DIR}" "${OUTPUT_DIR}"

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
  -DoutputDirectory=target/macos-package/input
)
if [[ "${SKIP_TESTS}" == "true" ]]; then
  DEP_ARGS+=(-DskipTests)
fi
"${MVNW}" "${DEP_ARGS[@]}"

# Ensure the package always contains the freshly built local shared module.
rm -f "${INPUT_DIR}"/shared-*.jar
cp "${SHARED_JAR_PATH}" "${INPUT_DIR}/shared-1.0-SNAPSHOT.jar"

rm -rf "${OUTPUT_DIR}/${APP_NAME}.app" "${OUTPUT_DIR}/${APP_NAME}.dmg" "${OUTPUT_DIR}/${APP_NAME}.pkg"

echo "Packaging ${APP_NAME} (${PACKAGE_TYPE}) with jpackage..."
jpackage \
  --type "${PACKAGE_TYPE}" \
  --name "${APP_NAME}" \
  --app-version "${APP_VERSION}" \
  --dest "${OUTPUT_DIR}" \
  --input "${INPUT_DIR}" \
  --main-jar "${MAIN_JAR}" \
  --main-class "${MAIN_CLASS}" \
  --java-options "--enable-native-access=ALL-UNNAMED" \
  "${ICON_OPTION[@]}"

case "${PACKAGE_TYPE}" in
  app-image)
    OUTPUT_PATH="${OUTPUT_DIR}/${APP_NAME}.app"
    ;;
  dmg)
    OUTPUT_PATH="${OUTPUT_DIR}/${APP_NAME}-${APP_VERSION}.dmg"
    ;;
  pkg)
    OUTPUT_PATH="${OUTPUT_DIR}/${APP_NAME}-${APP_VERSION}.pkg"
    ;;
esac

echo "Package created:"
echo "  ${OUTPUT_PATH}"
