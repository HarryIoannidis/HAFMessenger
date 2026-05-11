#!/usr/bin/env bash
set -euo pipefail

APP_NAME="${APP_NAME:-HAFMessengerServer}"
APP_DIR="${APP_DIR:-$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)}"
SERVER_BIN="${APP_DIR}/bin/${APP_NAME}"

STATE_ROOT="${XDG_CONFIG_HOME:-${HOME}/.config}/hafmessenger-server"
CONFIG_DIR="${STATE_ROOT}/config"
RUNTIME_DIR="${STATE_ROOT}/runtime"
LOG_DIR="${STATE_ROOT}/logs"
BUNDLED_CONFIG_DIR="${APP_DIR}/resources/config"
BUNDLED_ENV_TEMPLATE="${APP_DIR}/resources/server.env.template"

SERVER_ENV_FILE="${CONFIG_DIR}/variables.env"
HTTPS_DEVTUNNEL_PORT="${HAF_DEVTUNNEL_HTTPS_PORT:-${DEVTUNNEL_PORT:-8443}}"
WS_DEVTUNNEL_PORT="${HAF_DEVTUNNEL_WS_PORT:-8444}"
DEFAULT_WS_PATH="/ws/v1/realtime"
DEVTUNNEL_PROTOCOL="${DEVTUNNEL_PROTOCOL:-https}"
DEVTUNNEL_ID="${HAF_DEVTUNNEL_ID:-hafmessenger-realtime}"
TUNNEL_META_FILE="${RUNTIME_DIR}/devtunnel.env"
TUNNEL_HOST_LOG="${RUNTIME_DIR}/devtunnel-host.log"
LOCK_DIR="${RUNTIME_DIR}/server.lock"
LOCK_PID_FILE="${LOCK_DIR}/pid"
# Avoid killing the AppImage parent process directly; doing so can unmount /tmp/.mount_* mid-start.
AUTO_KILL_PATTERN="${HAF_AUTO_KILL_PATTERN:-server-launcher.sh|devtunnel host hafmessenger-|/tmp/.mount_HAFMes.*/bin/HAFMessengerServer|com.haf.server.core.Main}"

prepend_path_if_dir() {
  local dir="$1"
  if [[ -d "${dir}" && ":${PATH}:" != *":${dir}:"* ]]; then
    PATH="${dir}:${PATH}"
  fi
}

bootstrap_path_for_gui_launches() {
  prepend_path_if_dir "${HOME}/bin"
  prepend_path_if_dir "${HOME}/.local/bin"
  prepend_path_if_dir "/usr/local/bin"
  prepend_path_if_dir "/usr/bin"
  prepend_path_if_dir "/bin"
  export PATH
}

launch_in_terminal_window() {
  local script_path
  script_path="$(readlink -f "${BASH_SOURCE[0]}")"

  if command -v x-terminal-emulator >/dev/null 2>&1; then
    env HAF_SERVER_TERMINAL_WRAPPED=1 APP_DIR="${APP_DIR}" APP_NAME="${APP_NAME}" PATH="${PATH}" \
      x-terminal-emulator -e "${script_path}" "$@" >/dev/null 2>&1 &
    return 0
  fi

  if command -v gnome-terminal >/dev/null 2>&1; then
    env HAF_SERVER_TERMINAL_WRAPPED=1 APP_DIR="${APP_DIR}" APP_NAME="${APP_NAME}" PATH="${PATH}" \
      gnome-terminal -- "${script_path}" "$@" >/dev/null 2>&1 &
    return 0
  fi

  if command -v konsole >/dev/null 2>&1; then
    env HAF_SERVER_TERMINAL_WRAPPED=1 APP_DIR="${APP_DIR}" APP_NAME="${APP_NAME}" PATH="${PATH}" \
      konsole -e "${script_path}" "$@" >/dev/null 2>&1 &
    return 0
  fi

  if command -v xfce4-terminal >/dev/null 2>&1; then
    env HAF_SERVER_TERMINAL_WRAPPED=1 APP_DIR="${APP_DIR}" APP_NAME="${APP_NAME}" PATH="${PATH}" \
      xfce4-terminal -e "${script_path}" >/dev/null 2>&1 &
    return 0
  fi

  if command -v xterm >/dev/null 2>&1; then
    env HAF_SERVER_TERMINAL_WRAPPED=1 APP_DIR="${APP_DIR}" APP_NAME="${APP_NAME}" PATH="${PATH}" \
      xterm -e "${script_path}" "$@" >/dev/null 2>&1 &
    return 0
  fi

  return 1
}

