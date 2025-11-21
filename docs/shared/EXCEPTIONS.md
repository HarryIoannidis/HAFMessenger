## KeyNotFoundException

### Purpose
- Key (private or public) was not found in the keystore or cache.

### Fields
- 'String userId': the user's ID.
- 'String keyType': ''private'' or ''public''.

### When is it thrown
- 'UserKeyStore.getPrivateKey(userId)' → missing key.
- 'KeyProvider.getPublicKey(recipientId)' → fetch failed.
- `MessageEncryptor.encrypt(...)' → recipient key does not exist.

### Handling
- Client: fetch retry, error display.
- Server: HTTP 404, log incident.
- We don't do silent fallback in insecure mode.

---

## MessageExpiredException

### Purpose
- Message has exceeded TTL (timestamp + ttl < now).

### Fields
- 'long timestampMs': message timestamp.
- 'int ttlSeconds': TTL in seconds.
- 'long currentTimeMs': control time.

### When is it thrown
- `MessageValidator.validateNotExpired(m, clock)` → expired.
- `DefaultMessageReceiver.handleIncomingMessage(...)→ expiry check before decrypt.
- Server scheduler → automatic deletion from DB.

### Handling
- Client/Server: discard, log.
- **Security**: expiry check **before** decrypt (avoid replay attacks).

***

## MessageTamperedException

### Purpose
- Message corrupted after encryption (GCM tag mismatch).

### Fields
- 'String messageId': Message ID.
- 'String senderId': Sender ID.
- 'String recipientId': Recipient ID.

### When is it thrown
- 'MessageDecryptor.decrypt(m)' → 'AEADBadTagException' from 'Cipher.doFinal()'.
- AAD mismatch: different AAD in decrypt.
- Change metadata (timestamp, senderId, algo) after encrypt.

### Handling
- **CRITICAL**: log incident with metadata (not payload), security audit flag.
- Client: show security error, discard.
- Server: log, block sender if many tampered messages.
- **We don't** retry - tampering = security incident.

***

## MessageValidationException

### Purpose
- Message does not meet structural constraints (null fields, invalid values).

### Fields
- 'String fieldName': field that failed.
- 'String reason': description of an error.
- 'Object invalidValue': invalid value.

### When is it thrown
- `MessageValidator.validateEncryptedMessage(m)`:
    - Null/empty: senderId, recipientId, ciphertextB64, encryptedKeyB64, ivB64, tagB64.
    - Invalid Base64 encoding.
    - 'ttlSeconds <= 0' or 'ttlSeconds > MAX_TTL'.
    - 'timestampEpochMs' in the future or > 30 days old.
    - Invalid 'algo' (not ''AES-256-GCM'') or 'version' (not ''v1'').
- `MessageValidator.validateRecipientOrThrow(localId, m)` → recipient mismatch.

### Handling
- Client: display validation error, log.
- Server: HTTP 400, log.
- **Fast-fail**: validation before serialization/processing.

***

## Exception Hierarchy

```
Exception
  ├── RuntimeException
  │     ├── KeyNotFoundException
  │     ├── MessageExpiredException
  │     └── MessageTamperedException
  └── IllegalArgumentException
        └── MessageValidationException
```

***

## Rules

### Logging levels
- `MessageTamperedException` → **CRITICAL** (security audit).
- `KeyNotFoundException` → **ERROR**.
- `MessageExpiredException` → **WARN** (expected behavior).
- `MessageValidationException` → **WARN**.

### Security
- Expiry check **before** decrypt (replay protection).
- Tampering → immediate log, discard, potential block.
- Validation **before** deserialization (fast-fail).
- We do not disclose crypto details in error messages to a user.

### Error messages to user
- `KeyNotFoundException`: "Unable to send message: recipient's encryption key not available."
- `MessageExpiredException`: "Message expired."
- `MessageTamperedException`: "Message integrity check failed."
- `MessageValidationException`: "Invalid message format."