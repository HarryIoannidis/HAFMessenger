# PERSISTENCE

## Purpose

Document current DAO responsibilities and encrypted-data persistence behavior.

## Current Implementation

- `Envelope`: encrypted message envelopes and delivery lifecycle.
- `User`: auth/profile/search/public-key lookup paths.
- `Session`: JWT access-token + refresh-token session issuance, refresh rotation (hashed refresh storage), absolute session-lifetime cap, token validation/revocation, and recent-activity checks used by prod presence/duplicate-login policy.
- `FileUpload` + `Attachment`: encrypted upload and attachment lifecycle.
- `Contact`: contacts list management.
- Integrated Entity/DAO methods are invoked from ingress/router layers; there is no direct UI-to-DB path in architecture.

## Key Types/Interfaces

- `server.db.Envelope`
- `server.db.User`
- `server.db.Session`
- `server.db.FileUpload`
- `server.db.Attachment`
- `server.db.Contact`

## Flow

1. Ingress/router layers call integrated entity/DAOs for inserts/queries/updates.
2. Envelope and attachment rows are persisted with TTL metadata.
3. Envelope rows persist Ed25519 signature bytes, signature algorithm, and sender signing fingerprint.
4. User rows persist both encryption and signing public-key material for directory lookups/takeover rotation.
5. ACK/cleanup operations update delivered/expired state.
6. Attachment bind operations align attachment expiry to owning envelope expiry.

## Error/Security Notes

- Integrated entity/DAO layer stores encrypted bytes opaquely and does not decrypt payloads.
- Signature bytes are stored and returned opaquely; verification occurs in ingress/client receive paths.
- SQL errors are wrapped and surfaced via typed exceptions/logging.
- Session activity checks and touches use database-side `CURRENT_TIMESTAMP` comparisons to avoid timezone skew between app and DB runtimes.
- Refresh tokens are never stored in plaintext; only `SHA-256` hashes are persisted.
- Refresh rotation is capped by `sessions.absolute_expires_at`, so refresh cannot extend a session indefinitely.

## Related Files

- `server/src/main/java/com/haf/server/db/Envelope.java`
- `server/src/main/java/com/haf/server/db/Attachment.java`
- `server/src/main/java/com/haf/server/db/User.java`
- `server/src/main/resources/db/migration`
