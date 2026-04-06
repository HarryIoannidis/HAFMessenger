# RATE_LIMITER

## Purpose

Document implemented rate-limit policy and decision flow.

## Current Implementation

- `RateLimiterService` enforces per-user limits using `rate_limits` table.
- Message policy values in code:
  - window: 60 seconds
  - max messages: 100 per window
  - lockout: 15 minutes
- Login policy values in code (separate table `login_rate_limits`, key = SHA-256(email|ip)):
  - window: 10 minutes
  - max login attempts: 8 per window
  - lockout: 10 minutes
- Returns `RateLimitDecision(allowed, retryAfterSeconds)`.
- Decision flow performs SQL upsert first, then a select to evaluate current lockout and counter state.

## Key Types/Interfaces

- `server.router.RateLimiterService`
- `server.router.RateLimiterService.RateLimitDecision`
- `server.exceptions.RateLimitException`

## Flow

1. Upsert/update message or login window counters.
2. Read current count and lockout state.
3. If lockout is active, return block with computed retry-after seconds.
4. If threshold is exceeded, return block with window retry hint.
5. Return allow/block decision.
6. Emit audit events for rate-limit blocks/failures.
7. On successful login, clear login limiter state for that email+ip key.

## Error/Security Notes

- SQL errors are logged and wrapped in `RateLimitException`.
- Retry-after values are bounded to positive values.
- Block decisions always normalize `retryAfterSeconds >= 1`.

## Related Files

- `server/src/main/java/com/haf/server/router/RateLimiterService.java`
- `server/src/main/resources/db/migration/V5__create_rate_limits_table.sql`
- `server/src/main/resources/db/migration/V13__harden_sessions_and_add_login_rate_limits.sql`
