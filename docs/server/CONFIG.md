# CONFIG

## Purpose

Document server runtime configuration loaded by `ServerConfig`.

## Current Implementation

- `ServerConfig.load()` starts with environment variables and overlays values from `server/src/main/resources/config/variables.env` when present.
- Required vars include DB, TLS keystore, key passphrase, and search cursor secret.
- Optional vars control pool sizing, ports, search limits, and attachment policy.
- Required keys currently include `HAF_DB_URL`, `HAF_DB_USER`, `HAF_DB_PASS`, `HAF_KEY_PASS`, `HAF_TLS_KEYSTORE_PATH`, `HAF_TLS_KEYSTORE_PASS`, and `HAF_SEARCH_CURSOR_SECRET`.
- Defaults include HTTP `8443`, WS `8444`, search page size `20`, and search max page size `50`.

## Key Types/Interfaces

- `server.config.ServerConfig`
- `server.exceptions.ConfigurationException`

## Flow

1. Load env map and optional `variables.env` file.
2. Parse required/optional values with defaults and normalized types.
3. Apply TLS keystore compatibility fallback for `server/...` path variants.
4. Validate search and attachment constraints.
5. Expose typed getters used by server bootstrap and ingress.

## Error/Security Notes

- Missing required values fail fast with `ConfigurationException`.
- TLS keystore path has compatibility fallback resolution logic.
- Attachment policy values are validated to prevent invalid runtime limits.
- Password getters return cloned char arrays to reduce accidental mutable sharing.

## Related Files

- `server/src/main/java/com/haf/server/config/ServerConfig.java`
- `server/src/main/resources/config/variables.env`
- `server/src/main/java/com/haf/server/core/Main.java`
