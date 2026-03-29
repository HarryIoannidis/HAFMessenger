# DECRYPTION

## Purpose
Document the implemented shared decryption path for `EncryptedMessage`.

## Current Implementation
- `MessageDecryptor.decryptMessage(...)` is the main decrypt entrypoint.
- It enforces validation, expiry checks, X25519 key agreement, AAD rebuild, and AES-GCM decrypt.
- Sender ephemeral key is reconstructed from `ephemeralPublicB64` as X509-encoded XDH public key bytes.

## Key Types/Interfaces
- `shared.crypto.MessageDecryptor`
- `shared.crypto.CryptoECC`
- `shared.crypto.CryptoService`
- `shared.utils.MessageValidator`

## Flow
1. Validate envelope structure/policy.
2. Check TTL expiry against injected `ClockProvider`.
3. Decode IV/ciphertext/tag/ephemeral public key fields.
4. Recombine ciphertext + detached tag for crypto primitive input.
5. Derive session key and rebuild canonical AAD.
6. Decrypt with AES-GCM and return plaintext bytes.

## Error/Security Notes
- Tag/auth failures are wrapped as `MessageTamperedException`.
- Expiry failures raise `MessageExpiredException` before decrypt.
- Validation failures stop processing early.

## Related Files
- `shared/src/main/java/com/haf/shared/crypto/MessageDecryptor.java`
- `shared/src/main/java/com/haf/shared/utils/MessageValidator.java`
- `shared/src/main/java/com/haf/shared/crypto/AadCodec.java`
