#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# generate-server-tls.sh
#
# Generates a self-signed HTTPS server keystore (PKCS12) with SANs for
# localhost, 127.0.0.1, and ::1.
#
# Environment overrides:
#   TLS_KEYSTORE_PATH  – output keystore path (default: .local/hafmessenger/server/server.p12)
#   TLS_KEYSTORE_PASS  – keystore password    (default: random)
#   CERT_DAYS          – validity period       (default: 3650)
#   KEY_SIZE           – RSA key size          (default: 4096)
# ============================================================================

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"

TLS_KEYSTORE_PATH="${TLS_KEYSTORE_PATH:-${PROJECT_ROOT}/.local/hafmessenger/server/server.p12}"
TLS_KEYSTORE_PASS="${TLS_KEYSTORE_PASS:-$(openssl rand -base64 48 | tr -d '\n')}"
CERT_DAYS="${CERT_DAYS:-3650}"
KEY_SIZE="${KEY_SIZE:-4096}"
ALIAS="haf-server"

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool is required but was not found in PATH." >&2
  exit 1
fi

mkdir -p "$(dirname "${TLS_KEYSTORE_PATH}")"

# Remove existing keystore if present
rm -f "${TLS_KEYSTORE_PATH}"

echo "Generating server HTTPS keystore..."

keytool -genkeypair \
  -alias "${ALIAS}" \
  -keyalg RSA \
  -keysize "${KEY_SIZE}" \
  -validity "${CERT_DAYS}" \
  -storetype PKCS12 \
  -keystore "${TLS_KEYSTORE_PATH}" \
  -storepass "${TLS_KEYSTORE_PASS}" \
  -keypass "${TLS_KEYSTORE_PASS}" \
  -dname "CN=localhost,OU=HAFMessenger,O=HAF,L=Local,C=GR" \
  -ext "SAN=dns:localhost,ip:127.0.0.1,ip:::1" \
  -ext "KeyUsage=digitalSignature,keyEncipherment" \
  -ext "ExtendedKeyUsage=serverAuth"

echo ""
echo "============================================================"
echo "  Server TLS keystore generated"
echo "============================================================"
echo ""
echo "  Keystore: ${TLS_KEYSTORE_PATH}"
echo "  Alias:    ${ALIAS}"
echo "  Validity: ${CERT_DAYS} days"
echo "  SANs:     localhost, 127.0.0.1, ::1"
echo ""
echo "  Use in variables.env:"
echo "    HAF_TLS_KEYSTORE_PATH=${TLS_KEYSTORE_PATH}"
echo "    HAF_TLS_KEYSTORE_PASS=${TLS_KEYSTORE_PASS}"
echo ""
