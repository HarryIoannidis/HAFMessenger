# CRYPTO

## Purpose

Describe the current end-to-end cryptography model and where each crypto responsibility lives in the codebase.

## Current Implementation

- Envelope profile is fixed by `MessageHeader`: version `1`, encryption algorithm `AES-256-GCM+X25519`, signature algorithm `Ed25519`.
- `MessageEncryptor` performs per-message encryption:
  - Generates an ephemeral X25519 keypair.
  - Derives AES key material via `CryptoECC` (X25519 ECDH + SHA-256 KDF).
  - Encrypts payload with `CryptoService.encryptAesGcm(...)` using a 12-byte IV and detached 16-byte GCM tag.
  - Builds canonical AAD through `AadCodec.buildAAD(...)` from envelope metadata.
- `MessageDecryptor.decryptMessage(...)` validates with `MessageValidator.validate(...)`, enforces TTL with `ClockProvider`, reconstructs sender ephemeral public key, then decrypts.
- `MessageSignatureService` signs outbound envelopes and verifies inbound signatures over canonical envelope bytes.
- Server ingress/router handles encrypted envelopes as opaque payloads and does not decrypt message bodies.
- Private key material is managed through `UserKeystore` and sealed at rest by `KeystoreSealing`.

## Key Types/Interfaces

- `shared.crypto.MessageEncryptor`
- `shared.crypto.MessageDecryptor`
- `shared.crypto.CryptoService`
- `shared.crypto.CryptoECC`
- `shared.crypto.AadCodec`
- `shared.utils.MessageValidator`
- `shared.constants.MessageHeader`
- `shared.dto.EncryptedMessage`
- `shared.keystore.UserKeystore`

## Flow

1. Sender resolves recipient public key via `KeyProvider.getRecipientPublicKey(...)`.
2. `MessageEncryptor.encrypt(...)` creates encrypted envelope fields (`ivB64`, `ephemeralPublicB64`, `ciphertextB64`, `tagB64`).
3. Client sends the envelope through `MessageSender` over TLS ingress.
4. Sender signs envelope with local Ed25519 signing private key and includes signing fingerprint.
5. Receiver verifies signature/fingerprint binding, then decrypts with `MessageDecryptor.decryptMessage(...)`.
6. Receiver acknowledges delivered envelope IDs using `MessageReceiver.acknowledgeEnvelopes(...)`.

## Error/Security Notes

- Validation rejects malformed fields, unsupported content types, bad TTL, and base64/size violations before decrypt.
- AEAD tag failures are wrapped as `MessageTamperedException`.
- Expired envelopes raise `MessageExpiredException`.
- Recipient mismatch checks use `MessageValidator.validateRecipientOrThrow(...)` in receive paths.
- Unsigned or invalidly signed envelopes are rejected before decrypt.
- Private keys remain sealed on disk; keystore root fallback avoids startup failure on restricted system paths.

## Related Files

- `shared/src/main/java/com/haf/shared/crypto/MessageEncryptor.java`
- `shared/src/main/java/com/haf/shared/crypto/MessageDecryptor.java`
- `shared/src/main/java/com/haf/shared/crypto/CryptoService.java`
- `shared/src/main/java/com/haf/shared/crypto/CryptoECC.java`
- `shared/src/main/java/com/haf/shared/crypto/AadCodec.java`
- `shared/src/main/java/com/haf/shared/utils/MessageValidator.java`
- `shared/src/main/java/com/haf/shared/constants/MessageHeader.java`
- `docs/shared/ENCRYPTION.md`
- `docs/shared/DECRYPTION.md`
- `docs/shared/VALIDATION.md`
- `docs/shared/KEYSTORE.md`
