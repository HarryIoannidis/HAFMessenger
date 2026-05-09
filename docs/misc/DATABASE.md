# DATABASE

## Purpose

Document the current relational schema and DAO responsibilities used by the server.

## Current Implementation

- Database: MySQL with Flyway migrations under `server/src/main/resources/db/migration`.
- Connection management: HikariCP configured by `ServerConfig`.
- Main tables (from migrations):
  - `users`
  - `sessions`
  - `message_envelopes`
  - `audit_logs`
  - `rate_limits`
  - `login_rate_limits`
  - `file_uploads`
  - `contacts`
  - `message_attachments`
  - `message_attachment_chunks`
- Latest migrations include search indexes, attachment tables, session activity tracking, JWT/refresh session hardening, and mandatory signing-key/signature schema updates (`V10`-`V17`).
- Migration execution is part of server startup (`Main.runFlywayMigrations(...)`) before ingress servers start.

## Key Types/Interfaces

- DAOs:
  - `UserDAO`
  - `SessionDAO`
  - `EnvelopeDAO`
  - `FileUploadDAO`
  - `AttachmentDAO`
  - `ContactDAO`
- Exceptions/related:
  - `DatabaseOperationException`
  - `RateLimitException`

## Flow

1. Startup runs Flyway migrations before ingress starts.
2. Ingress and router paths call DAOs with prepared statements.
3. `EnvelopeDAO` persists encrypted envelope metadata/payload and supports fetch/ack/expiry cleanup.
4. `AttachmentDAO` handles init/chunk/complete/bind/download lifecycle for encrypted attachments.
5. Scheduled cleanup removes expired uploads/envelopes according to TTL policy.
6. Metrics/audit layers observe DAO-side outcomes (ingress rejects, cleanup counts, delivery latency).
7. Session presence/duplicate-login logic uses `sessions.last_seen_at` and database-time comparisons.
8. Session rows persist `access_jti` and `refresh_token_hash` (no plaintext refresh tokens).
9. `users` rows persist both encryption and signing public keys/fingerprints.
10. `message_envelopes` rows persist signature algorithm, sender signing fingerprint, and detached signature bytes.

## Error/Security Notes

- Server stores encrypted payloads opaquely; no decrypt in DAO layer.
- SQL is executed through `PreparedStatement` patterns.
- Session and auth checks gate protected endpoints before DAO mutation paths.
- Message and login rate-limit state is stored server-side in `rate_limits` and `login_rate_limits`.
- Migration `V16` resets legacy users and adds required signing-key columns; `V17` adds required message-signature columns.

## Related Files

- `server/src/main/java/com/haf/server/core/Main.java`
- `server/src/main/java/com/haf/server/db/EnvelopeDAO.java`
- `server/src/main/java/com/haf/server/db/AttachmentDAO.java`
- `server/src/main/java/com/haf/server/db/UserDAO.java`
- `server/src/main/resources/db/migration/V1__create_users_table.sql`
- `server/src/main/resources/db/migration/V11__create_message_attachments_tables.sql`
- `server/src/main/resources/db/migration/V12__add_sessions_last_seen.sql`
- `server/src/main/resources/db/migration/V13__harden_sessions_and_add_login_rate_limits.sql`
- `server/src/main/resources/db/migration/V16__require_signing_keys_and_reset_legacy_users.sql`
- `server/src/main/resources/db/migration/V17__require_message_signatures.sql`
