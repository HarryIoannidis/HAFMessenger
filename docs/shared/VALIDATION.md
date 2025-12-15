### Purpose
- Specifies rules and control steps for EncryptedMessage before decrypting.

### Field rules
- version: equal to MessageHeader.VERSION, otherwise reject.
- algorithm: must equal `MessageHeader.ALGO_AEAD` ("AES-256-GCM+RSA-OAEP"), otherwise reject.
- senderId/recipientId: non-space, within character policy.
- timestampEpochMs: > 0.
- ttlSeconds: within [MIN_TTL_SECONDS, MAX_TTL_SECONDS].
- ivB64: decode to 12 bytes.
- wrappedKeyB64: non‑empty.
- ciphertextB64/tagB64: non‑empty, tag 16 bytes.
- contentType: acceptable whitelist values.
- contentLength: 0 ≤ length ≤ MAX_CONTENT_LENGTH.

### Time Policies
- now ≤ timestampEpochMs + ttlSeconds· otherwise "Message expired".
- Optional: skew control, e.g. now + ALLOWED_SKEW ≥ timestampEpochMs.

### Recipient binding
- Confirm that m.recipientId matches the local user/key.

### AAD canonicalization
- decrypt rebuilds AAD from DTO; Any header/meta change causes an error tag.

### Errors / exceptions
- IllegalArgumentException for schema/policy violations.
- IllegalStateException for expiry.
- AEADBadTagException for Integrity/Identity (GCM).