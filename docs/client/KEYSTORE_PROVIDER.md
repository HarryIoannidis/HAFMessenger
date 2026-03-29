# KEYSTORE_PROVIDER

## Purpose
Document the client key-provider implementation used by messaging send/receive flows.

## Current Implementation
- Client uses `UserKeystoreKeyProvider` as the `KeyProvider` implementation.
- It wraps `UserKeystore` and exposes sender id + recipient public key resolution.
- Recipient key lookup order:
  1. local keystore metadata match
  2. optional directory-service fetcher callback (`setDirectoryServiceFetcher`)
- Default constructor path bootstraps keystore root through `KeystoreBootstrap.run(...)` before opening `UserKeystore`.

## Key Types/Interfaces
- `com.haf.client.crypto.UserKeystoreKeyProvider`
- `com.haf.shared.keystore.KeyProvider`
- `com.haf.shared.keystore.UserKeystore`
- `com.haf.shared.exceptions.KeyNotFoundException`

## Flow
1. Construct provider with sender id and passphrase.
2. Sender calls `getSenderId()` for envelope identity.
3. Encryption flow calls `getRecipientPublicKey(recipientId)`.
4. Local metadata list is scanned for matching key id before remote fetch fallback.
5. For receiver paths, provider exposes local keystore/passphrase accessors.

## Error/Security Notes
- Missing recipient keys raise `KeyNotFoundException`.
- Passphrase is cloned and never logged.
- Directory fetch failures do not silently downgrade security behavior.

## Related Files
- `client/src/main/java/com/haf/client/crypto/UserKeystoreKeyProvider.java`
- `shared/src/main/java/com/haf/shared/keystore/KeyProvider.java`
- `shared/src/main/java/com/haf/shared/keystore/UserKeystore.java`
