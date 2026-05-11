# ROUTING

## Purpose

Describe mailbox routing behavior for inbound envelopes and acknowledgements.

## Current Implementation

- `MailboxRouter` accepts validated envelopes from WSS ingress.
- Persists envelopes via DAO and supports realtime subscriptions, reconnect backlog fetch, and receipt workflows.
- Supports ownership-scoped delivery acknowledgement/read lookup and timed cleanup work.
- TTL cleanup runs on a fixed 5-minute interval and decrements queue-depth metrics for deleted rows.

## Key Types/Interfaces

- `server.router.MailboxRouter`
- `server.router.QueuedEnvelope`
- `server.db.EnvelopeDAO`

## Flow

1. WSS ingress hands validated envelope to router.
2. Router persists envelope and increments queue-depth metrics.
3. Realtime subscriptions push envelopes to online recipients.
4. Reconnect backlog fetches undelivered envelopes by recipient.
5. On delivery receipt, router marks owned envelope ids as delivered and records delivery latency.
6. Expired envelopes are pruned via cleanup paths.

## Error/Security Notes

- Receipt paths enforce ownership checks before delivery/read-state routing.
- Router relies on pre-validation/rate-limit checks from ingress layer.

## Related Files

- `server/src/main/java/com/haf/server/router/MailboxRouter.java`
- `server/src/main/java/com/haf/server/router/QueuedEnvelope.java`
- `server/src/main/java/com/haf/server/db/EnvelopeDAO.java`
