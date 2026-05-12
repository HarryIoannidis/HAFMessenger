#!/usr/bin/env bash
set -euo pipefail

# ============================================================================
# generate-mysql-certs.sh
#
# Generates a custom CA and MySQL server certificate with proper CN/SAN
# entries so that sslMode=VERIFY_IDENTITY works with 127.0.0.1 and localhost.
#
# What this script produces:
#   1. A self-signed CA (ca-key.pem, ca-cert.pem)
#   2. A server certificate signed by that CA (server-key.pem, server-cert.pem)
#      with SAN entries: 127.0.0.1, localhost, ::1
#   3. A PKCS12 truststore (mysql-truststore.p12) containing the CA cert
#
# After running, you must configure MySQL to use the generated certs.
# ============================================================================

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)"

OUTPUT_DIR="${OUTPUT_DIR:-${PROJECT_ROOT}/server/src/main/resources/config/mysql-ssl}"
TRUSTSTORE_DIR="${TRUSTSTORE_DIR:-${PROJECT_ROOT}/server/src/main/resources/config}"
TRUSTSTORE_PASS="${TRUSTSTORE_PASS:-HafMysqlTruststore1}"
TRUSTSTORE_FILE="${TRUSTSTORE_DIR}/mysql-truststore.p12"

CA_SUBJECT="${CA_SUBJECT:-/CN=HAF-MySQL-CA}"
SERVER_CN="${SERVER_CN:-127.0.0.1}"
CERT_DAYS="${CERT_DAYS:-3650}"
KEY_BITS="${KEY_BITS:-4096}"

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl is required but was not found in PATH." >&2
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool is required but was not found in PATH." >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

# --- Step 1: Generate CA ---------------------------------------------------

echo "Generating CA private key..."
openssl genrsa -out "${OUTPUT_DIR}/ca-key.pem" "${KEY_BITS}" 2>/dev/null

echo "Generating CA certificate (${CERT_DAYS} days)..."
openssl req -new -x509 \
  -key "${OUTPUT_DIR}/ca-key.pem" \
  -out "${OUTPUT_DIR}/ca-cert.pem" \
  -days "${CERT_DAYS}" \
  -subj "${CA_SUBJECT}" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign"

# --- Step 2: Generate Server Certificate ------------------------------------

SAN_EXT_FILE="${OUTPUT_DIR}/server-san.cnf"
cat > "${SAN_EXT_FILE}" <<EOF
[req]
distinguished_name = req_dn
req_extensions     = v3_req
prompt             = no

[req_dn]
CN = ${SERVER_CN}

[v3_req]
subjectAltName = @alt_names

[alt_names]
IP.1  = 127.0.0.1
IP.2  = ::1
DNS.1 = localhost
EOF

echo "Generating server private key..."
openssl genrsa -out "${OUTPUT_DIR}/server-key.pem" "${KEY_BITS}" 2>/dev/null

echo "Generating server CSR..."
openssl req -new \
  -key "${OUTPUT_DIR}/server-key.pem" \
  -out "${OUTPUT_DIR}/server-csr.pem" \
  -config "${SAN_EXT_FILE}"

echo "Signing server certificate with CA..."
openssl x509 -req \
  -in "${OUTPUT_DIR}/server-csr.pem" \
  -CA "${OUTPUT_DIR}/ca-cert.pem" \
  -CAkey "${OUTPUT_DIR}/ca-key.pem" \
  -CAcreateserial \
  -out "${OUTPUT_DIR}/server-cert.pem" \
  -days "${CERT_DAYS}" \
  -extfile "${SAN_EXT_FILE}" \
  -extensions v3_req 2>/dev/null

# Clean up intermediate files
rm -f "${OUTPUT_DIR}/server-csr.pem" "${OUTPUT_DIR}/ca-cert.srl" "${SAN_EXT_FILE}"

# --- Step 3: Build Java Truststore ------------------------------------------

echo "Building Java PKCS12 truststore..."
if [[ -f "${TRUSTSTORE_FILE}" ]]; then
  # Delete existing entry if present
  keytool -delete -alias mysql-ca \
    -keystore "${TRUSTSTORE_FILE}" \
    -storetype PKCS12 \
    -storepass "${TRUSTSTORE_PASS}" 2>/dev/null || true
fi

keytool -importcert \
  -alias mysql-ca \
  -file "${OUTPUT_DIR}/ca-cert.pem" \
  -keystore "${TRUSTSTORE_FILE}" \
  -storetype PKCS12 \
  -storepass "${TRUSTSTORE_PASS}" \
  -noprompt

# --- Step 4: Verify ---------------------------------------------------------

echo ""
echo "============================================================"
echo "  Certificates generated successfully!"
echo "============================================================"
echo ""
echo "Files created in: ${OUTPUT_DIR}"
echo "  ca-key.pem       - CA private key (keep secret!)"
echo "  ca-cert.pem      - CA certificate"
echo "  server-key.pem   - MySQL server private key"
echo "  server-cert.pem  - MySQL server certificate"
echo ""
echo "Truststore updated: ${TRUSTSTORE_FILE}"
echo ""
echo "Server certificate details:"
openssl x509 -in "${OUTPUT_DIR}/server-cert.pem" -noout -subject -issuer -ext subjectAltName 2>/dev/null || \
openssl x509 -in "${OUTPUT_DIR}/server-cert.pem" -noout -subject -issuer
echo ""
echo "============================================================"
echo "  Next steps: Configure MySQL to use these certificates"
echo "============================================================"
echo ""
echo "1. Copy certs to MySQL's data directory (or another secure location):"
echo ""
echo "   sudo cp ${OUTPUT_DIR}/ca-cert.pem     /var/lib/mysql/ca.pem"
echo "   sudo cp ${OUTPUT_DIR}/server-cert.pem  /var/lib/mysql/server-cert.pem"
echo "   sudo cp ${OUTPUT_DIR}/server-key.pem   /var/lib/mysql/server-key.pem"
echo "   sudo chown mysql:mysql /var/lib/mysql/ca.pem /var/lib/mysql/server-cert.pem /var/lib/mysql/server-key.pem"
echo "   sudo chmod 600 /var/lib/mysql/server-key.pem"
echo ""
echo "2. Edit /etc/mysql/mysql.conf.d/mysqld.cnf (or equivalent) and set:"
echo ""
echo "   [mysqld]"
echo "   ssl_ca   = /var/lib/mysql/ca.pem"
echo "   ssl_cert = /var/lib/mysql/server-cert.pem"
echo "   ssl_key  = /var/lib/mysql/server-key.pem"
echo ""
echo "3. Restart MySQL:"
echo ""
echo "   sudo systemctl restart mysql"
echo ""
echo "4. Update variables.env to use VERIFY_IDENTITY:"
echo ""
echo "   sslMode=VERIFY_IDENTITY"
echo ""
