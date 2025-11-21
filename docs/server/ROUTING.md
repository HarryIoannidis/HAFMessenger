### Purpose
- In-memory queue management for outbound delivery and subscription management for real-time notifications.

***

## MailboxRouter

### Purpose
- Central component for routing validated envelopes in recipient queues.

### Responsibilities
- Accepts 'EncryptedMessage' from ingress (HTTP/WebSocket).
- Saves to DB via 'EnvelopeDAO.insert()'.
- Enqueue in in-memory 'ConcurrentHashMap<recipientId, Queue<QueuedEnvelope>>'.
- Notify active WebSocket subscriptions for new messages.
- Increment `MetricsRegistry.increaseQueueDepth()`.

### enqueue(EncryptedMessage) flow
- Validate that 'recipientId' != null.
- It calls 'EnvelopeDAO.insert(message)' → gets 'envelopeId'.
- Creates 'QueuedEnvelope(envelopeId, recipientId, expiresAtMs)'.
- Places in the recipient's queue: 'queues.computeIfAbsent(recipientId, k -> new ConcurrentLinkedQueue<>()).offer(envelope)'.
- If the recipient has an active WebSocket subscription, it sends a notification frame.
- Increment queue depth counter.

### subscribe(recipientId, WebSocketSession) flow
- Registers sessions on the 'subscriptions' map.
- Flush all pending envelopes from the recipient's queue to WebSocket.
- Every new envelope that arrives afterwards is pushed immediately.

### unsubscribe(recipientId, WebSocketSession) flow
- Removes sessions from 'subscriptions'.
- Pending envelopes stay in the queue for next connection or HTTP poll.

### poll(recipientId, limit) flow
- HTTP clients can do 'GET /api/v1/messages?recipientId=...&limit=10'.
- Router returns up to 'limit' envelopes from the queue.
- Each delivered envelope is deducted from queue and decrement queue depth.

### TTL cleanup
- Periodic background task (e.g. every 60 sec) scans queues.
- Removes expired envelopes (current time > expiresAtMs).
- Calls 'EnvelopeDAO.deleteExpired()' to cleanup the DB.
- Audit log with 'logCleanup(deletedCount, durationMs)'.

***

## QueuedEnvelope

### Purpose
- Lightweight DTO for in-memory queue entries.

### Fields
- 'long envelopeId': primary key from DB.
- `String recipientId`: recipient user ID.
- 'long expiresAtMs': Unix timestamp in ms (clientTimestamp + TTL).

### Usage
- It is created after the DB insert.
- Used for queue management and TTL checks.
- The full 'EncryptedMessage' is loaded by DB only upon delivery (lazy load).

***

## Thread safety

### Concurrent access
- 'queues': 'ConcurrentHashMap' with 'ConcurrentLinkedQueue' values.
- 'subscriptions': 'ConcurrentHashMap<String, Set<WebSocketSession>>' with thread-safe collections.
- Enqueue/poll/cleanup operations are thread-safe with no external locking.

### Lock-free design
- Each recipient queue is independent, no contention between different recipients.
- Subscription updates (add/remove) using atomic operations.

***

## Metrics

### MetricsRegistry integration
- 'increaseQueueDepth()': every time you enqueue.
- 'decreaseQueueDepth()': whenever deliver or expire.
- `snapshot().queueDepth()`: current total pending envelopes across all recipients.

***

## Error handling

### DB errors during insert
- 'EnvelopeDAO.insert()' throws exception → MailboxRouter propagates to ingress.
- Ingress returns '500' and audit log error.
- Envelope **doesn't** enter the in-memory queue.

### WebSocket send failures
- If a notification frame fails (broken connection), it removes the subscription.
- Envelope stays in the queue for retry in next connection or HTTP poll.