# CRYPTO_SERVICE

## Purpose
Describe implemented shared crypto primitives and message crypto orchestration.

## Current Implementation
- `CryptoService`: AES-GCM key/IV generation and encrypt/decrypt helpers.
- `CryptoECC`: X25519 key agreement and AES-key derivation.
- `MessageEncryptor`: builds encrypted envelope DTOs from plaintext payload.
- `MessageDecryptor`: validates/decrypts envelopes using recipient private key.
- `CryptoService` enforces IV length checks and uses `AES/GCM/NoPadding` with configurable AAD input.

## Key Types/Interfaces
- `shared.crypto.CryptoService`
- `shared.crypto.CryptoECC`
- `shared.crypto.MessageEncryptor`
- `shared.crypto.MessageDecryptor`

## Flow
1. Encryptor generates ephemeral keypair + derives AES key + computes AAD + encrypts payload.
2. DTO carries metadata, iv, ephemeral public key, ciphertext, and detached tag.
3. Decryptor validates envelope, checks expiry, derives AES key, rebuilds AAD, and decrypts.
4. AEAD failures are converted to tamper-specific exceptions in decrypt path.

## Error/Security Notes
- AES-GCM tag mismatch surfaces as `MessageTamperedException`.
- Expired envelopes surface as `MessageExpiredException`.
- Crypto failures raise typed exceptions and should not leak secret material.

## Related Files
- `shared/src/main/java/com/haf/shared/crypto/CryptoService.java`
- `shared/src/main/java/com/haf/shared/crypto/CryptoECC.java`
- `shared/src/main/java/com/haf/shared/crypto/MessageEncryptor.java`
- `shared/src/main/java/com/haf/shared/crypto/MessageDecryptor.java`
