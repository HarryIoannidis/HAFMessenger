#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# bootstrap-local-dev.sh
#
# Creates everything a new developer needs to run HAFMessenger locally.
# All generated material lands under .local/hafmessenger/ which is gitignored.
#
# What this script produces:
#   1. Server environment file   (.local/hafmessenger/server/variables.env)
#   2. Server HTTPS keystore     (.local/hafmessenger/server/server.p12)
#   3. MySQL CA + server certs   (.local/hafmessenger/server/mysql-ssl/*)
#   4. MySQL Java truststore     (.local/hafmessenger/server/mysql-truststore.p12)
#   5. Admin X25519 keypair      (.local/hafmessenger/server/admin_private_key.txt
#                                  + public key embedded in variables.env)
#   6. Client truststore         (.local/hafmessenger/client/truststore.p12)
#   7. Client properties file    (.local/hafmessenger/client/client.properties)
#
# Usage:
#   ./scripts/bootstrap-local-dev.sh          # run from project root
#   FORCE=1 ./scripts/bootstrap-local-dev.sh  # overwrite existing files
# ============================================================================

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"

LOCAL_ROOT="${PROJECT_ROOT}/.local/hafmessenger"
SERVER_DIR="${LOCAL_ROOT}/server"
CLIENT_DIR="${LOCAL_ROOT}/client"
MYSQL_SSL_DIR="${SERVER_DIR}/mysql-ssl"

FORCE="${FORCE:-0}"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

banner() {
  echo ""
  echo "============================================================"
  echo "  $1"
  echo "============================================================"
  echo ""
}

random_password() {
  # Generate a 48-byte random value, base64-encoded (64 chars)
  openssl rand -base64 48 | tr -d '\n'
}

check_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "ERROR: '$1' is required but was not found in PATH." >&2
    exit 1
  fi
}

should_generate() {
  local file="$1"
  if [[ "${FORCE}" == "1" ]] || [[ ! -f "${file}" ]]; then
    return 0
  fi
  echo "  SKIP: ${file} already exists (use FORCE=1 to overwrite)"
  return 1
}

# ---------------------------------------------------------------------------
# Preflight checks
# ---------------------------------------------------------------------------

check_tool openssl
check_tool keytool

# ---------------------------------------------------------------------------
# Directory setup
# ---------------------------------------------------------------------------

mkdir -p "${SERVER_DIR}" "${CLIENT_DIR}" "${MYSQL_SSL_DIR}"

banner "HAFMessenger Local Dev Bootstrap"

# ---------------------------------------------------------------------------
# Step 1: Generate strong random secrets
# ---------------------------------------------------------------------------

echo "Generating random secrets..."

DB_PASS="$(random_password)"
KEY_PASS="$(random_password)"
TLS_KEYSTORE_PASS="$(random_password)"
MYSQL_TRUSTSTORE_PASS="$(random_password)"
JWT_SECRET="$(random_password)"
SEARCH_CURSOR_SECRET="$(random_password)"
LOGIN_SENTINEL_PASSWORD="$(random_password)"

# ---------------------------------------------------------------------------
# Step 2: Generate Admin X25519 keypair
# ---------------------------------------------------------------------------

ADMIN_PRIVATE_KEY_FILE="${SERVER_DIR}/admin_private_key.txt"
ADMIN_PUBLIC_KEY_B64=""

if should_generate "${ADMIN_PRIVATE_KEY_FILE}"; then
  echo "Generating admin X25519 keypair..."

  # Generate X25519 private key
  ADMIN_PRIV_PEM=$(openssl genpkey -algorithm X25519 2>/dev/null)
  ADMIN_PUB_PEM=$(echo "${ADMIN_PRIV_PEM}" | openssl pkey -pubout 2>/dev/null)

  # Extract base64 DER from PEM for the public key (used in env file)
  ADMIN_PUBLIC_KEY_B64=$(echo "${ADMIN_PUB_PEM}" | grep -v '^\-\-' | tr -d '\n')

  # Write private key file
  cat > "${ADMIN_PRIVATE_KEY_FILE}" <<EOF
