# ROUTING

### Purpose
- In-memory queue management for routing encrypted envelopes to recipients.
- Manages subscriptions, polling, and TTL-based cleanup.

---

## MailboxRouter

### Purpose
- Routes validated envelopes from ingress to recipient mailboxes.

### Dependencies
- `EnvelopeDAO`: persistence layer for envelope storage.
- `MetricsRegistry`: queue depth tracking.
- `AuditLogger`: logging for routing events.

### Methods

#### ingressEnvelope(EncryptedMessageDTO dto, String senderId, String recipientId, long ttlSeconds)
- Validates envelope metadata (sender, recipient, TTL).
- Stores via `EnvelopeDAO.insert()`.
- Adds to in-memory queue for recipient.
- If recipient is subscribed (online via WebSocket): delivers immediately.
- Increments `MetricsRegistry.increaseQueueDepth()`.

#### subscribe(String userId, WebSocket connection)
- Registers WebSocket connection for real-time delivery.
- Flushes any pending envelopes for the user.

#### unsubscribe(String userId)
- Removes WebSocket connection.
- Pending envelopes remain in queue for polling.

#### poll(String userId) → List<QueuedEnvelope>
- Returns pending envelopes for offline delivery.
- Marks delivered envelopes via `EnvelopeDAO.markDelivered()`.
- Decrements `MetricsRegistry.decreaseQueueDepth()`.

#### cleanupExpired()
- Called by scheduled task (every 5 minutes).
- Delegates to `EnvelopeDAO.deleteExpired()`.
- Decrements queue depth for expired envelopes.
- Logs cleanup via `AuditLogger.logCleanup()`.

---

## QueuedEnvelope

### Purpose
- Internal DTO representing an envelope in the routing queue.

### Fields
- `long id`: database ID.
- `String senderId`: sender user ID.
- `String recipientId`: recipient user ID.
- `byte[] payload`: encrypted payload (opaque blob).
- `long createdAtMs`: creation timestamp.
- `long expiresAtMs`: expiration timestamp.
- `boolean delivered`: delivery status.

---

## Thread safety
- In-memory queues use `ConcurrentHashMap<String, Queue<QueuedEnvelope>>`.
- Queue operations are atomic per recipient.
- Subscribe/unsubscribe are thread-safe (ConcurrentHashMap).
- Cleanup scheduled via `ScheduledExecutorService`.

---

## Metrics
- `increaseQueueDepth()`: on enqueue.
- `decreaseQueueDepth()`: on delivery or expiry.
- `snapshot().queueDepth`: current pending count.

---

## Error handling
- Database errors during insert: throws exception, ingress returns 500.
- Delivery errors (WebSocket send failure): log error, keep envelope in queue for polling.
- Cleanup errors: log via `AuditLogger`, retry on next scheduled run.

---

## TTL cleanup job
- Scheduled every 5 minutes via `ScheduledExecutorService.scheduleAtFixedRate()`.
- Calls `EnvelopeDAO.deleteExpired()`.
- Logs results via `AuditLogger.logCleanup(deleted, durationMs)`.
- Thread-safe: runs on dedicated thread, does not block ingress.