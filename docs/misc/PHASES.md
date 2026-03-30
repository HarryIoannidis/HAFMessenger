# PHASES

## Purpose

Track implementation status by phase while separating completed functionality from future/planned work.

## Current Implementation

Completed/active areas in current codebase:

- Phase 1-4 foundations: shared wire format, validation, crypto, keystore workflows, client send/receive integration.
- Server ingress/routing/persistence: HTTPS/WSS ingress, mailbox routing, rate limiting, DAO persistence, audit/metrics.
- UI expansion: chat/search/profile/preview/settings controllers and corresponding ViewModels.
- Attachment transport: init/chunk/complete/bind/download endpoint and client integration.

## Key Types/Interfaces

- Shared: `MessageHeader`, `MessageValidator`, `MessageEncryptor`, `MessageDecryptor`, `KeyProvider`.
- Client: `MessageSender`, `MessageReceiver`, `MessagesViewModel`, `SearchViewModel`.
- Server: `HttpIngressServer`, `WebSocketIngressServer`, `MailboxRouter`, `RateLimiterService`, `DAO` classes.

## Flow

1. Shared contracts and validators define packet rules.
2. Client encrypts and transmits envelopes/attachments.
3. Server validates, routes, stores, and pushes updates.
4. Receiver decrypts, updates UI state, and acknowledges envelopes.

## Error/Security Notes

- Implemented sections above are based on current source and tests.
- Future/planned items should be explicitly tagged below instead of mixed with implemented behavior.

## Related Files

- `docs/shared/WIRE_FORMAT.md`
- `docs/server/INGRESS.md`
- `docs/server/PERSISTENCE.md`
- `docs/client/WEBSOCKET.md`
- `docs/misc/WORKFLOW.md`

## Future/Planned

- Extended operational telemetry dashboards and tracing.
- Additional deployment hardening and runbook formalization.
- Broader compliance packaging and operator-facing docs.
