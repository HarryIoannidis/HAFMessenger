package com.haf.server.router;

import com.haf.server.db.Envelope;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.shared.dto.EncryptedMessage;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Routes queued envelopes between persistence and active mailbox subscribers.
 */
public final class MailboxRouter implements AutoCloseable {

    private static final Duration TTL_CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final Envelope envelopeDAO;
    private final ScheduledExecutorService scheduler;
    private final AuditLogger auditLogger;
    private final MetricsRegistry metricsRegistry;

    private final ConcurrentHashMap<String, CopyOnWriteArraySet<MailboxSubscriber>> subscribers = new ConcurrentHashMap<>();
    private ScheduledFuture<?> cleanupFuture;

    /**
     * Constructs a MailboxRouter instance.
     *
     * @param envelopeDAO     The DAO for managing envelopes in the database.
     * @param scheduler       The scheduler for running periodic tasks.
     * @param auditLogger     The logger for auditing cleanup operations.
     * @param metricsRegistry The registry for tracking metrics.
     */
    public MailboxRouter(Envelope envelopeDAO,
            ScheduledExecutorService scheduler,
            AuditLogger auditLogger,
            MetricsRegistry metricsRegistry) {
        this.envelopeDAO = Objects.requireNonNull(envelopeDAO, "envelopeDAO");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
        this.metricsRegistry = Objects.requireNonNull(metricsRegistry, "metricsRegistry");
    }

    /**
     * Starts the mailbox router, scheduling TTL cleanup tasks.
     */
    public void start() {
        cleanupFuture = scheduler.scheduleAtFixedRate(
                this::runTtlCleanup,
                TTL_CLEANUP_INTERVAL.toSeconds(),
                TTL_CLEANUP_INTERVAL.toSeconds(),
                TimeUnit.SECONDS);
    }

    /**
     * Handles the ingress of a new encrypted message.
     *
     * @param message the encrypted message to be ingressed.
     * @return the queued envelope representing the ingressed message.
     */
    public QueuedEnvelope ingress(EncryptedMessage message) {
        QueuedEnvelope envelope = envelopeDAO.insert(message);
        metricsRegistry.increaseQueueDepth();
        dispatch(message.getRecipientId(), envelope);
        return envelope;
    }

    /**
     * Handles message ingress with per-sender client idempotency.
     *
     * @param message         encrypted message
     * @param clientMessageId client-provided idempotency key
     * @return ingress result
     */
    public MailboxIngressResult ingressIdempotent(EncryptedMessage message, String clientMessageId) {
        Envelope.InsertResult result = envelopeDAO.insertIdempotent(message, clientMessageId);
        if (!result.duplicate()) {
            metricsRegistry.increaseQueueDepth();
            dispatch(message.getRecipientId(), result.envelope());
        }
        return new MailboxIngressResult(result.envelope(), result.duplicate());
    }

    /**
     * Fetches undelivered messages for a specific recipient.
     *
     * @param recipientId The ID of the recipient.
     * @param limit       The maximum number of messages to fetch.
     * @return list of undelivered messages for the recipient.
     */
    public List<QueuedEnvelope> fetchUndelivered(String recipientId, int limit) {
        return envelopeDAO.fetchForRecipient(recipientId, limit);
    }

    /**
     * Fetches envelopes that belong to a mailbox owner, regardless of delivery
     * status.
     *
     * @param userId      mailbox owner
     * @param envelopeIds candidate envelope IDs
     * @return owned non-expired envelopes
     */
    public List<QueuedEnvelope> fetchOwned(String userId, Collection<String> envelopeIds) {
        if (userId == null || envelopeIds == null || envelopeIds.isEmpty()) {
            return List.of();
        }
        return envelopeDAO.fetchByIdsIncludingDelivered(envelopeIds).values().stream()
                .filter(env -> userId.equals(env.payload().getRecipientId()))
                .toList();
    }

    /**
     * Acknowledges delivery for the specified user only and returns the owned
     * envelopes that were acknowledged.
     *
     * @param userId      mailbox owner
     * @param envelopeIds candidate envelope IDs
     * @return owned envelopes that were marked delivered
     */
    public List<QueuedEnvelope> acknowledgeOwnedAndReturn(String userId, Collection<String> envelopeIds) {
        if (userId == null || envelopeIds == null || envelopeIds.isEmpty()) {
            return List.of();
        }

        Map<String, QueuedEnvelope> envelopes = envelopeDAO.fetchByIdsIncludingDelivered(envelopeIds);

        List<String> owned = envelopeIds.stream()
                .filter(id -> {
                    QueuedEnvelope envelope = envelopes.get(id);
                    return envelope != null && userId.equals(envelope.payload().getRecipientId());
                })
                .distinct()
                .toList();

        if (owned.isEmpty()) {
            return List.of();
        }

        Map<String, QueuedEnvelope> newlyDelivered = envelopeDAO.fetchByIds(owned);
        boolean updated = envelopeDAO.markDelivered(owned);
        if (updated) {
            recordNewDeliveryMetrics(newlyDelivered.values());
        }
        if (!updated) {
            return List.of();
        }
        return owned.stream()
                .map(envelopes::get)
                .toList();
    }

