## Persistence

### Purpose
- Database access layer για message envelopes και pre-ingress validation logic.

***

## EnvelopeDAO

### Purpose
- JDBC-based persistence για `message_envelopes` table.

### Responsibilities
- Insert validated `EncryptedMessage` στο DB.
- Fetch envelopes by recipient για delivery.
- Delete expired envelopes (TTL cleanup).
- Thread-safe με HikariCP connection pooling.

### Database schema
- Table: `message_envelopes`
    - `envelope_id` BIGINT PRIMARY KEY AUTO_INCREMENT.
    - `sender_id` VARCHAR(255) NOT NULL.
    - `recipient_id` VARCHAR(255) NOT NULL INDEX.
    - `ciphertext_b64` TEXT NOT NULL.
    - `iv_b64` VARCHAR(255) NOT NULL.
    - `wrapped_key_b64` TEXT NOT NULL.
    - `tag_b64` VARCHAR(255) NOT NULL.
    - `algorithm` VARCHAR(50) NOT NULL.
    - `content_type` VARCHAR(100).
    - `client_timestamp_ms` BIGINT NOT NULL.
    - `ttl_seconds` INT NOT NULL.
    - `expires_at` TIMESTAMP NOT NULL INDEX.
    - `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP.

### insert(EncryptedMessage) → long envelopeId
- Εκτελεί INSERT με όλα τα fields από `EncryptedMessage`.
- Υπολογίζει `expires_at = FROM_UNIXTIME((client_timestamp_ms + ttl_seconds * 1000) / 1000)`.
- Returns auto-generated `envelope_id`.
- Throws `SQLException` σε DB errors (propagates σε caller).

### INSERT SQL
```sql
INSERT INTO message_envelopes 
  (sender_id, recipient_id, ciphertext_b64, iv_b64, wrapped_key_b64, tag_b64, 
   algorithm, content_type, client_timestamp_ms, ttl_seconds, expires_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FROM_UNIXTIME((?+?*1000)/1000));
```

### fetchByRecipient(recipientId, limit) → List<EncryptedMessage>
- SELECT με `WHERE recipient_id = ? AND expires_at > NOW() ORDER BY created_at LIMIT ?`.
- Deserialize rows σε `EncryptedMessage` DTOs.
- Returns empty list αν δεν υπάρχουν pending messages.

### SELECT SQL
```sql
SELECT envelope_id, sender_id, recipient_id, ciphertext_b64, iv_b64, 
       wrapped_key_b64, tag_b64, algorithm, content_type, 
       client_timestamp_ms, ttl_seconds
FROM message_envelopes
WHERE recipient_id = ? AND expires_at > NOW()
ORDER BY created_at ASC
LIMIT ?;
```

### deleteExpired() → int deletedCount
- DELETE με `WHERE expires_at <= NOW()`.
- Returns αριθμό deleted rows για audit logging.
- Καλείται από background TTL cleanup task.

### DELETE SQL
```sql
DELETE FROM message_envelopes
WHERE expires_at <= NOW();
```

***

## EncryptedMessageValidator

### Purpose
- Pre-persistence validation για `EncryptedMessage` payloads πριν φτάσουν στο DB.

### Responsibilities
- Structural validation (required fields).
- Base64 format validation.
- TTL expiry check.
- Payload size enforcement.

### validate(EncryptedMessage) → ValidationResult

### Validation rules
- Required fields: `senderId`, `recipientId`, `ciphertextB64`, `ivB64`, `ephemeralPublicB64`, `tagB64` όλα non-null και non-blank.
- Headers: `CLIENT_TIMESTAMP` και `TTL_MILLIS` must exist στο `message.headers`.
- Base64 validation:
    - `ciphertextB64`, `ivB64`, `ephemeralPublicB64`, `tagB64` πρέπει να είναι valid Base64 (δοκιμή decode).
    - Αν decode αποτύχει → `STRUCTURAL_INVALID`.
- TTL expiry:
    - `expiresAtMs = clientTimestamp + ttlMillis`.
    - Αν `expiresAtMs <= System.currentTimeMillis()` → `TTL_EXPIRED`.
- Payload size:
    - `ciphertextB64.length()` decoded size (Base64 → bytes) <= `ServerConfig.getMaxMessageBytes()`.
    - Αν υπερβαίνει → `PAYLOAD_TOO_LARGE`.

### ValidationResult

### Purpose
- Immutable DTO για validation outcome.

### Fields
- `boolean valid`: true αν όλα τα checks πέρασαν.
- `String reason`: null αν valid, αλλιώς error code (`STRUCTURAL_INVALID`, `TTL_EXPIRED`, `PAYLOAD_TOO_LARGE`).
- `long expiresAtMillis`: Unix timestamp σε ms για το envelope expiry (χρησιμοποιείται από router).

### Factory methods
- `ValidationResult.valid(expiresAtMillis)` → `(true, null, expiresAtMillis)`.
- `ValidationResult.invalid(reason)` → `(false, reason, 0)`.

***

## Thread safety

### EnvelopeDAO concurrency
- HikariCP pool διαχειρίζεται connections thread-safe.
- Κάθε method παίρνει connection από pool, εκτελεί query, κλείνει (try-with-resources).
- Κανένα shared mutable state, κάθε call ανεξάρτητο.

### Validator concurrency
- Stateless validator, κανένα shared state.
- Μπορεί να χρησιμοποιηθεί από multiple threads ταυτόχρονα.

***

## Error handling

### DB errors
- `SQLException` κατά INSERT/SELECT/DELETE → wrap σε `IllegalStateException` με descriptive message.
- Caller (MailboxRouter, cleanup task) audit log error και propagate ή retry.

### Validation errors
- Returns `ValidationResult.invalid(reason)` αντί για exception.
- Caller (ingress servers) convert σε HTTP `400`/`413` response.

***

## Testing

### EnvelopeDAO tests
- Mock `DataSource` και verify SQL execution.
- Integration tests με Testcontainers MySQL:
    - Insert → verify row exists.
    - FetchByRecipient → verify ordering και expiry filtering.
    - DeleteExpired → verify cleanup logic.

### Validator tests
- Unit tests για κάθε validation rule:
    - Missing fields.
    - Invalid Base64.
    - Expired TTL.
    - Oversized payload.
- No DB dependencies, pure logic testing.