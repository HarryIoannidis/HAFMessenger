# INGRESS

## Purpose

Describe implemented HTTPS and websocket ingress surfaces and request handling behavior.

## Current Implementation

- HTTPS ingress (`HttpIngressServer`) binds contexts under `/api/v1/...` including messages, auth, search, contacts, health, config, and attachments.
- Websocket ingress (`WebSocketIngressServer`) manages authenticated real-time channel behavior in dev mode.
- Ingress integrates validator, rate limiter, mailbox router, DAOs, audit, and metrics.
- Core HTTPS contexts include `/messages` (`POST` ingress, `GET` polling fetch), `/messages/ack` (`POST` ACK), `/register`, `/login`, `/token/refresh`, `/logout`, `/users`, `/search`, `/contacts`, `/attachments`, and config/health endpoints.

## Key Types/Interfaces

- `server.ingress.HttpIngressServer`
- `server.ingress.WebSocketIngressServer`
- `server.handlers.EncryptedMessageValidator`
- `server.router.RateLimiterService`
- `server.router.MailboxRouter`

## Flow

1. Request enters HTTPS endpoint with security headers and request id; WSS endpoint is present only in dev mode.
2. Session/auth checks run for protected routes using `Authorization: Bearer <access-jwt>` and authenticated HTTPS paths touch session activity timestamps.
3. Envelope payloads are validated and rate-limited.
4. Router/DAO path persists, dispatches, and acknowledges envelopes.
5. Dev websocket paths push message/presence events and process ACK payloads; prod clients use HTTPS polling for receive/presence updates.
6. Attachment endpoints run init/chunk/complete/bind/download lifecycle.

## Error/Security Notes

- TLS is restricted to `TLSv1.3` with hardened cipher suites.
- Invalid auth/session or malformed payloads return structured errors.
- Ingress does envelope validation only; no plaintext decryption occurs server-side.
- Rate limiting applies to ingress `POST /messages` and polling endpoints (`GET /messages`, `POST /messages/ack`), and login-specific limits apply to `/login` (email+IP key).
- Security headers include HSTS, CSP, X-Content-Type-Options, and X-Frame-Options on responses.

## Related Files

- `server/src/main/java/com/haf/server/ingress/HttpIngressServer.java`
- `server/src/main/java/com/haf/server/ingress/WebSocketIngressServer.java`
- `server/src/main/java/com/haf/server/handlers/EncryptedMessageValidator.java`
