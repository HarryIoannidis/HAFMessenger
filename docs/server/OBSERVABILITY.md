# OBSERVABILITY

## Purpose

Describe built-in server observability mechanisms for audit and metrics.

## Current Implementation

- `AuditLogger` emits structured operational/security events.
- `MetricsRegistry` tracks counters/timers used by ingress/router paths.
- Main scheduler emits periodic metrics snapshots.
- Metrics snapshot cadence is 60 seconds and includes ingress, rejects, rate-limit rejects, queue depth, delivered count, and average delivery latency.

## Key Types/Interfaces

- `server.metrics.AuditLogger`
- `server.metrics.MetricsRegistry`
- `server.core.Main`

## Flow

1. Ingress/router components emit events and metric increments.
2. Periodic scheduler records snapshot summaries.
3. Rate-limit and validation events are logged with explicit action/status fields.
4. Errors are logged with request/user context where available.

## Error/Security Notes

- Logs avoid encrypted payload plaintext/key material.
- Audit hooks are used for rate-limit and processing failures.
- Audit entries include `requestId` when available for traceability across ingress and DAO/router paths.

## Related Files

- `server/src/main/java/com/haf/server/metrics/AuditLogger.java`
- `server/src/main/java/com/haf/server/metrics/MetricsRegistry.java`
- `server/src/main/java/com/haf/server/core/Main.java`
