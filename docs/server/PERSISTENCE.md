## Persistence

### Purpose
- Database access layer for message_envelopes and related persistence concerns.

***

## EnvelopeDAO

### Purpose
- JDBC-based persistence for `message_envelopes` table.

### Responsibilities
- Insert validated `EncryptedMessage` into DB.
- Fetch envelopes by recipient for delivery.
- Mark delivered and delete expired envelopes (TTL cleanup).
- Thread-safe via HikariCP connection pooling.

### Database schema (current)
- Table: `message_envelopes`
    - `envelope_id` VARCHAR(64) PRIMARY KEY
    - `sender_id` VARCHAR(64) NOT NULL
    - `recipient_id` VARCHAR(64) NOT NULL
    - `encrypted_payload` LONGBLOB NOT NULL
    - `wrapped_key` BLOB NOT NULL
    - `iv` VARBINARY(12) NOT NULL
    - `auth_tag` VARBINARY(16) NOT NULL
    - `aad_hash` VARCHAR(64) NOT NULL
    - `content_type` VARCHAR(100) NOT NULL
    - `content_length` INT NOT NULL
    - `timestamp` BIGINT NOT NULL
    - `ttl` INT NOT NULL
    - `delivered` BOOLEAN DEFAULT FALSE
    - `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    - `expires_at` TIMESTAMP NOT NULL

Notes:
- `content_length` is INT (32-bit). DTO uses `long`.
- Server stores encrypted payload and metadata only. It does not decrypt content.

### insert(EncryptedMessage) → QueuedEnvelope
- Performs INSERT with all fields.
- Computes `expires_at` from `timestamp + ttl`.
- Returns hydrated `QueuedEnvelope` DTO for routing.
- Wraps SQL errors in `IllegalStateException` and logs via AuditLogger.

### fetch(recipientId, limit) → List<QueuedEnvelope>
- `WHERE recipient_id = ? AND delivered = FALSE AND expires_at > NOW()` ordered by `timestamp`.
- Hydrates `EncryptedMessage` fields from row.

### markDelivered(envelopeIds) → boolean
- Sets `delivered = TRUE` for the given IDs.

### deleteExpired() → int deletedCount
- Deletes rows with `expires_at < NOW()`.

***

## EncryptedMessage Validation (ingress)

### Purpose
- Pre-persistence validation for `EncryptedMessage` payloads before persistence.

### Rules (summary)
- Required fields present and non-empty.
- Base64 fields decodable; IV=12 bytes, tag=16 bytes.
- TTL within bounds; not expired.
- ContentType allowlist.
- `algorithm` must match `MessageHeader.ALGO_AEAD`.
