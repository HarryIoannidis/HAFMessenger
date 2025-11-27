### Purpose
- Describes the EncryptedMessage decryption stream with policy checking, canonical AAD, and AES-GCM.

### Algorithms
- RSA-OAEP SHA-256/MGF1 for symmetric key unwrap.
- AES-GCM with 12-byte IV (96-bit) and 128-bit tags.

### Feeds
- Validate: version, algo, recipient binding, timestamp/ttl, IV/tag lengths, content policy.
- Rebuild AAD: canonical AAD from meta fields (version|algo|senderId|recipientId|timestampEpochMs|ttlSeconds|contentType|contentLength).
- Join: ct || tag in combined.
- Unwrap: RSA-OAEP unwrap of AES key.
- Decrypt: AES-GCM decrypt with iv, key, AAD, combined → plaintext.

### Errors
- Schema/policy violations: reject before decrypt.
- AEADBadTagException: AAD/IV/ciphertext/tag corruption or wrong key.

### Notes
- aadB64 is informational; the AAD is always reconstructed by the DTO for determinism.
- The detached tag is transferred separately to the DTO; Combined only exists in the Crypto Layer.