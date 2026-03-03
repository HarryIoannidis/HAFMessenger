# DTO

## EncryptedMessage

### Purpose
- Wire protocol DTO for E2E encrypted messages.

### Fields
- `String version`: protocol version (`"1"`).
- `String senderId`: sender ID.
- `String recipientId`: recipient ID.
- `long timestampEpochMs`: Unix timestamp in ms.
- `long ttlSeconds`: TTL (60-86400).
- `String algorithm`: `"AES-256-GCM+RSA-OAEP"` (legacy wire string).
- `String ivB64`: IV (12 bytes) in Base64.
- `String ephemeralPublicB64`: sender's ephemeral X25519 public key (DER) in Base64.
- `String ciphertextB64`: encrypted payload in Base64.
- `String tagB64`: GCM tag (16 bytes) in Base64.
- `String contentType`: MIME type.
- `long contentLength`: plaintext length in bytes.
- `boolean e2e`: always `true`.
- `@JsonIgnore String aadB64`: AAD (not serialized, reconstructed in decrypt).

---

## EncryptedFileDTO

### Purpose
- Holds the result of client-side AES-256-GCM file encryption for E2E encrypted file uploads.

### Fields
- `String ciphertextB64`: AES-GCM ciphertext (Base64).
- `String ivB64`: 12-byte GCM IV (Base64).
- `String tagB64`: 16-byte GCM authentication tag (Base64).
- `String ephemeralPublicB64`: sender's ephemeral X25519 public key (Base64/DER).
- `String contentType`: MIME type of the original file.
- `long originalSize`: size in bytes of the plaintext file.

### Usage
- Embedded in `RegisterRequest` for encrypted ID photo and selfie.
- Server stores opaquely in `file_uploads` without the AES session key.

---

## LoginRequest

### Purpose
- Login request DTO sent by the client.

### Fields
- `String email`: user email address.
- `String password`: plaintext password (sent over TLS, server verifies against BCrypt hash).

---

## LoginResponse

### Purpose
- Login response DTO returned by the server.

### Fields
- Server acknowledgement of login success/failure with session token or error message.

---

## RegisterRequest

### Purpose
- Registration request DTO sent by the client.

### Fields
- `String fullName`: user's full name.
- `String regNumber`: registration number.
- `String idNumber`: ID card number.
- `String rank`: military rank.
- `String telephone`: phone number.
- `String email`: email address.
- `String password`: plaintext password (sent over TLS, server hashes before storage).
- `String publicKeyPem`: user's X25519 public key in PEM format.
- `String publicKeyFingerprint`: SHA-256 fingerprint of the public key.
- `EncryptedFileDTO idPhoto`: E2E-encrypted ID card photo.
- `EncryptedFileDTO selfiePhoto`: E2E-encrypted selfie photo.

---

## RegisterResponse

### Purpose
- Registration response DTO returned by the server.

### Fields
- Server acknowledgement of registration success/failure with user ID or error message.

---

## KeyMetadata

### Purpose
- Metadata for X25519 keypairs.

### Fields
- `String keyId`: unique identifier.
- `String fingerprint`: SHA-256 of the public key.
- `long createdAtMs`: creation timestamp.