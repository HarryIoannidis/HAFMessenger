### Purpose
- Set canonical AAD so that encrypt and decrypt use the same bytes for metadata authentication (AEAD).

### Field and Row
- version: string.
- algorithm: string.
- senderId: string.
- recipientId: string.
- timestampEpochMs: long (ms since epoch).
- ttlSeconds: int.
- contentType: string.
- contentLength: long.

### Coding
- Strings: UTF-8 with 4-byte length-prefix (big-endian), followed by bytes of content.
- Numbers: long/int as big-endian binaries in ByteBuffer.

### Rules
- Null is forbidden on strings; use an empty sequence of 0 length where necessary.
- No normalization beyond UTF-8; same input → same bytes.
- decrypt ignores m.aadB64 and rebuilds AAD from the DTO's meta fields.

### Test vectors
- Two equal DTOs → same AAD (byte-wise).
- Change any field → a different AAD, decrypt fails (tag error).