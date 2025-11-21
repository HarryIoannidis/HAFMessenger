## RateLimiter

### Purpose
- Sliding-window rate limiting με MySQL backend για προστασία από abuse και DoS attacks.

***

## RateLimiterService

### Purpose
- Enforce per-user message rate limits με sliding window (60 sec) και lockout policy (15 min).

### Responsibilities
- Track message count ανά user σε 60-second windows.
- Block users που υπερβαίνουν threshold (100 messages/window).
- Apply lockout (15 min) μετά από sustained abuse.
- Audit log για rate-limit events.

### Database schema
- Table: `rate_limits`
    - `user_id` VARCHAR(255) PRIMARY KEY.
    - `message_count` INT (messages στο current window).
    - `window_start` TIMESTAMP (αρχή του current window).
    - `lockout_until` TIMESTAMP NULL (lockout expiry, NULL αν δεν υπάρχει lockout).

### checkAndConsume(requestId, userId) flow
- Εκτελεί atomic UPSERT:
    - Αν δεν υπάρχει row → INSERT με `message_count=1`, `window_start=NOW()`.
    - Αν υπάρχει row:
        - Αν `NOW() - window_start >= 60 sec` → reset: `message_count=1`, `window_start=NOW()`.
        - Αλλιώς → increment: `message_count = message_count + 1`.
        - Αν `message_count > 100` → set `lockout_until = NOW() + 15 min`.
- SELECT για έλεγχο lockout:
    - Αν `lockout_until` != NULL AND `lockout_until > NOW()` → rate limited.
    - Αν `lockout_until` != NULL AND `lockout_until <= NOW()` → lockout expired, allow.
    - Αν `lockout_until` = NULL → check `message_count`:
        - Αν `message_count <= 100` → allow.
        - Αν `message_count > 100` → rate limited (lockout μόλις γίνε set).
- Returns `RateLimitDecision(allowed, retryAfterSeconds)`.

### UPSERT SQL
```sql
INSERT INTO rate_limits (user_id, message_count, window_start, lockout_until)
VALUES (?, 1, NOW(), NULL)
ON DUPLICATE KEY UPDATE
  message_count = CASE
    WHEN TIMESTAMPDIFF(SECOND, window_start, NOW()) >= 60 THEN 1
    ELSE message_count + 1
  END,
  window_start = CASE
    WHEN TIMESTAMPDIFF(SECOND, window_start, NOW()) >= 60 THEN NOW()
    ELSE window_start
  END,
  lockout_until = CASE
    WHEN message_count + 1 > 100 THEN DATE_ADD(NOW(), INTERVAL 15 MINUTE)
    ELSE lockout_until
  END;
```

### SELECT SQL
```sql
SELECT message_count, lockout_until
FROM rate_limits
WHERE user_id = ?;
```

### RateLimitDecision

### Purpose
- Immutable DTO για rate-limit απόφαση.

### Fields
- `boolean allowed`: true αν request επιτρέπεται.
- `long retryAfterSeconds`: seconds μέχρι retry (0 αν allowed, >0 αν blocked).

### Factory methods
- `RateLimitDecision.allow()` → `(true, 0)`.
- `RateLimitDecision.block(retryAfterSeconds)` → `(false, max(retryAfterSeconds, 1))`.

***

## Configuration

### Constants (hard-coded στο Phase 5)
- `WINDOW_SECONDS = 60`: διάρκεια sliding window.
- `MAX_MESSAGES_PER_WINDOW = 100`: threshold για rate limiting.
- `LOCKOUT_MINUTES = 15`: διάρκεια lockout μετά από abuse.

### Future tunables (Phase 6+)
- Μπορούν να γίνουν configurable μέσω `ServerConfig` env vars.
- Different limits για different user roles (π.χ. admin vs regular).

***

## Thread safety

### DB-level atomicity
- UPSERT με `ON DUPLICATE KEY UPDATE` είναι atomic operation.
- Κανένα external locking, όλη η λογική στο SQL.

### Concurrent requests
- Multiple requests από τον ίδιο user εκτελούνται σειριακά στο DB level (row lock).
- Διαφορετικοί users δεν έχουν contention.

***

## Audit logging

### AuditLogger integration
- `logRateLimit(requestId, userId, retryAfterSeconds)` για κάθε blocked request.
- Περιλαμβάνει `retryAfterSeconds` για debugging και monitoring.

### MetricsRegistry integration
- Ingress servers καλούν `MetricsRegistry.incrementRateLimitRejects()` όταν `allowed=false`.

***

## Error handling

### DB connection errors
- `SQLException` κατά UPSERT/SELECT → wrap σε `IllegalStateException`.
- Audit log error με `logError("rate_limit_failed", requestId, userId, exception)`.
- Propagate exception σε ingress → HTTP `500` response.

### Fail-open vs fail-closed
- Current implementation: **fail-closed** (αν DB error, δεν επιτρέπεται request).
- Για production: consider fail-open με fallback σε in-memory rate limiting.

***

## Testing

### Unit tests
- Mock `DataSource` και `Connection` με Mockito.
- Verify UPSERT/SELECT logic για scenarios:
    - First request από user (no existing row).
    - Requests εντός window (increment count).
    - Requests μετά από window reset.
    - Lockout trigger (>100 messages).
    - Lockout expiry.

### Integration tests
- Testcontainers MySQL για real DB.
- Verify concurrent requests από multiple threads.
- Verify sliding window behavior με real timestamps.
- **Disabled** στα local environments χωρίς Docker (annotation `@Disabled`).