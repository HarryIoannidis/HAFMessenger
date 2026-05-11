# INGRESS

## Purpose

Describe implemented HTTPS REST ingress and WSS realtime ingress behavior.

## Current Implementation

- HTTPS ingress (`HttpIngressServer`) binds REST contexts under the configured HTTPS path (default `/api/v1/...`) for auth, users, search, contacts, health, config, and attachments.
- WSS ingress (`RealtimeWebSocketServer`) binds to the configured WebSocket path (default `/ws/v1/realtime`) for live messages, receipts, typing, presence, heartbeat, and server push.
- REST and WSS ingress share security services, DAOs, audit, and rate limiting where appropriate.

## Key Types/Interfaces

- `server.ingress.HttpIngressServer`
- `server.realtime.RealtimeWebSocketServer`
- `server.realtime.MessageIngressService`
- `server.handlers.EncryptedMessageValidator`
- `server.router.RateLimiterService`
- `server.router.MailboxRouter`

## Flow

1. REST requests enter HTTPS endpoints with security headers and request ids.
2. Protected REST routes and WSS events use `Authorization: Bearer <access-jwt>` and touch session activity timestamps.
3. WSS message envelopes are validated, rate-limited, replay-checked, signature-checked, and routed without server-side decryption.
4. WSS receipts are ownership-scoped before delivery/read state changes are routed to the sender.
5. Attachment endpoints run init/chunk/complete/bind/download lifecycle; chunk upload and blob download use binary bodies, while lifecycle metadata stays JSON.

## Error/Security Notes

- TLS is restricted to `TLSv1.3` with hardened cipher suites.
- Invalid auth/session or malformed payloads return structured errors.
- Auth failures distinguish generic invalid sessions (`invalid session`) from forced-session takeover revocation (`session revoked by takeover`) so clients can show specific logout UX.
- WSS ingress does envelope validation only; no plaintext decryption occurs server-side.
- Registration and takeover flows require both encryption and signing public keys plus matching fingerprints.
- Rate limiting applies to WSS per user/socket/event type, WSS message sends consume the existing message-send limit, and login-specific limits apply to `/login` (email+IP key).
- Binary attachment chunks require `Content-Type: application/octet-stream` and `X-Attachment-Chunk-Index`.
- Binary attachment downloads return `application/octet-stream` plus `X-Attachment-Id`, `X-Attachment-Encrypted-Size`, `X-Attachment-Chunk-Count`, and `X-Attachment-Content-Type`.
- Security headers include HSTS, CSP, X-Content-Type-Options, and X-Frame-Options on responses.

## Related Files

- `server/src/main/java/com/haf/server/ingress/HttpIngressServer.java`
- `server/src/main/java/com/haf/server/realtime/RealtimeWebSocketServer.java`
- `server/src/main/java/com/haf/server/realtime/MessageIngressService.java`
- `server/src/main/java/com/haf/server/handlers/EncryptedMessageValidator.java`
