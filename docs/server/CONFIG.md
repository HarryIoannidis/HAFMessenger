# CONFIG

### Purpose
- Documents the actual runtime configuration surface loaded by `ServerConfig`.
- Centralizes server settings (DB, TLS, search, attachments) and fail-fast validation rules.

### Loading behavior
- `ServerConfig.load()` starts from `System.getenv()` and then overlays values from `server/src/main/resources/config/variables.env` if present.
- In this implementation, values from `variables.env` override environment variables with the same key.

### Required variables
- `HAF_DB_URL`: JDBC URL for MySQL.
- `HAF_DB_USER`: database username.
- `HAF_DB_PASS`: database password.
- `HAF_KEY_PASS`: keystore passphrase used by shared keystore flows.
- `HAF_TLS_KEYSTORE_PATH`: PKCS12 path for TLS server cert/key.
- `HAF_TLS_KEYSTORE_PASS`: password for the TLS keystore.
- `HAF_SEARCH_CURSOR_SECRET`: HMAC secret used to sign search cursors.

### Optional variables (with defaults)
- `HAF_DB_POOL_SIZE` (default `20`).
- `HAF_HTTP_PORT` (default `8443`).
- `HAF_WS_PORT` (default `8444`).
- `HAF_KEYSTORE_ROOT` (optional path override).
- `HAF_ADMIN_PUBLIC_KEY` (optional PEM returned by `/api/v1/config/admin-key`).

### Search configuration
- `HAF_SEARCH_PAGE_SIZE` (default `20`).
- `HAF_SEARCH_MAX_PAGE_SIZE` (default `50`).
- `HAF_SEARCH_MIN_QUERY_LENGTH` (default `3`).
- `HAF_SEARCH_MAX_QUERY_LENGTH` (default `128`).

### Attachment policy configuration
- `HAF_ATTACHMENT_MAX_BYTES` (default `10485760` / 10 MB).
- `HAF_ATTACHMENT_INLINE_MAX_BYTES` (default `1048576` / 1 MB).
- `HAF_ATTACHMENT_CHUNK_BYTES` (default `524288` / 512 KB).
- `HAF_ATTACHMENT_ALLOWED_TYPES` (CSV MIME allowlist; defaults to shared attachment constants).
- `HAF_ATTACHMENT_UNBOUND_TTL_SECONDS` (default `1800`).

### Validation rules
- Missing required values throw `ConfigurationException` and abort startup.
- Search constraints are validated at boot:
  - `minQueryLength >= 1`
  - `maxQueryLength >= minQueryLength`
  - `pageSize` and `maxPageSize` are positive
- Attachment constraints are validated at boot:
  - all size/TTL values must be positive
  - `inlineMaxBytes <= maxBytes`
  - MIME allowlist must not be empty

### Path normalization notes
- TLS keystore path is resolved with compatibility fallbacks when prefixed or not prefixed by `server/`.
- If the configured path does not exist directly, `ServerConfig` tries alternative relative forms.
