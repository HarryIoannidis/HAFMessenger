#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# generate-admin-keys.sh
#
# Generates an X25519 keypair for the server admin. The private key is used
# to decrypt registration photos that clients encrypt with the public key.
#
# Environment overrides:
#   ADMIN_KEY_DIR  – output directory (default: .local/hafmessenger/server)
# ============================================================================

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"

ADMIN_KEY_DIR="${ADMIN_KEY_DIR:-${PROJECT_ROOT}/.local/hafmessenger/server}"

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl is required but was not found in PATH." >&2
  exit 1
fi

mkdir -p "${ADMIN_KEY_DIR}"

PRIV_FILE="${ADMIN_KEY_DIR}/admin_private_key.txt"
PUB_FILE="${ADMIN_KEY_DIR}/admin_public_key.txt"

echo "Generating admin X25519 keypair..."

# Generate X25519 private key in PEM
PRIV_PEM=$(openssl genpkey -algorithm X25519 2>/dev/null)

# Derive public key PEM
PUB_PEM=$(echo "${PRIV_PEM}" | openssl pkey -pubout 2>/dev/null)

# Extract Base64-encoded DER for use in variables.env
PUB_B64=$(echo "${PUB_PEM}" | grep -v '^\-\-' | tr -d '\n')

# Extract Base64-encoded private DER
PRIV_DER_B64=$(echo "${PRIV_PEM}" | openssl pkey -outform DER 2>/dev/null | openssl base64 -A)

# Write private key
cat > "${PRIV_FILE}" <<EOF
DO NOT COMMIT THIS FILE TO VERSION CONTROL.
This is the admin's X25519 private key, needed to decrypt registration photos.

${PRIV_DER_B64}
EOF
chmod 600 "${PRIV_FILE}"

# Write public key
cat > "${PUB_FILE}" <<EOF
Admin X25519 public key (Base64 DER).
Set HAF_ADMIN_PUBLIC_KEY to this value in variables.env.

${PUB_B64}
EOF

echo ""
echo "============================================================"
echo "  Admin X25519 keypair generated"
echo "============================================================"
echo ""
echo "  Private key: ${PRIV_FILE}"
echo "  Public key:  ${PUB_FILE}"
echo ""
echo "  Add to variables.env:"
echo "    HAF_ADMIN_PUBLIC_KEY=${PUB_B64}"
echo ""
