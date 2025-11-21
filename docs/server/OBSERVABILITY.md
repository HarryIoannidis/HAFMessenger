## Observability

### Purpose
- Centralized audit logging και in-memory metrics για monitoring, debugging, και compliance.

***

## AuditLogger

### Purpose
- Structured logging για όλα τα security-critical και operational events.

### Responsibilities
- Log ingress events (accept/reject/rate-limit).
- Log validation failures με reason codes.
- Log errors με full stack traces.
- Log TTL cleanup και metrics snapshots.
- JSON-structured output για parsing από SIEM/log aggregators.

### Logger configuration
- Log4j2 logger name: `"AuditLogger"`.
- Separate appender από application logs για audit trail isolation.
- Output format: JSON με fields `timestamp`, `action`, `userId`, `requestId`, `status`, `reason`, `latencyMs`, κτλ.

### Methods

### logIngressAccepted(requestId, userId, recipientId, status, latencyMs)
- Action: `"send_message"`.
- Fields: `requestId`, `userId`, `recipientId`, `status` (200/202), `latencyMs`.
- Level: INFO.
- Called: μετά από successful `MailboxRouter.enqueue()`.

### logIngressRejected(requestId, userId, reason, status)
- Action: `"send_message_rejected"`.
- Fields: `requestId`, `userId`, `reason` (validation/malformed), `status` (400/413).
- Level: WARN.
- Called: όταν validation αποτύχει πριν το routing.

### logValidationFailed(requestId, userId, recipientId, reason)
- Action: `"validation_failed"`.
- Fields: `requestId`, `userId`, `recipientId`, `reason` (`STRUCTURAL_INVALID`, `TTL_EXPIRED`, `PAYLOAD_TOO_LARGE`).
- Level: WARN.
- Called: από `EncryptedMessageValidator` failures.

### logRateLimit(requestId, userId, retryAfterSeconds)
- Action: `"rate_limit"`.
- Fields: `requestId`, `userId`, `status` (429), `retryAfterSeconds`.
- Level: WARN.
- Called: όταν `RateLimiterService.checkAndConsume()` returns `allowed=false`.

### logMetrics(ingressCount, rejectCount, rateLimitRejectCount, queueDepth)
- Action: `"metrics"`.
- Fields: `userId="system"`, `ingressCount`, `rejectCount`, `rateLimitRejectCount`, `queueDepth`.
- Level: INFO.
- Called: περιοδικά (π.χ. κάθε 60 sec) από background task.

### logCleanup(deleted, durationMs)
- Action: `"ttl_cleanup"`.
- Fields: `userId="system"`, `deleted`, `durationMs`.
- Level: INFO.
- Called: μετά από `EnvelopeDAO.deleteExpired()`.

### logError(action, requestId, userId, error)
- Action: custom (π.χ. `"ws_ingress_error"`, `"rate_limit_failed"`).
- Fields: `requestId`, `userId`, full exception stack trace.
- Level: ERROR.
- Called: σε όλα τα unhandled exceptions.

***

## MetricsRegistry

### Purpose
- Thread-safe in-memory counters για real-time operational metrics.

### Responsibilities
- Track ingress throughput.
- Track reject rates (validation, rate-limit).
- Track current queue depth.
- Expose snapshot για audit logging και health checks.

### Counters (AtomicLong)
- `ingressCount`: total validated messages που έφτασαν στο `MailboxRouter`.
- `rejectCount`: total rejected messages (validation/malformed).
- `rateLimitRejectCount`: total rate-limited requests.
- `queueDepth`: current pending envelopes στο `MailboxRouter` (incremented on enqueue, decremented on delivery/expiry).

### Methods

### incrementIngress()
- Atomic increment του `ingressCount`.
- Called: από ingress servers μετά από successful routing.

### incrementRejects()
- Atomic increment του `rejectCount`.
- Called: όταν validation αποτύχει ή JSON parse error.

### incrementRateLimitRejects()
- Atomic increment του `rateLimitRejectCount`.
- Called: όταν `RateLimiterService` blocks request.

### increaseQueueDepth()
- Atomic increment του `queueDepth`.
- Called: από `MailboxRouter.enqueue()`.

### decreaseQueueDepth()
- Atomic decrement του `queueDepth`.
- Called: όταν envelope delivered ή expired.

### snapshot() → MetricsSnapshot
- Returns immutable snapshot των current values.
- Thread-safe read (AtomicLong.get()).

### MetricsSnapshot

### Purpose
- Immutable DTO για metrics export.

### Fields
- `long ingressCount`.
- `long rejectCount`.
- `long rateLimitRejectCount`.
- `long queueDepth`.

### Usage
- Passed σε `AuditLogger.logMetrics()`.
- Exposed σε health check endpoint (π.χ. `GET /health`).

***

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
- Logs στο `/var/log/haf-messenger/audit.json` (ή stdout σε containerized env).
- Parseable από Splunk, ELK, Datadog, κτλ.
- Search queries: `action="rate_limit"`, `userId="user-123"`, `status=500`.

***

## Thread safety

### AuditLogger
- Log4j2 είναι thread-safe internally.
- Κανένα shared mutable state στο `AuditLogger`.

### MetricsRegistry
- `AtomicLong` counters → lock-free atomic operations.
- `snapshot()` reads current values χωρίς locking.

***

## Testing

### AuditLogger tests
- Custom Log4j2 `TestAppender` που capture events σε in-memory list.
- Verify ότι κάθε method γράφει σωστό action/fields.
- Assert ότι sensitive fields (ciphertext, keys) **δεν** εμφανίζονται ποτέ στα logs.

### MetricsRegistry tests
- Unit tests για increment/decrement operations.
- Concurrency test: multiple threads increment simultaneously, verify final count.
- Snapshot immutability: verify ότι snapshot δεν αλλάζει μετά την δημιουργία του.