# WIRE_FORMAT

### Purpose
- Documents the wire format and validation policies for EncryptedMessage.

### Version and Profile
- Version: 1
- Algorithm profile: AES-256-GCM with X25519 ECDH key agreement + SHA-256 KDF
- Wire string: `"AES-256-GCM+RSA-OAEP"` (legacy, kept for backward compatibility)

### GCM Parameters
- IV (nonce): 12 bytes, ivB64 field (Base64)
- Tag: 16 bytes, tagB64 field (Base64)

### Envelope Fields (DTO)
- Required: version, senderId, recipientId, timestampEpochMs, ttlSeconds, algorithm, ivB64, ephemeralPublicB64, ciphertextB64, tagB64, contentType, contentLength
- Optional: e2e
- ephemeralPublicB64: sender's ephemeral X25519 public key in DER/Base64 (used for ECDH key agreement, not a wrapped key)
- contentLength: non-negative, equal to bytes plaintext

### Content type policy
- MIME allowlist only:
    - text/plain, text/markdown
    - image/png, image/jpeg, image/gif, image/webp
    - video/mp4, video/webm, video/ogg
    - application/pdf
    - application/vnd.openxmlformats-officedocument. (wordprocessingml.document|spreadsheetml.sheet|presentationml.presentation)
    - application/msword, application/vnd.ms-excel, application/vnd.ms-powerpoint
    - application/octet-stream
- Normalization: ignored parameters (e.g. ; charset=utf-8); Check only on the base type

### Size Limits
- MAX_CIPHERTEXT_BASE64: according to MessageHeader (e.g. 8 MB)

### Validation roles
- Client: MessageValidator.isValid() before sending
- Server: MessageValidator.validate() at ingress, reject to fail

### JSON rules
- Serialization: JsonCodec.toJson(dto)
- Deserialization: JsonCodec.fromJson(json)
- Decoding Accuracy:
    - FAIL_ON_UNKNOWN_PROPERTIES = true (fail on unknown fields)
    - Null strings rejected; An empty value is allowed only where specified
    - Checking types/bounds: numbers within bounds, Base64 fields decodable, IV/tag of exact lengths