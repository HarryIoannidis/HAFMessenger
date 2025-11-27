### Purpose
- Describes the encryption/decryption schema in shared: AES-256-GCM, RSA-OAEP for key wrap, detached GCM tag in DTO and canonical AAD.

### Algorithms
- Symmetric: AES-GCM with 12-byte IV (96-bit), tag 128-bit.
- Asymmetric: RSA-OAEP with SHA-256/MGF1 for wrap/unwrap of AES key.

### Feeds
- Encrypt: build DTO meta → build AAD (canonical) → AES‑GCM encrypt (combined) → split ct/tag → fill DTO (ciphertextB64, tagB64, ivB64, wrappedKeyB64).
- Decrypt: policy checks → rebuild AAD (canonical) → join ct+tag → AES-GCM decrypt → return plaintext.

### AAD (canonical)
- Fields and row: version | algo | senderId | recipientId | timestampEpochMs | ttlSeconds | contentType | contentLength.
- Encoding: UTF-8 strings with 4-byte length-prefix, numbers in big-endian (long/int).

### DTO EncryptedMessage (about encryption)
- ivB64: 12-byte IV (96-bit).
- wrappedKeyB64: AES key with RSA-OAEP.
- ciphertextB64, tagB64: detached tag (16B) from combined.
- aadB64: base64 of AAD bytes (informational).

### Safety rules
- New IV per message, powerful SecureRandom.
- Avoid key/IV reuse, fixed AAD series, check TTL before decrypt.