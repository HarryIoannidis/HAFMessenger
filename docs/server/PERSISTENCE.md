# PERSISTENCE

### Purpose
- Documents current DAO responsibilities and how encrypted data is stored.
- Focuses on implemented behavior in `server/src/main/java/com/haf/server/db`.

---

## EnvelopeDAO

### Scope
- Stores encrypted message envelopes in `message_envelopes`.
- Never decrypts payloads; only persists/re-hydrates opaque fields.

### Stored envelope fields
- `envelope_id`, `sender_id`, `recipient_id`
- `encrypted_payload` (ciphertext bytes)
- `wrapped_key` (ephemeral public key bytes)
- `iv`, `auth_tag`
- `aad_hash`, `content_type`, `content_length`, `timestamp`, `ttl`, `expires_at`

### Key methods
- `insert(EncryptedMessage message) -> QueuedEnvelope`
- `fetchForRecipient(String recipientId, int limit) -> List<QueuedEnvelope>`
- `fetchByIds(Collection<String> envelopeIds) -> Map<String, QueuedEnvelope>`
- `markDelivered(List<String> envelopeIds) -> boolean`
- `deleteExpired() -> int`

---

## UserDAO

### Scope
- Registration persistence and user/profile lookup.
- Public-key lookup for recipient encryption.
- Search + cursor pagination support.

### Key methods
- `insert(RegisterRequest request, String hashedPassword) -> String userId`
- `updatePhotoIds(userId, idPhotoId, selfiePhotoId)`
- `existsByEmail(email)`
- `findByEmail(email)`
- `getPublicKey(userId)`
- `searchUsersPage(query, excludeUserId, limit, cursorFullName, cursorUserId)`

---

## SessionDAO

### Scope
- Session creation, validation, and revocation in `sessions`.

### Key methods
- `createSession(userId) -> sessionId`
- `getUserIdForSession(sessionId) -> userId|null`
- `revokeSession(sessionId)`

### Note
- `jwt_token` is currently persisted as a placeholder string while session ID is the active bearer credential.

---

## FileUploadDAO

### Scope
- Stores encrypted registration photos (`EncryptedFileDTO`) in `file_uploads`.
- Used by registration flow for ID/selfie evidence.

### Key method
- `insert(EncryptedFileDTO dto, String uploaderId) -> fileId`

---

## AttachmentDAO

### Scope
- Chunked encrypted chat attachments using:
  - `message_attachments`
  - `message_attachment_chunks`

### Upload lifecycle methods
- `initUpload(...) -> UploadInitResult`
- `storeChunk(...) -> ChunkStoreResult`
- `completeUpload(...) -> CompletionResult`
- `bindUploadToEnvelope(...) -> BindResult`
- `loadForRecipient(...) -> DownloadBlob`
- `deleteExpiredUploads() -> int`

---

## Security and correctness rules
- DAOs use `PreparedStatement` throughout.
- Encrypted payload bytes are stored opaquely (no decryption in server DB layer).
- Expiry is enforced by TTL/`expires_at` queries plus scheduled cleanup jobs.
- Errors are wrapped in `DatabaseOperationException` (or specific domain exceptions) and audited by callers.
