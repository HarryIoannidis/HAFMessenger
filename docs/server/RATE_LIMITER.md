# RATE_LIMITER

## Purpose
Document implemented rate-limit policy and decision flow.

## Current Implementation
- `RateLimiterService` enforces per-user limits using `rate_limits` table.
- Policy values in code:
  - window: 60 seconds
  - max messages: 100 per window
  - lockout: 15 minutes
- Returns `RateLimitDecision(allowed, retryAfterSeconds)`.
- Decision flow performs SQL upsert first, then a select to evaluate current lockout and counter state.

## Key Types/Interfaces
- `server.router.RateLimiterService`
- `server.router.RateLimiterService.RateLimitDecision`
- `server.exceptions.RateLimitException`

## Flow
1. Upsert/update user window counters.
2. Read current count and lockout state.
3. If lockout is active, return block with computed retry-after seconds.
4. If threshold is exceeded, return block with window retry hint.
5. Return allow/block decision.
6. Emit audit events for rate-limit blocks/failures.

## Error/Security Notes
- SQL errors are logged and wrapped in `RateLimitException`.
- Retry-after values are bounded to positive values.
- Block decisions always normalize `retryAfterSeconds >= 1`.

## Related Files
- `server/src/main/java/com/haf/server/router/RateLimiterService.java`
- `server/src/main/resources/db/migration/V5__create_rate_limits_table.sql`
