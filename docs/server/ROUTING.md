# ROUTING

## Purpose

Describe mailbox routing behavior for inbound envelopes and acknowledgements.

## Current Implementation

- `MailboxRouter` accepts validated envelopes from ingress.
- Persists envelopes via DAO and tracks subscriptions for dev-mode push delivery.
- Supports acknowledgement (`acknowledgeOwned`) and timed cleanup work.
- TTL cleanup runs on a fixed 5-minute interval and decrements queue-depth metrics for deleted rows.

## Key Types/Interfaces

- `server.router.MailboxRouter`
- `server.router.QueuedEnvelope`
- `server.db.EnvelopeDAO`

## Flow

1. Ingress hands validated envelope to router.
2. Router persists envelope and increments queue-depth metrics.
3. In dev mode, router publishes to active websocket subscribers for the recipient.
4. In all modes, polling paths can fetch undelivered envelopes by recipient.
5. On ACK, router marks owned envelope ids as delivered and records delivery latency.
6. Expired envelopes are pruned via cleanup paths.

## Error/Security Notes

- ACK path enforces ownership checks before delivery-state mutation.
- Router relies on pre-validation/rate-limit checks from ingress layer.
- Subscription map is concurrent and per-recipient, reducing cross-user delivery risk in memory.

## Related Files

- `server/src/main/java/com/haf/server/router/MailboxRouter.java`
- `server/src/main/java/com/haf/server/router/QueuedEnvelope.java`
- `server/src/main/java/com/haf/server/db/EnvelopeDAO.java`