ensure_terminal_window() {
  if [[ "${HAF_SERVER_TERMINAL_WRAPPED:-0}" == "1" ]]; then
    return 0
  fi
  if [[ -t 1 ]]; then
    return 0
  fi
  if [[ -z "${DISPLAY:-}" && -z "${WAYLAND_DISPLAY:-}" ]]; then
    return 0
  fi

  if launch_in_terminal_window "$@"; then
    exit 0
  fi

  echo "No terminal emulator detected; continuing without auto-open terminal." >&2
}

collect_protected_pids() {
  local protected=" $$ "
  local probe_pid="$$"
  local parent_pid=""

  # Protect current script and its process ancestors from self-termination.
  for _ in {1..32}; do
    parent_pid="$(ps -o ppid= -p "${probe_pid}" 2>/dev/null | tr -d '[:space:]')"
    if [[ -z "${parent_pid}" || "${parent_pid}" == "0" || "${parent_pid}" == "1" ]]; then
      break
    fi
    protected="${protected}${parent_pid} "
    probe_pid="${parent_pid}"
  done

  printf '%s\n' "${protected}"
}

is_protected_pid() {
  local pid="$1"
  local protected_set="$2"
  [[ " ${protected_set} " == *" ${pid} "* ]]
}

kill_stale_processes_on_start() {
  local protected_set
  protected_set="$(collect_protected_pids)"

  local stale_pids=()
  local pid=""
  while IFS= read -r pid; do
    [[ -z "${pid}" ]] && continue
    if is_protected_pid "${pid}" "${protected_set}"; then
      continue
    fi
    stale_pids+=("${pid}")
  done < <(pgrep -f "${AUTO_KILL_PATTERN}" 2>/dev/null || true)

  if (( ${#stale_pids[@]} == 0 )); then
    return 0
  fi

  echo "Stopping stale HAF server/tunnel processes from previous runs..."
  kill "${stale_pids[@]}" >/dev/null 2>&1 || true
  sleep 1

  local stubborn_pids=()
  for pid in "${stale_pids[@]}"; do
    if kill -0 "${pid}" >/dev/null 2>&1; then
      stubborn_pids+=("${pid}")
    fi
  done

  if (( ${#stubborn_pids[@]} > 0 )); then
    kill -9 "${stubborn_pids[@]}" >/dev/null 2>&1 || true
  fi

  # If a stale launcher was killed abruptly, its lock directory may remain.
  rm -rf "${LOCK_DIR}" >/dev/null 2>&1 || true
}

resolve_server_env_value() {
  local env_key="$1"
  local default_value="$2"
  if [[ ! -f "${SERVER_ENV_FILE}" ]]; then
    printf '%s\n' "${default_value}"
    return 0
  fi

  local configured_value
  configured_value="$(sed -n "s/^[[:space:]]*${env_key}[[:space:]]*=[[:space:]]*//p" "${SERVER_ENV_FILE}" | tail -n 1)"
  configured_value="${configured_value%%#*}"
  configured_value="${configured_value%\"}"
  configured_value="${configured_value#\"}"
  configured_value="${configured_value%\'}"
  configured_value="${configured_value#\'}"
  configured_value="$(printf '%s' "${configured_value}" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"

  if [[ -n "${configured_value}" ]]; then
    printf '%s\n' "${configured_value}"
    return 0
  fi

  printf '%s\n' "${default_value}"
}

resolve_server_env_port() {
  local env_key="$1"
  local default_port="$2"
  local configured_port
  configured_port="$(resolve_server_env_value "${env_key}" "${default_port}")"
  configured_port="${configured_port%%#*}"
  configured_port="${configured_port//\"/}"
  configured_port="${configured_port//\'/}"
  configured_port="${configured_port//[[:space:]]/}"

  if [[ "${configured_port}" =~ ^[0-9]+$ ]] && (( configured_port >= 1 && configured_port <= 65535 )); then
    printf '%s\n' "${configured_port}"
    return 0
  fi

  printf '%s\n' "${default_port}"
}

find_listening_pid_for_port() {
  local port="$1"
  local pid=""

  if command -v lsof >/dev/null 2>&1; then
    pid="$(lsof -nP -iTCP:"${port}" -sTCP:LISTEN -t 2>/dev/null | head -n 1 || true)"
    if [[ -n "${pid}" ]]; then
      printf '%s\n' "${pid}"
      return 0
    fi
  fi

  if command -v ss >/dev/null 2>&1; then
    pid="$(ss -ltnp 2>/dev/null | awk -v p=":${port}" '$4 ~ p"$" {
      if (match($0, /pid=[0-9]+/)) {
        print substr($0, RSTART + 4, RLENGTH - 4)
        exit
      }
    }')"
    if [[ -n "${pid}" ]]; then
      printf '%s\n' "${pid}"
      return 0
    fi
  fi

  return 1
}

stop_pid_with_escalation() {
  local pid="$1"
  kill "${pid}" >/dev/null 2>&1 || true
  sleep 1
  if kill -0 "${pid}" >/dev/null 2>&1; then
    kill -9 "${pid}" >/dev/null 2>&1 || true
  fi
}

ensure_server_port_available() {
  local port="$1"
  local protected_set
  protected_set="$(collect_protected_pids)"

  local pid=""
  local cmd=""
  while true; do
    pid="$(find_listening_pid_for_port "${port}" || true)"
    if [[ -z "${pid}" ]]; then
      return 0
    fi

    cmd="$(ps -o args= -p "${pid}" 2>/dev/null || true)"
    if is_protected_pid "${pid}" "${protected_set}"; then
      cat <<MSG >&2
Port ${port} is in use by protected PID ${pid}; refusing to self-terminate it.
Command: ${cmd}
Stop it manually, then retry.
MSG
      exit 1
    fi

    echo "Stopping process on port ${port} (PID ${pid})..."
    stop_pid_with_escalation "${pid}"
  done
}

acquire_single_instance_lock() {
  if mkdir "${LOCK_DIR}" 2>/dev/null; then
    printf '%s\n' "$$" > "${LOCK_PID_FILE}"
    return 0
  fi

  local running_pid=""
  if [[ -f "${LOCK_PID_FILE}" ]]; then
    running_pid="$(cat "${LOCK_PID_FILE}" 2>/dev/null || true)"
  fi

  if [[ -n "${running_pid}" ]] && kill -0 "${running_pid}" >/dev/null 2>&1; then
    cat <<MSG >&2
HAF server launcher is already running (PID ${running_pid}).
Use the existing terminal window, or stop that process before starting again.
MSG
    exit 1
  fi

  rm -rf "${LOCK_DIR}" || true
  mkdir "${LOCK_DIR}"
  printf '%s\n' "$$" > "${LOCK_PID_FILE}"
}

mkdir -p "${CONFIG_DIR}" "${RUNTIME_DIR}" "${LOG_DIR}"

if [[ ! -x "${SERVER_BIN}" ]]; then
  echo "Server launcher binary not found or not executable: ${SERVER_BIN}" >&2
  exit 1
fi

copy_if_missing() {
  local source="$1"
  local destination="$2"
  if [[ -f "${source}" && ! -f "${destination}" ]]; then
    cp "${source}" "${destination}"
  fi
}

seed_runtime_config() {
  copy_if_missing "${BUNDLED_CONFIG_DIR}/server.p12" "${CONFIG_DIR}/server.p12"
  copy_if_missing "${BUNDLED_CONFIG_DIR}/mysql-truststore.p12" "${CONFIG_DIR}/mysql-truststore.p12"
  copy_if_missing "${BUNDLED_CONFIG_DIR}/admin_private_key.txt" "${CONFIG_DIR}/admin_private_key.txt"

  if [[ ! -f "${SERVER_ENV_FILE}" ]]; then
    if [[ ! -f "${BUNDLED_ENV_TEMPLATE}" ]]; then
      echo "Missing bundled env template: ${BUNDLED_ENV_TEMPLATE}" >&2
      exit 1
    fi

    sed \
      -e "s|__CONFIG_DIR__|${CONFIG_DIR}|g" \
      "${BUNDLED_ENV_TEMPLATE}" > "${SERVER_ENV_FILE}"

    cat <<MSG
Created first-run config file:
  ${SERVER_ENV_FILE}
Review database credentials and secrets before production use.
MSG
  fi
}

require_devtunnel_cli() {
  bootstrap_path_for_gui_launches

  if ! command -v devtunnel >/dev/null 2>&1; then
    echo "Missing devtunnel CLI in PATH. Attempting automatic installation..."
    if ! command -v curl >/dev/null 2>&1; then
      echo "curl is required to auto-install devtunnel." >&2
      exit 1
    fi
    if ! curl -fsSL https://aka.ms/DevTunnelCliInstall | bash; then
      echo "Automatic devtunnel install failed. Install it manually and retry." >&2
      exit 1
    fi
    bootstrap_path_for_gui_launches
    if ! command -v devtunnel >/dev/null 2>&1; then
      echo "devtunnel was installed but still not found in PATH." >&2
      exit 1
    fi
  fi
}

is_devtunnel_logged_in() {
  local status
  status="$(devtunnel user show 2>&1 || true)"
  if printf '%s' "${status}" | grep -qi "Not logged in"; then
    return 1
  fi
  if printf '%s' "${status}" | grep -qi "Logged in as"; then
    return 0
  fi
  return 1
}

ensure_devtunnel_login() {
  if is_devtunnel_logged_in; then
    return 0
  fi

  echo "Dev Tunnel login not found. Starting browser sign-in..."
  if ! devtunnel user login; then
    echo "Browser login failed or was canceled. Falling back to device-code login..."
    devtunnel user login -d
  fi

  if ! is_devtunnel_logged_in; then
    echo "Dev Tunnel login is still missing. Please run: devtunnel user login" >&2
    exit 1
  fi
}

ensure_tunnel_exists() {
  if devtunnel show "${DEVTUNNEL_ID}" >/dev/null 2>&1; then
    return 0
  fi

  echo "Creating persistent Dev Tunnel: ${DEVTUNNEL_ID}"
  devtunnel create "${DEVTUNNEL_ID}" >/dev/null
}

ensure_tunnel_port() {
  local port="$1"
  if devtunnel port show "${DEVTUNNEL_ID}" -p "${port}" >/dev/null 2>&1; then
    return 0
  fi
  if devtunnel set "${DEVTUNNEL_ID}" >/dev/null 2>&1 && devtunnel port show -p "${port}" >/dev/null 2>&1; then
    return 0
  fi

  echo "Creating tunnel port ${port}/${DEVTUNNEL_PROTOCOL}"
  if devtunnel port create "${DEVTUNNEL_ID}" -p "${port}" --protocol "${DEVTUNNEL_PROTOCOL}" >/dev/null 2>&1; then
    return 0
  fi
  devtunnel set "${DEVTUNNEL_ID}" >/dev/null 2>&1
  devtunnel port create -p "${port}" --protocol "${DEVTUNNEL_PROTOCOL}" >/dev/null
}

enable_anonymous_port_access() {
  local port="$1"
  if devtunnel access list "${DEVTUNNEL_ID}" --port-number "${port}" 2>/dev/null | grep -qi "anonymous"; then
    return 0
  fi
  if devtunnel set "${DEVTUNNEL_ID}" >/dev/null 2>&1 && devtunnel access list --port-number "${port}" 2>/dev/null | grep -qi "anonymous"; then
    return 0
  fi

  echo "Enabling anonymous tunnel access on port ${port}"
  if devtunnel access create "${DEVTUNNEL_ID}" --port-number "${port}" --anonymous >/dev/null 2>&1; then
    return 0
  fi
  devtunnel set "${DEVTUNNEL_ID}" >/dev/null 2>&1
  devtunnel access create --port-number "${port}" --anonymous >/dev/null
}

resolve_tunnel_url_for_port() {
  local port="$1"
  local json
  local found

  json="$(devtunnel show "${DEVTUNNEL_ID}" -j 2>/dev/null || true)"
  if [[ -n "${json}" ]]; then
    found="$(printf '%s' "${json}" | tr -d '\n' \
      | sed -n "s/.*\"portNumber\"[[:space:]]*:[[:space:]]*${port}[^}]*\"portUri\"[[:space:]]*:[[:space:]]*\"\(https:\/\/[^\"]*devtunnels\.ms\/\{0,1\}\)\".*/\1/p")"
    found="${found%/}"
    if [[ -n "${found}" ]]; then
      printf '%s\n' "${found}"
      return 0
    fi
  fi

  json="$(devtunnel port show "${DEVTUNNEL_ID}" -p "${port}" -j 2>/dev/null || true)"
  if [[ -z "${json}" ]]; then
    return 1
  fi

  found="$(printf '%s' "${json}" | tr -d '\n' \
    | sed -n 's/.*"portUri"[[:space:]]*:[[:space:]]*"\(https:\/\/[^"]*devtunnels\.ms\/\{0,1\}\)".*/\1/p')"
  found="${found%/}"
  if [[ -n "${found}" ]]; then
    printf '%s\n' "${found}"
    return 0
  fi
  return 1
}

to_wss_url() {
  local base_url="$1"
  local ws_path="$2"
  local trimmed="${base_url%/}"
  if [[ "${ws_path}" != /* ]]; then
    ws_path="/${ws_path}"
  fi
  trimmed="${trimmed/#https:/wss:}"
  printf '%s%s\n' "${trimmed}" "${ws_path}"
}

HOST_PID=""
SERVER_PID=""

cleanup() {
  if [[ -n "${SERVER_PID}" ]] && kill -0 "${SERVER_PID}" >/dev/null 2>&1; then
    kill "${SERVER_PID}" >/dev/null 2>&1 || true
    wait "${SERVER_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -n "${HOST_PID}" ]] && kill -0 "${HOST_PID}" >/dev/null 2>&1; then
    kill "${HOST_PID}" >/dev/null 2>&1 || true
    wait "${HOST_PID}" >/dev/null 2>&1 || true
  fi
  if [[ -d "${LOCK_DIR}" ]]; then
    rm -rf "${LOCK_DIR}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

seed_runtime_config
ensure_terminal_window "$@"
kill_stale_processes_on_start
acquire_single_instance_lock
SERVER_HTTPS_PORT="$(resolve_server_env_port HAF_HTTPS_PORT "${HTTPS_DEVTUNNEL_PORT}")"
SERVER_HTTPS_PATH="$(resolve_server_env_value HAF_HTTPS_PATH "/api/v1")"
SERVER_WS_PORT="$(resolve_server_env_port HAF_WS_PORT "${WS_DEVTUNNEL_PORT}")"
SERVER_WS_PATH="$(resolve_server_env_value HAF_WS_PATH "${DEFAULT_WS_PATH}")"
ensure_server_port_available "${SERVER_HTTPS_PORT}"
ensure_server_port_available "${SERVER_WS_PORT}"
require_devtunnel_cli
ensure_devtunnel_login
ensure_tunnel_exists
ensure_tunnel_port "${SERVER_HTTPS_PORT}"
ensure_tunnel_port "${SERVER_WS_PORT}"
enable_anonymous_port_access "${SERVER_HTTPS_PORT}"
enable_anonymous_port_access "${SERVER_WS_PORT}"

: > "${TUNNEL_HOST_LOG}"

echo "Starting Dev Tunnel host for ${DEVTUNNEL_ID}:${SERVER_HTTPS_PORT},${SERVER_WS_PORT}"
devtunnel host "${DEVTUNNEL_ID}" 2>&1 \
  | tee -a "${TUNNEL_HOST_LOG}" &
HOST_PID=$!

REST_FORWARD_URL=""
WS_FORWARD_BASE_URL=""
for _ in {1..60}; do
  if ! kill -0 "${HOST_PID}" >/dev/null 2>&1; then
    echo "Dev Tunnel host stopped unexpectedly. Check ${TUNNEL_HOST_LOG}." >&2
    exit 1
  fi
  REST_FORWARD_URL="$(resolve_tunnel_url_for_port "${SERVER_HTTPS_PORT}" || true)"
  WS_FORWARD_BASE_URL="$(resolve_tunnel_url_for_port "${SERVER_WS_PORT}" || true)"
  if [[ -n "${REST_FORWARD_URL}" && -n "${WS_FORWARD_BASE_URL}" ]]; then
    break
  fi
  sleep 1
done

if [[ -z "${REST_FORWARD_URL}" || -z "${WS_FORWARD_BASE_URL}" ]]; then
  echo "Unable to resolve both tunnel forwarding URLs. Check ${TUNNEL_HOST_LOG}." >&2
  exit 1
fi

WSS_FORWARD_URL="$(to_wss_url "${WS_FORWARD_BASE_URL}" "${SERVER_WS_PATH}")"

cat > "${TUNNEL_META_FILE}" <<META
HAF_DEVTUNNEL_ID=${DEVTUNNEL_ID}
HAF_DEVTUNNEL_REST_URL=${REST_FORWARD_URL}
HAF_DEVTUNNEL_WSS_URL=${WSS_FORWARD_URL}
HAF_DEVTUNNEL_HTTPS_PORT=${SERVER_HTTPS_PORT}
HAF_DEVTUNNEL_HTTPS_PATH=${SERVER_HTTPS_PATH}
HAF_DEVTUNNEL_WS_PORT=${SERVER_WS_PORT}
HAF_DEVTUNNEL_WS_PATH=${SERVER_WS_PATH}
HAF_DEVTUNNEL_PROTOCOL=${DEVTUNNEL_PROTOCOL}
META

echo "REST HTTPS URL: ${REST_FORWARD_URL}"
echo "Realtime WSS URL: ${WSS_FORWARD_URL}"
echo "Saved tunnel metadata: ${TUNNEL_META_FILE}"

echo "Starting HAF server with env file: ${SERVER_ENV_FILE}"
if [[ -n "${JAVA_TOOL_OPTIONS:-}" ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS} -Dhaf.log.path=${LOG_DIR}"
else
  export JAVA_TOOL_OPTIONS="-Dhaf.log.path=${LOG_DIR}"
fi
HAF_SERVER_ENV_FILE="${SERVER_ENV_FILE}" "${SERVER_BIN}" "$@" &
SERVER_PID=$!

set +e
wait "${SERVER_PID}"
SERVER_EXIT=$?
set -e

if [[ "${SERVER_EXIT}" -ne 0 ]]; then
  echo "Server exited with status ${SERVER_EXIT}" >&2
fi

exit "${SERVER_EXIT}"
