# EXCEPTIONS

## Purpose
Document shared exception types used by crypto, validation, JSON, and keystore flows.

## Current Implementation
Shared exception classes:
- `CryptoOperationException`
- `JsonCodecException`
- `KeyNotFoundException`
- `KeystoreOperationException`
- `MessageExpiredException`
- `MessageTamperedException`
- `MessageValidationException`
- Exceptions are used as shared contracts so client and server can map failures consistently without string parsing.

## Key Types/Interfaces
- `shared.exceptions.*`
- `shared.utils.MessageValidator.ErrorCode` (used by `MessageValidationException`)

## Flow
1. Validation/crypto/keystore/json components throw typed exceptions.
2. Client/server map these into user-facing or HTTP error responses.
3. Security-sensitive errors are logged without secret material.
4. Tests assert specific exception types on negative paths.

## Error/Security Notes
- `MessageTamperedException` indicates integrity failure and should be treated as security-significant.
- `MessageValidationException` carries explicit error-code list for diagnostics.
- `KeyNotFoundException` is expected for missing recipient/public-key states.

## Related Files
- `shared/src/main/java/com/haf/shared/exceptions`
- `shared/src/main/java/com/haf/shared/utils/MessageValidator.java`
- `shared/src/main/java/com/haf/shared/crypto/MessageDecryptor.java`
