# ENCRYPTION

## Purpose

Document the implemented shared encryption path for outbound payloads.

## Current Implementation

- `MessageEncryptor.encrypt(...)` creates one `EncryptedMessage` per payload.
- Uses X25519-derived AES key, random IV, canonical AAD, AES-GCM encryption, and detached tag encoding.
- IV length is 12 bytes (`MessageHeader.IV_BYTES`) and tag length is 16 bytes (`MessageHeader.GCM_TAG_BYTES`).

## Key Types/Interfaces

- `shared.crypto.MessageEncryptor`
- `shared.crypto.CryptoECC`
- `shared.crypto.CryptoService`
- `shared.dto.EncryptedMessage`

## Flow

1. Validate payload/contentType/ttl arguments.
2. Generate ephemeral keypair and derive AES key.
3. Populate envelope metadata and compute AAD.
4. Encrypt payload and split ciphertext/tag into Base64 fields.
5. Set `e2e=true` and return ready-to-send `EncryptedMessage`.

## Error/Security Notes

- Invalid inputs throw `IllegalArgumentException`.
- Crypto operation failures raise `CryptoOperationException` or wrapped exceptions.
- Detached tag and metadata binding are required for tamper detection.

## Related Files

- `shared/src/main/java/com/haf/shared/crypto/MessageEncryptor.java`
- `shared/src/main/java/com/haf/shared/crypto/CryptoService.java`
- `shared/src/main/java/com/haf/shared/crypto/AadCodec.java`
