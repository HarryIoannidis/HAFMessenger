# OBSERVABILITY

### Purpose
- Centralized audit logging and in-memory metrics for monitoring, debugging, and compliance.

---

## AuditLogger

### Purpose
- Structured logging for all security-critical and operational events.

### Responsibilities
- Log ingress events (accept/reject/rate-limit).
- Log validation failures with reason codes.
- Log errors with full stack traces.
- Log TTL cleanup and metrics snapshots.
- JSON-structured output for parsing by SIEM/log aggregators.

### Logger configuration
- Log4j2 logger name: `"AuditLogger"`.
- Separate appender from application logs for audit trail isolation.
- Output format: JSON with fields `timestamp`, `action`, `userId`, `requestId`, `status`, `reason`, `latencyMs`, etc.

### Methods

#### logIngressAccepted(requestId, userId, recipientId, status, latencyMs)
- Action: `"send_message"`.
- Fields: `requestId`, `userId`, `recipientId`, `status` (200/202), `latencyMs`.
- Level: INFO.
- Called: after successful `MailboxRouter.enqueue()`.

#### logIngressRejected(requestId, userId, reason, status)
- Action: `"send_message_rejected"`.
- Fields: `requestId`, `userId`, `reason` (validation/malformed), `status` (400/413).
- Level: WARN.
- Called: when validation fails before routing.

#### logValidationFailed(requestId, userId, recipientId, reason)
- Action: `"validation_failed"`.
- Fields: `requestId`, `userId`, `recipientId`, `reason` (`STRUCTURAL_INVALID`, `TTL_EXPIRED`, `PAYLOAD_TOO_LARGE`).
- Level: WARN.
- Called: from `EncryptedMessageValidator` failures.

#### logRateLimit(requestId, userId, retryAfterSeconds)
- Action: `"rate_limit"`.
- Fields: `requestId`, `userId`, `status` (429), `retryAfterSeconds`.
- Level: WARN.
- Called: when `RateLimiterService.checkAndConsume()` returns `allowed=false`.

#### logMetrics(ingressCount, rejectCount, rateLimitRejectCount, queueDepth)
- Action: `"metrics"`.
- Fields: `userId="system"`, `ingressCount`, `rejectCount`, `rateLimitRejectCount`, `queueDepth`.
- Level: INFO.
- Called: periodically (e.g. every 60 sec) from background task.

#### logCleanup(deleted, durationMs)
- Action: `"ttl_cleanup"`.
- Fields: `userId="system"`, `deleted`, `durationMs`.
- Level: INFO.
- Called: after `EnvelopeDAO.deleteExpired()`.

#### logError(action, requestId, userId, error)
- Action: custom (e.g. `"ws_ingress_error"`, `"rate_limit_failed"`).
- Fields: `requestId`, `userId`, full exception stack trace.
- Level: ERROR.
- Called: on all unhandled exceptions.

---

## MetricsRegistry

### Purpose
- Thread-safe in-memory counters for real-time operational metrics.

### Responsibilities
- Track ingress throughput.
- Track reject rates (validation, rate-limit).
- Track current queue depth.
- Expose snapshot for audit logging and health checks.

### Counters (AtomicLong)
- `ingressCount`: total validated messages that reached `MailboxRouter`.
- `rejectCount`: total rejected messages (validation/malformed).
- `rateLimitRejectCount`: total rate-limited requests.
- `queueDepth`: current pending envelopes in `MailboxRouter` (incremented on enqueue, decremented on delivery/expiry).

### Methods

#### incrementIngress()
- Atomic increment of `ingressCount`.
- Called: from ingress servers after successful routing.

#### incrementRejects()
- Atomic increment of `rejectCount`.
- Called: when validation fails or JSON parse error.

#### incrementRateLimitRejects()
- Atomic increment of `rateLimitRejectCount`.
- Called: when `RateLimiterService` blocks request.

#### increaseQueueDepth()
- Atomic increment of `queueDepth`.
- Called: from `MailboxRouter.enqueue()`.

#### decreaseQueueDepth()
- Atomic decrement of `queueDepth`.
- Called: when envelope delivered or expired.

#### snapshot() → MetricsSnapshot
- Returns immutable snapshot of current values.
- Thread-safe read (AtomicLong.get()).

### MetricsSnapshot

#### Purpose
- Immutable DTO for metrics export.

#### Fields
- `long ingressCount`.
- `long rejectCount`.
- `long rateLimitRejectCount`.
- `long queueDepth`.

#### Usage
- Passed to `AuditLogger.logMetrics()`.
- Exposed to health check endpoint (e.g. `GET /health`).

---

## Log output format

### JSON structure
```json
{
  "timestamp": "2025-11-18T09:00:15.123Z",
  "action": "send_message",
  "requestId": "req-abc123",
  "userId": "user-456",
  "recipientId": "user-789",
  "status": 202,
  "latencyMs": 45
}
```

### SIEM integration
- Logs to `/var/log/haf-messenger/audit.json` (or stdout in containerized env).
- Parseable by Splunk, ELK, Datadog, etc.
- Search queries: `action="rate_limit"`, `userId="user-123"`, `status=500`.

---

## Thread safety

### AuditLogger
- Log4j2 is thread-safe internally.
- No shared mutable state in `AuditLogger`.

### MetricsRegistry
- `AtomicLong` counters → lock-free atomic operations.
- `snapshot()` reads current values without locking.

---

## Testing

### AuditLogger tests
- Custom Log4j2 `TestAppender` that captures events in an in-memory list.
- Verify that each method writes the correct action/fields.
- Assert that sensitive fields (ciphertext, keys) **never** appear in logs.

### MetricsRegistry tests
- Unit tests for increment/decrement operations.
- Concurrency test: multiple threads increment simultaneously, verify final count.
- Snapshot immutability: verify that snapshot does not change after creation.