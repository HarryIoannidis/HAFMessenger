# CONFIG

## Purpose

Document server runtime configuration loaded by `ServerConfig`.

## Current Implementation

- `ServerConfig.load()` starts with environment variables and overlays values from `server/src/main/resources/config/variables.env` when present.
- Env-file lookup can be overridden via JVM property `haf.server.env.path` or environment variable `HAF_SERVER_ENV_FILE`.
- Required vars include DB credentials, TLS keystore, key passphrase, search cursor secret, and JWT signing secret.
- `HAF_KEY_PASS` is consumed by server runtime only and is not used by client keystore bootstrap.
- Optional vars control pool sizing, ports, search limits, and attachment upload limits.
- Optional DB TLS truststore vars:
  - `HAF_DB_TRUSTSTORE_PATH`
  - `HAF_DB_TRUSTSTORE_PASS`
  - `HAF_DB_TRUSTSTORE_TYPE` (default: `PKCS12`)
- Required keys:
  - `HAF_DB_URL`
  - `HAF_DB_USER`
  - `HAF_DB_PASS`
  - `HAF_KEY_PASS`
  - `HAF_TLS_KEYSTORE_PATH`
  - `HAF_TLS_KEYSTORE_PASS`
  - `HAF_SEARCH_CURSOR_SECRET`
  - `HAF_JWT_SECRET`
- Key defaults:
  - DB pool size: `20`
  - HTTPS REST API port: `8443` (path: `/api/v1`, configurable via `HAF_HTTPS_PATH`)
  - WSS realtime port: `8444` (path: `/ws/v1/realtime`, configurable via `HAF_WS_PATH`)
  - Search page size: `20` (max `50`)
  - Search min/max query length: `3` / `128`
  - JWT access TTL: `900` seconds
  - JWT refresh TTL: `2592000` seconds
  - JWT absolute session TTL: `2592000` seconds
  - JWT idle session TTL: `max(600, HAF_JWT_ACCESS_TTL_SECONDS)` seconds when `HAF_JWT_IDLE_TTL_SECONDS` is unset
  - Attachment limits default to `AttachmentConstants` values (`max`, `inline max`, `chunk bytes`, unbound TTL)

## Key Types/Interfaces

- `server.config.ServerConfig`
- `server.exceptions.ConfigurationException`

## Flow

1. Load env map and optional `variables.env` file.
2. Parse required/optional values with defaults and normalized types.
3. Parse required keys and apply typed defaults for optional keys.
4. Apply TLS keystore compatibility fallback for `server/...` path variants.
5. Validate search, JWT, and attachment constraints.
6. Expose typed getters used by server bootstrap and ingress.

## Error/Security Notes

- Missing required values fail fast with `ConfigurationException`.
- TLS keystore path has compatibility fallback resolution logic.
- JWT TTL values are validated (`access >= 60s`, `refresh >= access`, `absolute >= refresh`, `idle >= 60s`).
- Attachment size/chunk/TTL values are validated to prevent invalid runtime limits.
- `HAF_JWT_IDLE_TTL_SECONDS` is consumed by `SessionDAO` at bootstrap to enforce session-idle expiry behavior.
- Password getters return cloned char arrays to reduce accidental mutable sharing.
- Packaging note: first-run local config bootstrap (no manual system-env setup) is tracked as a separate follow-up.

## Related Files

- `server/src/main/java/com/haf/server/config/ServerConfig.java`
- `server/src/main/resources/config/variables.env`
- `shared/src/main/java/com/haf/shared/constants/AttachmentConstants.java`
- `server/src/main/java/com/haf/server/core/Main.java`