DO NOT COMMIT THIS FILE TO VERSION CONTROL.
This is the admin's X25519 private key, needed to decrypt registration photos.

$(echo "${ADMIN_PRIV_PEM}" | openssl pkey -outform DER 2>/dev/null | openssl base64 -A)
EOF
  chmod 600 "${ADMIN_PRIVATE_KEY_FILE}"
  echo "  Created: ${ADMIN_PRIVATE_KEY_FILE}"
else
  # Read existing public key for env file
  echo "  Using existing admin keypair"
  ADMIN_PUBLIC_KEY_B64="EXISTING_KEY_UNCHANGED"
fi

# ---------------------------------------------------------------------------
# Step 3: Generate MySQL SSL certificates
# ---------------------------------------------------------------------------

echo "Generating MySQL SSL certificates..."

OUTPUT_DIR="${MYSQL_SSL_DIR}" \
  TRUSTSTORE_DIR="${SERVER_DIR}" \
  TRUSTSTORE_PASS="${MYSQL_TRUSTSTORE_PASS}" \
  bash "${SCRIPT_DIR}/generate-mysql-certs.sh"

# ---------------------------------------------------------------------------
# Step 4: Generate server HTTPS TLS keystore
# ---------------------------------------------------------------------------

echo "Generating server HTTPS TLS keystore..."

TLS_KEYSTORE_PATH="${SERVER_DIR}/server.p12" \
  TLS_KEYSTORE_PASS="${TLS_KEYSTORE_PASS}" \
  bash "${SCRIPT_DIR}/generate-server-tls.sh"

# ---------------------------------------------------------------------------
# Step 5: Generate client truststore (import server's self-signed cert)
# ---------------------------------------------------------------------------

CLIENT_TRUSTSTORE="${CLIENT_DIR}/truststore.p12"

if should_generate "${CLIENT_TRUSTSTORE}"; then
  echo "Generating client truststore..."

  # Export the server certificate from the server keystore
  SERVER_CERT_TMP="${LOCAL_ROOT}/server-cert-export.pem"
  keytool -exportcert \
    -alias haf-server \
    -keystore "${SERVER_DIR}/server.p12" \
    -storetype PKCS12 \
    -storepass "${TLS_KEYSTORE_PASS}" \
    -rfc \
    -file "${SERVER_CERT_TMP}" 2>/dev/null

  # Import into client truststore
  keytool -importcert \
    -alias haf-server \
    -file "${SERVER_CERT_TMP}" \
    -keystore "${CLIENT_TRUSTSTORE}" \
    -storetype PKCS12 \
    -storepass "${TLS_KEYSTORE_PASS}" \
    -noprompt 2>/dev/null

  rm -f "${SERVER_CERT_TMP}"
  echo "  Created: ${CLIENT_TRUSTSTORE}"
fi

# ---------------------------------------------------------------------------
# Step 6: Write server variables.env
# ---------------------------------------------------------------------------

ENV_FILE="${SERVER_DIR}/variables.env"

if should_generate "${ENV_FILE}"; then
  echo "Writing server environment file..."
  cat > "${ENV_FILE}" <<EOF
# ============================================================================
# HAFMessenger Server Configuration
# Generated by bootstrap-local-dev.sh on $(date -u +"%Y-%m-%dT%H:%M:%SZ")
# This file contains secrets — DO NOT commit to version control.
# ============================================================================

# Database
HAF_DB_URL=jdbc:mysql://127.0.0.1:3306/haf_messenger?sslMode=VERIFY_IDENTITY&allowPublicKeyRetrieval=true&serverTimezone=UTC
HAF_DB_USER=haf_app
HAF_DB_PASS=${DB_PASS}
HAF_DB_POOL_SIZE=20
HAF_DB_TRUSTSTORE_PATH=${SERVER_DIR}/mysql-truststore.p12
HAF_DB_TRUSTSTORE_PASS=${MYSQL_TRUSTSTORE_PASS}
HAF_DB_TRUSTSTORE_TYPE=PKCS12

