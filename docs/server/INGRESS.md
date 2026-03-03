# INGRESS

### Purpose
- Two parallel ingress endpoints: HTTPS REST and secure WebSocket for encrypted message input.
- Common validation, rate limiting, routing to `MailboxRouter`.

---

## HttpIngressServer

### Purpose
- HTTPS listener for `POST /api/v1/messages`.

### Responsibilities
- Parse JSON body to `EncryptedMessage`.
- Validate via `EncryptedMessageValidator`.
- Rate limit check via `RateLimiterService`.
- Enqueue validated envelope in `MailboxRouter`.
- HTTP responses: `202`, `400`, `413`, `429`, `500`.

### Request flow
- Client POST with JSON body.
- Server deserialize (if fail → `400 MALFORMED_JSON`).
- Validate (if invalid → `400`/`413`).
- Rate limit (if blocked → `429` with `Retry-After` header).
- Enqueue (if success → `202`, if DB error → `500`).
- Increment metrics.

### TLS
- `SSLContext` from `Main` (PKCS12, TLS 1.3).
- Ciphers: `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256`.
- Client auth: `HAF_TLS_CLIENT_AUTH` (`NONE`/`OPTIONAL`/`REQUIRED`).

### Threading
- Java `HttpServer` or embedded (Jetty/Netty) with thread pool.
- Stateless handlers, dependencies injected.

### Shutdown
- `stop(timeout)`: stops new connections, waits for in-flight requests, closes sockets.

---

## WebSocketIngressServer

### Purpose
- Secure WebSocket listener on `/ws` for persistent connections.

### Responsibilities
- Parse text frames as JSON `EncryptedMessage`.
- Validate, rate limit, enqueue (same logic as HTTP).
- WebSocket close codes: `1000`, `1003`, `1008`, `1011`.

### Connection lifecycle
- Client opens `wss://server:port/ws`.
- For each frame:
    - Parse JSON.
    - Validate (if invalid → error frame + close `1008`).
    - Rate limit (if blocked → error frame + close `1008`).
    - Enqueue (if success → ack frame, if error → error frame + close `1011`).
- Client or server closes connection.

### Error handling
- Parse errors: `{"error": "MALFORMED_JSON"}` + close `1003`.
- Validation: `{"error": "VALIDATION_FAILED", "reason": "..."}` + close `1008`.
- Rate limit: `{"error": "RATE_LIMITED", "retryAfter": <sec>}` + close `1008`.
- Internal: generic error + close `1011`.
- Audit log for everything.

### TLS
- Same `SSLContext` as HTTP.
- Upgrade via standard handshake.

### Threading
- Asynchronous I/O (Java-WebSocket/Jetty).
- State managed per connection.

### Shutdown
- `stop()`: sends close frame `1001 Going Away`, waits ack/timeout, closes.

---

## EncryptedMessageValidator

### Purpose
- Common validator for HTTP and WebSocket.

### Validation checks
- Required fields: `senderId`, `recipientId`, `ciphertextB64`, headers (`CLIENT_TIMESTAMP`, `TTL_MILLIS`).
- Valid base64 for `ciphertextB64`.
- TTL not expired (client timestamp + TTL > now).
- Payload size <= `HAF_MAX_MESSAGE_BYTES`.

### Output
- `ValidationResult(valid, reason, expiresAtMillis)`.

---

## RateLimiterService integration

### Purpose
- Common rate limiter for HTTP and WebSocket.

### Policy
- Sliding window: 60 sec, threshold 100 messages/user.
- Lockout: 15 min after sustained abuse.

### Output
- `RateLimitDecision(allowed, retryAfterSeconds)`.

---

## MailboxRouter integration

### Purpose
- Both endpoints call `MailboxRouter.enqueue(envelope)`.

### Flow
- Router stores in the DB via `EnvelopeDAO`.
- Updates in-memory queues for delivery.

---

## Metrics and audits

### AuditLogger events
- `logIngressAccepted(requestId, userId, recipientId, status, latencyMs)`.
- `logIngressRejected(requestId, userId, reason, status)`.
- `logRateLimit(requestId, userId, retryAfterSeconds)`.
- `logError(action, requestId, userId, exception)`.

### MetricsRegistry counters
- `incrementIngress()` for valid requests.
- `incrementRejects()` for validation/malformed.
- `incrementRateLimitRejects()` for rate-limited.