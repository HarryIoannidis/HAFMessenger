# PERSISTENCE

## EnvelopeDAO

### Purpose
- Data Access Object for encrypted message envelopes in MySQL.
- Handles insert, fetch, delivery status update, and TTL-based expiration.
- Never decrypts message content (zero-knowledge storage).

### Database schema
```sql
CREATE TABLE message_envelopes (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    sender_id    VARCHAR(128)  NOT NULL,
    recipient_id VARCHAR(128)  NOT NULL,
    payload      LONGBLOB      NOT NULL,
    aad_hash     CHAR(64)      NOT NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   TIMESTAMP     NOT NULL,
    delivered    BOOLEAN       NOT NULL DEFAULT FALSE,
    INDEX idx_recipient (recipient_id, delivered, expires_at)
);
```

### Methods

#### insert(EncryptedMessageDTO dto, long expiresAtMs) → long id
- PreparedStatement with parameterized query.
- Stores full JSON payload as BLOB.
- Computes AAD hash (SHA-256 of canonical AAD bytes).
- Sets `expires_at` from `timestampEpochMs + ttlSeconds * 1000`.
- Returns generated ID.

#### fetchForRecipient(String recipientId) → List<EnvelopeRow>
- `WHERE recipient_id = ? AND delivered = FALSE AND expires_at > NOW()`.
- Returns list of pending envelopes ordered by `created_at`.

#### fetchByIds(List<Long> ids) → List<EnvelopeRow>
- `WHERE id IN (?)`.
- Batch fetch for specific envelopes.

#### markDelivered(long id) → boolean
- `UPDATE ... SET delivered = TRUE WHERE id = ?`.
- Returns true if row was updated.

#### deleteExpired() → int
- `DELETE FROM message_envelopes WHERE expires_at < NOW()`.
- Returns number of deleted rows.
- Called by scheduled cleanup job (every 5 minutes).

### Connection management
- Uses HikariCP `DataSource` for connection pooling.
- All methods use try-with-resources for `Connection` and `PreparedStatement`.
- No connection leaks: `finally` block ensures cleanup.

### Security rules
- **Zero-knowledge**: `payload` stored as opaque BLOB, never parsed or decrypted.
- **Parameterized queries**: all SQL uses `PreparedStatement` (no string concatenation).
- **AAD hash**: integrity verification without decryption.
- **Audit**: all operations logged via `AuditLogger` (no payload content in logs).

---

## Ingress validation (EncryptedMessageValidator)

### Purpose
- Structural validation of incoming envelopes before persistence.

### Checks
- Required fields: `senderId`, `recipientId`, `ciphertextB64`, `ivB64`, `tagB64`, `ephemeralPublicB64`.
- TTL: within `[MIN_TTL_SECONDS, MAX_TTL_SECONDS]`.
- Payload size: `ciphertextB64.length() <= MAX_CIPHERTEXT_BASE64`.
- Base64 fields: decodable without error.
- Timestamp: not in future, not older than `MAX_TTL_SECONDS`.

### Output
- `ValidationResult(valid, reason, expiresAtMillis)`.
- Reject codes: `STRUCTURAL_INVALID`, `TTL_EXPIRED`, `PAYLOAD_TOO_LARGE`, `MALFORMED_BASE64`.
