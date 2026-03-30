# CODECS

## Purpose

Describe implemented serialization/encoding helpers in shared module.

## Current Implementation

- `JsonCodec`: strict JSON serialization/deserialization with Jackson (`FAIL_ON_UNKNOWN_PROPERTIES`, non-null inclusion).
- `AadCodec`: deterministic metadata-to-bytes encoding for AES-GCM AAD.
- `PemCodec` + `EccKeyIO`: PEM/DER conversion and X25519 key IO helpers.
- `JsonCodec` wraps failures in `JsonCodecException` so client/server layers handle a shared error type.

## Key Types/Interfaces

- `shared.utils.JsonCodec`
- `shared.crypto.AadCodec`
- `shared.utils.PemCodec`
- `shared.utils.EccKeyIO`

## Flow

1. DTOs are serialized/deserialized by `JsonCodec` on client/server boundaries.
2. Key material is converted using PEM/DER helpers.
3. AAD bytes are computed from DTO metadata before encrypt/decrypt.
4. Parsing/encoding failures bubble as typed exceptions for consistent error mapping.

## Error/Security Notes

- JSON failures raise `JsonCodecException`.
- Unknown JSON fields are rejected by default.
- PEM/key parsing failures propagate as typed exceptions or security exceptions.

## Related Files

- `shared/src/main/java/com/haf/shared/utils/JsonCodec.java`
- `shared/src/main/java/com/haf/shared/utils/PemCodec.java`
- `shared/src/main/java/com/haf/shared/utils/EccKeyIO.java`
- `shared/src/main/java/com/haf/shared/crypto/AadCodec.java`
