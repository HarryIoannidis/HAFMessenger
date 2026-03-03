# RATE_LIMITER

### Purpose
- Prevents abuse by enforcing per-user message quotas using a database-backed sliding window with daily reset.
- Integrates with `AuditLogger` for audit trail of rate-limit events.

### Database schema
```sql
CREATE TABLE rate_limits (
    user_id        VARCHAR(128) NOT NULL,
    window_start   TIMESTAMP    NOT NULL,
    message_count  INT          NOT NULL DEFAULT 0,
    total_bytes    BIGINT       NOT NULL DEFAULT 0,
    locked_until   TIMESTAMP    NULL,
    PRIMARY KEY (user_id, window_start)
);
```

---

## checkAndConsume

### Purpose
- Core method that atomically checks quota and records usage.

### Flow
1. Read current window for `userId`:
```sql
SELECT message_count, total_bytes, locked_until
FROM rate_limits
WHERE user_id = ? AND window_start = ?
FOR UPDATE;
```
2. Check lockout:
    - If `locked_until > NOW()` → return `RateLimitDecision(allowed=false, retryAfterSeconds)`.
3. Check message count:
    - If `message_count >= MAX_DAILY_MESSAGES` → lock user, return denied.
4. Check byte total:
    - If `total_bytes + messageBytes > MAX_MESSAGE_BYTES` → return denied.
5. Update counters:
```sql
INSERT INTO rate_limits (user_id, window_start, message_count, total_bytes)
VALUES (?, ?, 1, ?)
ON DUPLICATE KEY UPDATE
    message_count = message_count + 1,
    total_bytes = total_bytes + ?;
```
6. Return `RateLimitDecision(allowed=true)`.

### Configuration constants
- `MAX_DAILY_MESSAGES = 1000`: maximum messages per user per window.
- `MAX_MESSAGE_BYTES = 50 * 1024 * 1024` (50 MB): maximum total bytes per window.
- `WINDOW_DURATION_SECONDS = 86400` (24 hours): sliding window size.
- `LOCKOUT_DURATION_SECONDS = 900` (15 min): lockout after sustained abuse.

---

## RateLimitDecision

### Purpose
- Immutable result object from `checkAndConsume()`.

### Fields
- `boolean allowed`: whether the request is permitted.
- `int retryAfterSeconds`: seconds until quota resets (0 if allowed).

---

## Thread safety
- Database-level locking: `SELECT ... FOR UPDATE` ensures atomic read-check-update.
- Multiple server instances: safe due to DB-level row locking.
- Connection pooling: HikariCP manages concurrent connections.

---

## Audit logging
- Denied requests: `AuditLogger.logRateLimit(requestId, userId, retryAfterSeconds)`.
- Lockout events: `AuditLogger.logError("rate_limit_lockout", requestId, userId, null)` with lockout duration.
- Allowed requests: no explicit audit log (covered by ingress accepted log).

---

## Error handling
- Database connection failure: throws `RuntimeException`, ingress server returns `500`.
- SQL errors: caught, logged via `AuditLogger.logError()`, request denied as precaution.
- Invalid userId: validated before `checkAndConsume()` (non-null, non-blank).

---

## Testing

### Unit tests
- Mocked `DataSource` and `Connection` to verify SQL execution and decision logic.
- Test cases: under limit, at limit, over limit, lockout active, lockout expired, byte limit exceeded.

### Integration tests
- Real MySQL instance (testcontainers or embedded).
- Concurrent requests to verify atomicity.
- Window rollover behavior.
- Verify audit log entries.