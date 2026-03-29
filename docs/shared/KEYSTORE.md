# KEYSTORE

## Purpose
Describe current keystore root policy, bootstrap, and sealing behavior.

## Current Implementation
- Root selection priority:
  1. JVM property `haf.keystore.root`
  2. env `HAF_KEYSTORE_ROOT`
  3. OS preferred path (`/var/lib/haf/keystore` or `%ProgramData%\HAF\keystore`)
- Fallback path from `KeystoreRoot.userFallback()`.
- `UserKeystore` stores key directories with `public.pem`, `private.enc`, `metadata.json`.
- `KeystoreSealing` uses PBKDF2-HMAC-SHA256 + AES-GCM envelope format `v1.<salt>.<iv>.<ciphertext>`.
- `KeystoreBootstrap` can create initial key material and fallback to user path when privileged paths are unavailable.

## Key Types/Interfaces
- `shared.keystore.KeystoreRoot`
- `shared.keystore.KeystoreBootstrap`
- `shared.keystore.UserKeystore`
- `shared.keystore.KeystoreSealing`
- `shared.dto.KeyMetadata`

## Flow
1. Bootstrap resolves preferred root and falls back on permission/access errors.
2. First-run path can generate and persist initial keypair artifacts.
3. Runtime key loads open sealed private key with passphrase and metadata checks.

## Error/Security Notes
- Wrong passphrase or tampered envelope raises `KeystoreOperationException`.
- Private key remains sealed at rest.
- File permissions are hardened through `FilePerms` helpers.
- Passphrase arrays are cloned at API boundaries to reduce accidental shared mutable state.

## Related Files
- `shared/src/main/java/com/haf/shared/keystore/KeystoreRoot.java`
- `shared/src/main/java/com/haf/shared/keystore/KeystoreBootstrap.java`
- `shared/src/main/java/com/haf/shared/keystore/UserKeystore.java`
- `shared/src/main/java/com/haf/shared/keystore/KeystoreSealing.java`
