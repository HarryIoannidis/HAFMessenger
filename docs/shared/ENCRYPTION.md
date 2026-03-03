# ENCRYPTION

### Purpose
- Describes the encryption schema in shared: AES-256-GCM with X25519 ECDH key agreement, detached GCM tag in DTO and canonical AAD.

### Algorithms
- Symmetric: AES-GCM with 12-byte IV (96-bit), tag 128-bit.
- Asymmetric: X25519 ECDH key agreement + SHA-256 KDF for deriving the AES session key.

### Feeds
- Encrypt: generate ephemeral X25519 keypair → ECDH(ephemeral_private, recipient_public) → SHA-256 KDF → AES key → build DTO meta → build AAD (canonical) → AES-GCM encrypt (combined) → split ct/tag → fill DTO (ciphertextB64, tagB64, ivB64, ephemeralPublicB64).
- Decrypt: policy checks → reconstruct ephemeral public key from DER → ECDH(recipient_private, ephemeral_public) → SHA-256 KDF → AES key → rebuild AAD (canonical) → join ct+tag → AES-GCM decrypt → return plaintext.

### AAD (canonical)
- Fields and row: version | algo | senderId | recipientId | timestampEpochMs | ttlSeconds | contentType | contentLength.
- Encoding: UTF-8 strings with 4-byte length-prefix, numbers in big-endian (long/int).

### DTO EncryptedMessage (about encryption)
- ivB64: 12-byte IV (96-bit).
- ephemeralPublicB64: sender's ephemeral X25519 public key (DER, Base64).
- ciphertextB64, tagB64: detached tag (16B) from combined.
- aadB64: base64 of AAD bytes (informational).

### Safety rules
- New ephemeral X25519 keypair per message (perfect forward secrecy).
- New IV per message, strong SecureRandom.
- Avoid key/IV reuse, fixed AAD series, check TTL before decrypt.