# Encryption
HAF_KEY_PASS=${KEY_PASS}

# TLS
HAF_TLS_KEYSTORE_PATH=${SERVER_DIR}/server.p12
HAF_TLS_KEYSTORE_PASS=${TLS_KEYSTORE_PASS}

# Server
HAF_HTTPS_PORT=8443
HAF_HTTPS_PATH=/api/v1
HAF_WS_PORT=8444
HAF_WS_PATH=/ws/v1/realtime

# Admin
HAF_ADMIN_PUBLIC_KEY=${ADMIN_PUBLIC_KEY_B64}

# Search
HAF_SEARCH_PAGE_SIZE=20
HAF_SEARCH_MAX_PAGE_SIZE=50
HAF_SEARCH_MIN_QUERY_LENGTH=3
HAF_SEARCH_MAX_QUERY_LENGTH=128
HAF_TRUST_PROXY=false
HAF_TRUSTED_PROXY_CIDRS=
HAF_INGRESS_EXECUTOR_THREADS=64
HAF_INGRESS_EXECUTOR_QUEUE_CAPACITY=1024
HAF_SEARCH_CURSOR_SECRET=${SEARCH_CURSOR_SECRET}

# JWT
HAF_JWT_SECRET=${JWT_SECRET}
HAF_JWT_ACCESS_TTL_SECONDS=900
HAF_JWT_REFRESH_TTL_SECONDS=2592000
HAF_JWT_ABSOLUTE_TTL_SECONDS=2592000
HAF_JWT_IDLE_TTL_SECONDS=3600

# Login hardening
HAF_LOGIN_SENTINEL_PASSWORD=${LOGIN_SENTINEL_PASSWORD}

# Chat attachments
HAF_ATTACHMENT_MAX_BYTES=10485760
HAF_ATTACHMENT_INLINE_MAX_BYTES=2097152
HAF_ATTACHMENT_CHUNK_BYTES=2097152
HAF_ATTACHMENT_UNBOUND_TTL_SECONDS=1800
EOF
  chmod 600 "${ENV_FILE}"
  echo "  Created: ${ENV_FILE}"
fi

# ---------------------------------------------------------------------------
# Step 7: Write client properties
# ---------------------------------------------------------------------------

CLIENT_PROPS="${CLIENT_DIR}/client.properties"

if should_generate "${CLIENT_PROPS}"; then
  echo "Writing client properties..."
  cat > "${CLIENT_PROPS}" <<EOF
# HAFMessenger Client Configuration
# Generated by bootstrap-local-dev.sh

# Local HTTPS server base URL
server.url.prod=https://localhost:8443/

# Local WSS realtime endpoint
server.ws.url.prod=wss://localhost:8444/ws/v1/realtime

# Optional help center base URL
help.center.url.prod=https://localhost:8443
EOF
  echo "  Created: ${CLIENT_PROPS}"
fi

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------

banner "Bootstrap Complete"

echo "Files generated under: ${LOCAL_ROOT}"
echo ""
echo "Quick start:"
echo ""
echo "  1. Set up MySQL with the generated certs:"
echo "     See: ${MYSQL_SSL_DIR}/"
echo ""
echo "  2. Create the database and user:"
echo "     mysql> CREATE DATABASE IF NOT EXISTS haf_messenger;"
echo "     mysql> CREATE USER 'haf_app'@'localhost' IDENTIFIED BY '<password-from-variables.env>';"
echo "     mysql> GRANT ALL PRIVILEGES ON haf_messenger.* TO 'haf_app'@'localhost';"
echo ""
echo "  3. Start the server:"
echo "     HAF_SERVER_ENV_FILE=${ENV_FILE} ./mvnw -pl server exec:java"
echo ""
echo "  4. Start the client:"
echo "     Copy ${CLIENT_DIR}/client.properties to client/src/main/resources/config/"
echo "     Copy ${CLIENT_DIR}/truststore.p12 to client/src/main/resources/config/"
echo "     ./mvnw -pl client javafx:run"
echo ""
