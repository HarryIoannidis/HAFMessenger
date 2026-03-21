# INGRESS

### Purpose
- Documents the real ingress behavior implemented in `HttpIngressServer` and `WebSocketIngressServer`.
- Covers authentication, routing, rate limiting, and wire-level response behavior.

---

## HttpIngressServer

### Bound HTTPS contexts
- `POST /api/v1/messages`: authenticated encrypted message ingress.
- `POST /api/v1/register`: registration.
- `POST /api/v1/login`: login.
- `POST /api/v1/logout`: logout (session revoke).
- `GET /api/v1/users/{userId}/key`: public key lookup.
- `GET /api/v1/search`: authenticated user search (cursor pagination).
- `GET|POST|DELETE /api/v1/contacts`: contacts list/add/remove.
- `GET|HEAD /api/v1/health`: health probe.
- `GET /api/v1/config/admin-key`: optional admin public key for encrypted registration photos.
- `GET /api/v1/config/messaging`: authenticated attachment/messaging policy.
- `POST /api/v1/attachments/init`
- `POST /api/v1/attachments/{attachmentId}/chunk`
- `POST /api/v1/attachments/{attachmentId}/complete`
- `POST /api/v1/attachments/{attachmentId}/bind`
- `GET /api/v1/attachments/{attachmentId}`

### Message ingress flow (`POST /api/v1/messages`)
1. Require `Authorization: Bearer <sessionId>`; invalid session returns `401`.
2. Parse JSON to `EncryptedMessage`.
3. Validate via `EncryptedMessageValidator`.
4. Rate-limit via `RateLimiterService.checkAndConsume`.
5. Route via `MailboxRouter.ingress(message)`.
6. Return `202` with `IngressResponse(envelopeId, expiresAt)`.

### Security headers
- `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none';`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-Request-Id` per request

### TLS
- HTTPS uses a TLS 1.3 `SSLContext` created in `Main`.
- Cipher suites restricted to:
  - `TLS_AES_256_GCM_SHA384`
  - `TLS_CHACHA20_POLY1305_SHA256`

---

## WebSocketIngressServer

### Purpose
- Authenticated WebSocket channel for real-time delivery and acknowledgements.

### Connection lifecycle
- Client connects with `Authorization: Bearer <sessionId>` in handshake headers.
- On open:
  - session is validated via `SessionDAO`
  - mailbox subscription is created
  - up to 100 undelivered envelopes are pushed immediately
  - presence snapshot is sent for contacts
- On close: subscription and presence registrations are cleaned up.

### Inbound frame handling
- Inbound text frames are treated as ACK payloads, not new encrypted messages.
- Expected shape includes `envelopeIds` array.
- `MailboxRouter.acknowledgeOwned(userId, envelopeIds)` enforces ownership-safe ACK.
- Rate-limited connections receive `{"type":"rate_limit","retryAfterSeconds":...}`.

### Outbound push payloads
- Message push:
  - `{"type":"message","envelopeId":"...","payload":<EncryptedMessage>,"expiresAt":...}`
- Presence push:
  - `{"type":"presence","userId":"...","active":true|false}`

### Error handling
- Unauthorized websocket: policy close (`1008`/`POLICY_VALIDATION`).
- Internal processing errors: logged via `AuditLogger`, connection closed with `1011`.

---

## Shared ingress dependencies
- `EncryptedMessageValidator`: structural + expiry validation for encrypted messages.
- `RateLimiterService`: DB-backed 60-second window + lockout.
- `MailboxRouter`: persistence-backed routing, push dispatch, ACK ownership checks.
- `AuditLogger` + `MetricsRegistry`: structured event logging and counters.
