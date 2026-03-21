# OBSERVABILITY

### Purpose
- Documents the implemented observability components: `AuditLogger` and `MetricsRegistry`.

---

## AuditLogger

### What it does
- Emits structured Log4j2 audit events under logger name `AuditLogger`.
- Attaches common fields (`timestamp`, `action`, `requestId`, `userId`) plus action-specific fields.
- Increments metrics counters for key events.

### Key methods
- `logIngressAccepted(requestId, userId, recipientId, status, latencyMs)`
  - action: `send_message`
  - increments `ingressCount`

- `logIngressRejected(requestId, userId, reason, status)`
  - action: `send_message_rejected`
  - increments `rejectCount`

- `logRateLimit(requestId, userId, retryAfterSeconds)`
  - action: `rate_limit`
  - increments `rateLimitRejectCount`

- `logValidationFailure(requestId, userId, recipientId, reason)`
  - action: `validation_failed`

- `logRegistration(requestId, userId, email)`
  - action: `user_registered`

- `logLogin(requestId, userId, email)`
  - action: `user_login`

- `logSearchRequest(requestId, userId, queryLength, queryHash, limit, cursorSupplied)`
  - action: `search_users`

- `logCleanup(deleted, durationMs)`
  - action: `ttl_cleanup`

- `logMetricsSnapshot(snapshot)`
  - action: `metrics`
  - includes queue + delivery-latency aggregates

- `logError(action, requestId, userId, error[, extraFields])`
  - structured ERROR event with stack trace

---

## MetricsRegistry

### Counters
- `ingressCount`
- `rejectCount`
- `rateLimitRejectCount`
- `queueDepth`
- `totalDeliveryLatencyMs`
- `deliveredCount`

### Key methods
- `incrementIngress()`
- `incrementRejects()`
- `incrementRateLimitRejects()`
- `increaseQueueDepth()`
- `decreaseQueueDepth(long count)`
- `recordDeliveryLatency(long latencyMs)`
- `snapshot()` -> `MetricsSnapshot`

### Snapshot fields
- `ingressCount`
- `rejectCount`
- `rateLimitRejectCount`
- `queueDepth`
- `avgDeliveryLatencyMs`
- `deliveredCount`

---

## Operational notes
- Metrics are in-memory and process-local.
- Main server schedules periodic `logMetricsSnapshot(...)` every 60 seconds.
- TTL cleanup metrics are emitted by router cleanup and attachment cleanup scheduling.
