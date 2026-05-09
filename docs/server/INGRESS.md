# INGRESS

## Purpose

Describe implemented HTTPS ingress surfaces and request handling behavior.

## Current Implementation

- HTTPS ingress (`HttpIngressServer`) binds contexts under `/api/v1/...` including messages, auth, search, contacts, health, config, and attachments.
- Ingress integrates validator, rate limiter, mailbox router, DAOs, audit, and metrics.
- Core HTTPS contexts include `/messages` (`POST` ingress, `GET` polling fetch), `/messages/ack` (`POST` ACK), `/register`, `/login`, `/token/refresh`, `/logout`, `/users`, `/search`, `/contacts`, `/attachments`, and config/health endpoints.

## Key Types/Interfaces

- `server.ingress.HttpIngressServer`
- `server.handlers.EncryptedMessageValidator`
- `server.router.RateLimiterService`
- `server.router.MailboxRouter`

## Flow

1. Request enters HTTPS endpoint with security headers and request id.
2. Session/auth checks run for protected routes using `Authorization: Bearer <access-jwt>` and authenticated HTTPS paths touch session activity timestamps.
3. Envelope payloads are validated and rate-limited.
4. Ingress enforces `message.senderId == authenticated userId` and verifies mandatory Ed25519 signature/fingerprint before routing.
5. Router/DAO path persists, dispatches, and acknowledges envelopes.
6. Clients use HTTPS polling for receive and presence updates, and submit ACK payloads over HTTPS.
7. Attachment endpoints run init/chunk/complete/bind/download lifecycle; chunk upload and blob download use binary bodies, while lifecycle metadata stays JSON.

## Error/Security Notes

- TLS is restricted to `TLSv1.3` with hardened cipher suites.
- Invalid auth/session or malformed payloads return structured errors.
- Auth failures distinguish generic invalid sessions (`invalid session`) from forced-session takeover revocation (`session revoked by takeover`) so clients can show specific logout UX.
- Ingress does envelope validation only; no plaintext decryption occurs server-side.
- Registration and takeover flows require both encryption and signing public keys plus matching fingerprints.
- Rate limiting applies to ingress `POST /messages` and polling endpoints (`GET /messages`, `POST /messages/ack`), and login-specific limits apply to `/login` (email+IP key).
- Binary attachment chunks require `Content-Type: application/octet-stream` and `X-Attachment-Chunk-Index`.
- Binary attachment downloads return `application/octet-stream` plus `X-Attachment-Id`, `X-Attachment-Encrypted-Size`, `X-Attachment-Chunk-Count`, and `X-Attachment-Content-Type`.
- Security headers include HSTS, CSP, X-Content-Type-Options, and X-Frame-Options on responses.

## Related Files

- `server/src/main/java/com/haf/server/ingress/HttpIngressServer.java`
- `server/src/main/java/com/haf/server/handlers/EncryptedMessageValidator.java`
