# KEYSTORE_PROVIDER

### Purpose
- Provides client-side key lookup for local X25519 private key and recipient public keys, backed by `UserKeystore` and directory service stub.

### Dependencies
- `UserKeystore`: management of sealed local X25519 keypairs.
- Directory service (stub): lookup recipient public keys by ID.
- `char[] passphrase`: for unsealing the local private key.

### Key retrieval flow
1. Constructor: `UserKeystore keystore, char[] passphrase, String localUserId`.
2. `getLocalPrivateKey()`:
    - `keystore.loadCurrentPrivate(root, passphrase)` → unseal with PBKDF2 + AES-256-GCM.
    - Return X25519 PrivateKey.
3. `getRecipientPublicKey(String recipientId)`:
    - Lookup in directory stub (or cache).
    - Return X25519 PublicKey or throw `KeyNotFoundException`.

### KeyStore integration
- The `UserKeystore` stores X25519 keypairs in PEM format (public) and sealed envelope (private).
- Sealed format: `v1.b64salt.b64iv.b64(ciphertext+tag)`.
- Unsealing: PBKDF2(passphrase, salt, 200k iterations) → AES-256-GCM decrypt.

### Security rules
- Passphrase **never** stored—only in memory and zeroed after use.
- Private key unsealed on-demand and zeroed immediately after use (where feasible).
- Directory cache: short TTL (5 min) for recipient pubkeys, validate fingerprint.
- KeyNotFoundException → user-visible error, no fallback to weak keys.
- Log only key IDs (SHA-256 fingerprint), not key material.