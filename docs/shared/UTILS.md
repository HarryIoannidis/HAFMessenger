# UTILS

## Purpose
Summarize shared utility classes used by both client and server modules.

## Current Implementation
Utility groups include:
- time providers (`ClockProvider`, `SystemClockProvider`, `FixedClockProvider`)
- encoding/serialization (`JsonCodec`, `PemCodec`)
- key/material helpers (`EccKeyIO`, `FingerprintUtil`)
- policy/validation helpers (`MessageValidator`, `AttachmentPayloadCodec`)
- filesystem permissions (`FilePerms`)
- Utility implementations are reused across production and tests to keep validation/crypto behavior deterministic between modules.

## Key Types/Interfaces
- `shared.utils.ClockProvider`
- `shared.utils.JsonCodec`
- `shared.utils.EccKeyIO`
- `shared.utils.FingerprintUtil`
- `shared.utils.MessageValidator`

## Flow
1. Core crypto/network/persistence components call utility helpers.
2. Utilities provide deterministic behavior reused by tests and production code.
3. Shared utility contracts reduce duplicated logic between client and server.

## Error/Security Notes
- Utility failures are wrapped/raised via typed exceptions where applicable.
- Security-sensitive helpers (validation, key IO, file perms) are central trust points.

## Related Files
- `shared/src/main/java/com/haf/shared/utils`
- `shared/src/test/java/com/haf/shared/utils`
