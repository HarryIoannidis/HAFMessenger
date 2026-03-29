# WIRE_FORMAT

## Purpose
Describe the current `EncryptedMessage` wire contract and policy constraints.

## Current Implementation
Required envelope fields include:
- version, senderId, recipientId
- timestampEpochMs, ttlSeconds, algorithm
- ivB64, ephemeralPublicB64, ciphertextB64, tagB64
- contentType, contentLength
Optional/auxiliary field:
- `e2e`
- Runtime-only field `aadB64` exists on DTO but is ignored by JSON (`@JsonIgnore`) and not treated as trusted wire input.

Protocol policy values come from shared constants (`MessageHeader`, `CryptoConstants`).

## Key Types/Interfaces
- `shared.dto.EncryptedMessage`
- `shared.constants.MessageHeader`
- `shared.utils.MessageValidator`
- `shared.utils.JsonCodec`

## Flow
1. Sender constructs envelope and serializes with `JsonCodec`.
2. Server/client validators enforce schema/policy.
3. Receiver reconstructs AAD from metadata and decrypts ciphertext/tag.
4. ACK paths refer to server envelope IDs rather than plaintext message identifiers.

## Error/Security Notes
- Unknown JSON properties are rejected by codec configuration.
- Content-type and ciphertext-size limits are enforced by validator policy.
- `ephemeralPublicB64` carries sender ephemeral public key (not a wrapped symmetric key field).

## Related Files
- `shared/src/main/java/com/haf/shared/dto/EncryptedMessage.java`
- `shared/src/main/java/com/haf/shared/utils/JsonCodec.java`
- `shared/src/main/java/com/haf/shared/utils/MessageValidator.java`
- `shared/src/main/java/com/haf/shared/constants/MessageHeader.java`
