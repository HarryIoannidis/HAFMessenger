# PERSISTENCE

## Purpose

Document current DAO responsibilities and encrypted-data persistence behavior.

## Current Implementation

- `EnvelopeDAO`: encrypted message envelopes and delivery lifecycle.
- `UserDAO`: auth/profile/search/public-key lookup paths.
- `SessionDAO`: session creation/validation/revocation plus session activity touch and recent-activity checks used by prod presence/duplicate-login policy.
- `FileUploadDAO` + `AttachmentDAO`: encrypted upload and attachment lifecycle.
- `ContactDAO`: contacts list management.
- DAO methods are invoked from ingress/router layers; there is no direct UI-to-DB path in architecture.

## Key Types/Interfaces

- `server.db.EnvelopeDAO`
- `server.db.UserDAO`
- `server.db.SessionDAO`
- `server.db.FileUploadDAO`
- `server.db.AttachmentDAO`
- `server.db.ContactDAO`

## Flow

1. Ingress/router layers call DAOs for inserts/queries/updates.
2. Envelope and attachment rows are persisted with TTL metadata.
3. ACK/cleanup operations update delivered/expired state.
4. Attachment bind operations align attachment expiry to owning envelope expiry.

## Error/Security Notes

- DAO layer stores encrypted bytes opaquely and does not decrypt payloads.
- SQL errors are wrapped and surfaced via typed exceptions/logging.
- Session activity checks and touches use database-side `CURRENT_TIMESTAMP` comparisons to avoid timezone skew between app and DB runtimes.

## Related Files

- `server/src/main/java/com/haf/server/db/EnvelopeDAO.java`
- `server/src/main/java/com/haf/server/db/AttachmentDAO.java`
- `server/src/main/java/com/haf/server/db/UserDAO.java`
- `server/src/main/resources/db/migration`
