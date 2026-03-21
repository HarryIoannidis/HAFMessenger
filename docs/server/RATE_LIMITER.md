# RATE_LIMITER

### Purpose
- Documents `RateLimiterService`, the DB-backed per-user ingress throttle.

### Runtime policy
- Window: `60` seconds.
- Max messages per window: `100`.
- Lockout duration after threshold: `15` minutes.

---

## Backing table

`rate_limits` schema (from Flyway migration):
```sql
CREATE TABLE rate_limits (
    user_id VARCHAR(64) NOT NULL,
    message_count INT DEFAULT 0,
    window_start TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    lockout_until TIMESTAMP NULL,
    PRIMARY KEY (user_id)
);
```

---

## `checkAndConsume(requestId, userId)` flow
1. Upsert/update the user row:
  - reset `message_count` and `window_start` when 60s window elapsed
  - increment `message_count` otherwise
  - set `lockout_until` when threshold is reached
2. Read current state.
3. If `lockout_until` is still in the future:
  - return `RateLimitDecision.block(retryAfterSeconds)`
  - emit `AuditLogger.logRateLimit(...)`
4. If over threshold in current window:
  - return blocked decision.
5. Otherwise return `RateLimitDecision.allow()`.

---

## Decision type
- `RateLimitDecision(boolean allowed, long retryAfterSeconds)`
- Constructors:
  - `allow()` -> `(true, 0)`
  - `block(seconds)` -> `(false, max(seconds,1))`

---

## Failure behavior
- SQL failures are logged via `AuditLogger.logError("rate_limit_failed", ...)`.
- Service throws `RateLimitException` to caller.

---

## Notes
- This implementation is message-count based (not byte-budget based).
- Enforcement is shared by HTTP message ingress and WebSocket ACK processing.
