# INGRESS

## Purpose
Describe implemented HTTPS and websocket ingress surfaces and request handling behavior.

## Current Implementation
- HTTPS ingress (`HttpIngressServer`) binds contexts under `/api/v1/...` including messages, auth, search, contacts, health, config, and attachments.
- Websocket ingress (`WebSocketIngressServer`) manages authenticated real-time channel behavior.
- Ingress integrates validator, rate limiter, mailbox router, DAOs, audit, and metrics.
- Core HTTPS contexts include `/messages`, `/register`, `/login`, `/logout`, `/users`, `/search`, `/contacts`, `/attachments`, and config/health endpoints.

## Key Types/Interfaces
- `server.ingress.HttpIngressServer`
- `server.ingress.WebSocketIngressServer`
- `server.handlers.EncryptedMessageValidator`
- `server.router.RateLimiterService`
- `server.router.MailboxRouter`

## Flow
1. Request enters HTTPS/WSS endpoint with security headers and request id.
2. Session/auth checks run for protected routes.
3. Envelope payloads are validated and rate-limited.
4. Router/DAO path persists, dispatches, and acknowledges envelopes.
5. Websocket paths push message/presence events and process ACK payloads.
6. Attachment endpoints run init/chunk/complete/bind/download lifecycle.

## Error/Security Notes
- TLS is restricted to `TLSv1.3` with hardened cipher suites.
- Invalid auth/session or malformed payloads return structured errors.
- Ingress does envelope validation only; no plaintext decryption occurs server-side.
- Security headers include HSTS, CSP, X-Content-Type-Options, and X-Frame-Options on responses.

## Related Files
- `server/src/main/java/com/haf/server/ingress/HttpIngressServer.java`
- `server/src/main/java/com/haf/server/ingress/WebSocketIngressServer.java`
- `server/src/main/java/com/haf/server/handlers/EncryptedMessageValidator.java`
