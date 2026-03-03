## EncryptedMessage

### Purpose
- Wire protocol DTO for E2E encrypted messages.

### Fields
- `String version`: protocol version (`"1"`).
- 'String senderId': sender ID.
- 'String recipientId': recipient ID.
- 'long timestampEpochMs': Unix timestamp in ms.
- `long ttlSeconds`: TTL (60-86400).
- `String algorithm`: `"AES-256-GCM+RSA-OAEP"`.
- 'String ivB64': IV (12 bytes) in Base64.
- 'String ephemeralPublicB64': wrapped AES key in Base64.
- 'String ciphertextB64': encrypted payload in Base64.
- 'String tagB64': GCM tag (16 bytes) in Base64.
- `String contentType`: MIME type.
- 'long contentLength': plaintext length in bytes.
- `boolean e2e`: always `true`.
- '@JsonIgnore String aadB64': AAD (not serialized, reconstructed in decrypt).

***

## KeyMetadata

### Purpose
- Metadata for RSA keypairs.

### Fields
- `String keyId`: unique identifier.
- 'String fingerprint': SHA-256 of the public key.
- 'long createdAtMs': creation timestamp.