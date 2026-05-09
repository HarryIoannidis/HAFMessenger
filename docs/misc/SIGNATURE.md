# SIGNATURE

## Purpose

Describe how message signatures are generated, transmitted, validated, and persisted in the current HAFMessenger implementation.

## Current Implementation

- Signature scheme: Ed25519 (`CryptoConstants.ED25519_SIGNATURE_ALGO`, `MessageHeader.ALGO_SIGNATURE`).
- Each user has two keypairs:
  - Encryption keypair (X25519) for payload encryption.
  - Signing keypair (Ed25519) for detached message signatures.
- Outbound envelopes are signed by `MessageSignatureService.sign(...)` after encryption.
- Signature fields carried on `EncryptedMessage`:
  - `signatureAlgorithm`
  - `senderSigningKeyFingerprint`
  - `signatureB64`
- Server ingress verifies signatures before accepting envelopes.
- Client receiver verifies signature and signing-fingerprint binding before decrypting.
- Signing public key/fingerprint are part of registration, user key lookup, and takeover-key rotation.

## Key Types/Interfaces

- `shared.crypto.MessageSignatureService`
- `shared.utils.SigningKeyIO`
- `shared.dto.EncryptedMessage`
- `shared.keystore.KeyProvider`
- `client.crypto.UserKeystoreKeyProvider`
- `server.db.UserDAO` (`PublicKeyRecord`)
- `shared.responses.PublicKeyResponse`

## Flow

1. Registration generates X25519 + Ed25519 pairs and sends both public keys + fingerprints.
2. Server validates fingerprints against submitted PEM keys and stores both key types on `users`.
3. Sender encrypts payload to envelope, then signs canonical envelope bytes with local Ed25519 private key.
4. Sender includes signature algorithm, sender signing fingerprint, and signature bytes on the envelope.
5. Ingress authenticates sender identity, loads sender signing public key from DB, validates fingerprint binding, and verifies Ed25519 signature.
6. Envelope is persisted including signature columns in `message_envelopes`.
7. Receiver resolves sender signing public key, re-checks fingerprint binding, verifies signature, then decrypts.

## Error/Security Notes

- Unsigned envelopes or invalid signatures are rejected on ingress (`400 invalid message signature`).
- Fingerprint mismatch (message vs stored sender signing key) causes reject before signature verify.
- Receiver rejects tampered messages before decrypt if fingerprint/signature validation fails.
- Takeover login requires both encryption and signing key material; server validates both fingerprints.
- Schema hardening migrations:
  - `V16` adds required signing-key columns to `users`.
  - `V17` adds required signature columns to `message_envelopes`.

## Related Files

- `shared/src/main/java/com/haf/shared/crypto/MessageSignatureService.java`
- `shared/src/main/java/com/haf/shared/utils/SigningKeyIO.java`
- `shared/src/main/java/com/haf/shared/dto/EncryptedMessage.java`
- `shared/src/main/java/com/haf/shared/keystore/KeyProvider.java`
- `client/src/main/java/com/haf/client/network/DefaultMessageSender.java`
- `client/src/main/java/com/haf/client/network/DefaultMessageReceiver.java`
- `server/src/main/java/com/haf/server/ingress/HttpIngressServer.java`
- `server/src/main/resources/db/migration/V16__require_signing_keys_and_reset_legacy_users.sql`
- `server/src/main/resources/db/migration/V17__require_message_signatures.sql`
