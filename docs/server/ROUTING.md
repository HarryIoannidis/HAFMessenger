# ROUTING

### Purpose
- Documents `MailboxRouter` behavior for envelope ingress, push delivery, acknowledgements, and TTL cleanup.

---

## MailboxRouter

### Dependencies
- `EnvelopeDAO`: stores and retrieves envelopes from `message_envelopes`.
- `MetricsRegistry`: queue depth and delivery-latency tracking.
- `AuditLogger`: cleanup and error event logging.
- `ScheduledExecutorService`: runs periodic TTL cleanup.

### Core methods
- `start()`
  - schedules `runTtlCleanup()` every 5 minutes.

- `ingress(EncryptedMessage message) -> QueuedEnvelope`
  - persists via `EnvelopeDAO.insert(message)`.
  - increments queue depth.
  - dispatches to current subscribers for recipient.

- `fetchUndelivered(String recipientId, int limit) -> List<QueuedEnvelope>`
  - loads undelivered, unexpired envelopes from DAO.

- `acknowledge(Collection<String> envelopeIds) -> boolean`
  - bulk mark-delivered path.

- `acknowledgeOwned(String userId, Collection<String> envelopeIds) -> boolean`
  - ownership-safe ACK path used by WebSocket ingress.
  - only marks envelopes belonging to `userId`.
  - decreases queue depth only for acknowledged owned envelopes.

- `subscribe(String recipientId, MailboxSubscriber subscriber)`
  - registers recipient push subscriber.

- `unsubscribe(MailboxSubscription subscription)`
  - unregisters subscriber.

### Cleanup behavior
- `runTtlCleanup()` calls `EnvelopeDAO.deleteExpired()`.
- Queue depth reduced by deleted count.
- Emits `AuditLogger.logCleanup(deleted, durationMs)`.

---

## QueuedEnvelope

`QueuedEnvelope` stores:
- `envelopeId`
- `payload` (`EncryptedMessage`)
- `createdAtEpochMs`
- `expiresAtEpochMs`

---

## Concurrency model
- Subscriber registry uses `ConcurrentHashMap<String, CopyOnWriteArraySet<MailboxSubscriber>>`.
- Cleanup runs on scheduler thread; ingress and ACK run concurrently with thread-safe counters/collections.
- DAO remains source-of-truth for persistence and ACK state.
