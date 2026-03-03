# DECRYPTION

### Purpose
- Describes the EncryptedMessage decryption stream with policy checking, canonical AAD, X25519 ECDH key derivation, and AES-GCM.

### Algorithms
- X25519 ECDH key agreement + SHA-256 KDF for deriving the AES session key.
- AES-GCM with 12-byte IV (96-bit) and 128-bit tags.

### Feeds
- Validate: version, algorithm, recipient binding, timestamp/ttl, IV/tag lengths, content policy.
- Reconstruct ephemeral public key: decode `ephemeralPublicB64` DER → `KeyFactory.getInstance("XDH").generatePublic()`.
- Derive AES key: `CryptoECC.generateAndDeriveAesKey(recipientPrivate, ephemeralPublic)` → ECDH + SHA-256.
- Rebuild AAD: canonical AAD from meta fields (version|algorithm|senderId|recipientId|timestampEpochMs|ttlSeconds|contentType|contentLength).
- Join: ct || tag in combined.
- Decrypt: AES-GCM decrypt with iv, key, AAD, combined → plaintext.

### Errors
- Schema/policy violations: reject before decrypt.
- AEADBadTagException: AAD/IV/ciphertext/tag corruption or wrong key → `MessageTamperedException`.

### Notes
- aadB64 is informational; the AAD is always reconstructed from the DTO's meta fields for determinism.
- The detached tag is transferred separately in the DTO; Combined only exists in the Crypto Layer.
- Ephemeral public key is the sender's X25519 key generated per-message (not a wrapped symmetric key).