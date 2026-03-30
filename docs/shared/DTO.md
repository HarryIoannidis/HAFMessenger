# DTO

## Purpose

Describe current shared DTO families used across client/server boundaries.

## Current Implementation

- Core encrypted payload DTOs:
  - `EncryptedMessage`
  - `EncryptedFileDTO`
  - attachment payload DTOs
- Auth/search/contact and attachment request/response DTOs are split under:
  - `shared.requests`
  - `shared.responses`
- Metadata DTO: `KeyMetadata`.
- Request/response families cover login/register/logout, user search, contacts, messaging policy, and attachment upload/download lifecycle.

## Key Types/Interfaces

- `shared.dto.EncryptedMessage`
- `shared.dto.EncryptedFileDTO`
- `shared.dto.KeyMetadata`
- `shared.requests.*`
- `shared.responses.*`

## Flow

1. Client serializes request DTOs for HTTPS/WSS operations.
2. Server deserializes, processes, and returns response DTOs.
3. Shared DTOs guarantee consistent wire contracts between modules.
4. Codec/validator rules enforce strict field handling on boundaries.

## Error/Security Notes

- DTO validation is enforced by shared/server validators, not by DTO classes themselves.
- Sensitive payload fields remain encrypted on server-side persistence paths.

## Related Files

- `shared/src/main/java/com/haf/shared/dto`
- `shared/src/main/java/com/haf/shared/requests`
- `shared/src/main/java/com/haf/shared/responses`