    /**
     * Marks owned envelopes as read. Read receipts imply delivery, so undelivered
     * owned envelopes are also removed from the mailbox queue.
     *
     * @param userId      mailbox owner emitting the read receipt
     * @param envelopeIds candidate envelope IDs
     * @return owned envelopes that were marked read
     */
    public List<QueuedEnvelope> markReadOwnedAndReturn(String userId, Collection<String> envelopeIds) {
        if (userId == null || envelopeIds == null || envelopeIds.isEmpty()) {
            return List.of();
        }

        Map<String, QueuedEnvelope> envelopes = envelopeDAO.fetchByIdsIncludingDelivered(envelopeIds);
        List<String> owned = envelopeIds.stream()
                .filter(id -> {
                    QueuedEnvelope envelope = envelopes.get(id);
                    return envelope != null && userId.equals(envelope.payload().getRecipientId());
                })
                .distinct()
                .toList();

        if (owned.isEmpty()) {
            return List.of();
        }

        Map<String, QueuedEnvelope> newlyDelivered = envelopeDAO.fetchByIds(owned);
        boolean updated = envelopeDAO.markRead(owned);
        if (updated) {
            recordNewDeliveryMetrics(newlyDelivered.values());
        }
        if (!updated) {
            return List.of();
        }
        return owned.stream()
                .map(envelopes::get)
                .toList();
    }

    /**
     * Fetches minimal persisted receipt state for replay to a reconnecting sender.
     *
     * @param senderId sender whose outbound receipt state should be replayed
     * @param limit    maximum receipt rows to fetch
     * @return minimal receipt metadata
     */
    public List<ReceiptReplay> fetchReceiptReplayForSender(String senderId, int limit) {
        return envelopeDAO.fetchReceiptsForSender(senderId, limit).stream()
                .map(record -> new ReceiptReplay(record.envelopeId(), record.recipientId(),
                        record.state() == Envelope.ReceiptState.READ))
                .toList();
    }

    private void recordNewDeliveryMetrics(Collection<QueuedEnvelope> newlyDelivered) {
        if (newlyDelivered == null || newlyDelivered.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (QueuedEnvelope envelope : newlyDelivered) {
            long latency = now - envelope.createdAtEpochMs();
            metricsRegistry.recordDeliveryLatency(latency);
        }
        metricsRegistry.decreaseQueueDepth(newlyDelivered.size());
    }

    /**
     * Subscribes a recipient to receive notifications for new messages.
     *
     * @param recipientId the ID of the recipient.
     * @param subscriber  the subscriber to be notified.
     * @return a MailboxSubscription object representing the subscription.
     */
    public MailboxSubscription subscribe(String recipientId, MailboxSubscriber subscriber) {
        subscribers.computeIfAbsent(recipientId, key -> new CopyOnWriteArraySet<>()).add(subscriber);
        return new MailboxSubscription(recipientId, subscriber);
    }

    /**
     * Unsubscribes a recipient from receiving notifications.
     *
     * @param subscription the subscription to be removed.
     */
    public void unsubscribe(MailboxSubscription subscription) {
        if (subscription == null) {
            return;
        }
        Set<MailboxSubscriber> set = subscribers.get(subscription.recipientId());
        if (set != null) {
            set.remove(subscription.subscriber());
            if (set.isEmpty()) {
                subscribers.remove(subscription.recipientId());
            }
        }
    }

    /**
     * Dispatches a message to all subscribers of a recipient.
     *
     * @param recipientId the ID of the recipient.
     * @param envelope    the envelope containing the message.
     */
    private void dispatch(String recipientId, QueuedEnvelope envelope) {
        Set<MailboxSubscriber> subs = subscribers.get(recipientId);
        if (subs != null) {
            subs.forEach(sub -> sub.onEnvelope(envelope));
        }
    }

    /**
     * Performs cleanup of expired messages and logs the operation.
     */
    private void runTtlCleanup() {
        long start = System.nanoTime();
        int deleted = envelopeDAO.deleteExpired();
        if (deleted > 0) {
            metricsRegistry.decreaseQueueDepth(deleted);
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        auditLogger.logCleanup(deleted, durationMs);
    }

    /**
     * Closes the MailboxRouter, stopping any scheduled cleanup tasks.
     */
    @Override
    public void close() {
        if (cleanupFuture != null) {
            cleanupFuture.cancel(true);
        }
    }

    /**
     * Interface for subscribers to receive notifications of new messages.
     */
    public interface MailboxSubscriber {

        /**
         * Called when a new envelope is available for the subscriber.
         *
         * @param envelope the new envelope.
         */
        void onEnvelope(QueuedEnvelope envelope);
    }

    /**
     * Represents a subscription to a recipient's mailbox.
     *
     * @param recipientId the ID of the recipient.
     * @param subscriber  the subscriber associated with the subscription.
     */
    public record MailboxSubscription(String recipientId, MailboxSubscriber subscriber) {
    }

    /**
     * Idempotent mailbox ingress outcome.
     *
     * @param envelope  stored envelope
     * @param duplicate true when a previous client message id was reused
     */
    public record MailboxIngressResult(QueuedEnvelope envelope, boolean duplicate) {
    }

    /**
     * Minimal receipt replay metadata for sender reconnects.
     *
     * @param envelopeId  envelope whose receipt state should be replayed
     * @param recipientId original recipient who emitted the receipt
     * @param read        true for read, false for delivered
     */
    public record ReceiptReplay(String envelopeId, String recipientId, boolean read) {
    }
}
