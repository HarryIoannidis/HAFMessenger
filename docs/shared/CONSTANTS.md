# CONSTANTS

## Purpose

Summarize core protocol/crypto constants used across client and server.

## Current Implementation

- `CryptoConstants` defines AES-GCM, X25519/XDH, Argon2, and salt/tag/IV sizes.
- `MessageHeader` defines wire protocol constants and core message policy:
  - version/algo
  - identity and TTL bounds
  - ciphertext size limit
  - built-in content-type constants used by text and attachment envelopes
- `AttachmentConstants` defines default attachment size/chunk policy, binary attachment header names, and the attachment MIME allowlist, which defaults to `*/*`.
- Attachment defaults are `10 MiB` max, `2 MiB` inline max, and `4 MiB` binary chunk size unless server env overrides them.
- Notable policy bounds include TTL `60..86400` seconds and ciphertext Base64 max length `16 MiB` equivalent (`MAX_CIPHERTEXT_BASE64`).

## Key Types/Interfaces

- `shared.constants.CryptoConstants`
- `shared.constants.MessageHeader`
- `shared.constants.AttachmentConstants`

## Flow

1. Validators/encryptors use constants to enforce policy.
2. Server config may override some attachment defaults at runtime.
3. Client/server remain aligned through shared constants.

## Error/Security Notes

- Constant changes are protocol-impacting and require synchronized client/server rollout.
- Content-type allowlist is enforced during validation.
- Attachment defaults and binary header names are shared across modules to prevent client/server drift in upload/download transport.

## Related Files

- `shared/src/main/java/com/haf/shared/constants/CryptoConstants.java`
- `shared/src/main/java/com/haf/shared/constants/MessageHeader.java`
- `shared/src/main/java/com/haf/shared/constants/AttachmentConstants.java`
