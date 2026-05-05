# ROUTING

## Purpose

Describe mailbox routing behavior for inbound envelopes and acknowledgements.

## Current Implementation

- `MailboxRouter` accepts validated envelopes from ingress.
- Persists envelopes via DAO and supports polling fetch/ack delivery workflows.
- Supports acknowledgement (`acknowledgeOwned`) and timed cleanup work.
- TTL cleanup runs on a fixed 5-minute interval and decrements queue-depth metrics for deleted rows.

## Key Types/Interfaces

- `server.router.MailboxRouter`
- `server.router.QueuedEnvelope`
- `server.db.EnvelopeDAO`

## Flow

1. Ingress hands validated envelope to router.
2. Router persists envelope and increments queue-depth metrics.
3. Polling paths fetch undelivered envelopes by recipient.
4. On ACK, router marks owned envelope ids as delivered and records delivery latency.
5. Expired envelopes are pruned via cleanup paths.

## Error/Security Notes

- ACK path enforces ownership checks before delivery-state mutation.
- Router relies on pre-validation/rate-limit checks from ingress layer.

## Related Files

- `server/src/main/java/com/haf/server/router/MailboxRouter.java`
- `server/src/main/java/com/haf/server/router/QueuedEnvelope.java`
- `server/src/main/java/com/haf/server/db/EnvelopeDAO.java`
