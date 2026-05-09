# VALIDATION

## Purpose

Describe implemented validation rules for encrypted message envelopes.

## Current Implementation

- Primary validator: `MessageValidator.validate(...)`.
- Additional helpers:
  - `validateOrCollectErrors(...)`
  - `validateRecipientOrThrow(...)`
- Validation covers protocol version/algo, ids, ttl, timestamp, content type, base64 fields, and size constraints.
- Cryptographic signature checks are enforced in runtime ingress/receive paths (`MessageSignatureService`), not in `MessageValidator` itself.
- Error codes include targeted categories (`BAD_VERSION`, `BAD_TTL`, `BAD_IV`, `BAD_TAG`, `BAD_CONTENT_TYPE`, etc.) for diagnostics and tests.

## Key Types/Interfaces

- `shared.utils.MessageValidator`
- `shared.utils.MessageValidator.ErrorCode`
- `shared.constants.MessageHeader`
- `shared.exceptions.MessageValidationException`

## Flow

1. Validate DTO and collect policy violations.
2. Throw/propagate validation exception when violations exist.
3. Recipient-specific check is applied before decrypt in receive paths.
4. Ed25519 signature + signing-key fingerprint are verified before decrypt/accept.
5. Callers map validation failures to HTTP/client-safe error surfaces.

## Error/Security Notes

- Validation is required before encrypt send and before decrypt receive.
- Content-type allowlist and TTL bounds are enforced consistently.
- Recipient mismatch is treated as hard failure.
- Signature verification is mandatory for protocol `v1` message acceptance.

## Related Files

- `shared/src/main/java/com/haf/shared/utils/MessageValidator.java`
- `shared/src/main/java/com/haf/shared/constants/MessageHeader.java`
- `shared/src/main/java/com/haf/shared/exceptions/MessageValidationException.java`
