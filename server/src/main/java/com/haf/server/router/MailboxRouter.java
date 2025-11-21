package com.haf.server.router;

import com.haf.server.db.EnvelopeDAO;
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

public final class MailboxRouter implements AutoCloseable {

    private static final Duration TTL_CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final EnvelopeDAO envelopeDAO;
    private final ScheduledExecutorService scheduler;
    private final AuditLogger auditLogger;
    private final MetricsRegistry metricsRegistry;

    private final ConcurrentHashMap<String, CopyOnWriteArraySet<MailboxSubscriber>> subscribers = new ConcurrentHashMap<>();
    private ScheduledFuture<?> cleanupFuture;

    /**
     * Constructs a MailboxRouter instance.
     * @param envelopeDAO The DAO for managing envelopes in the database.
     * @param scheduler The scheduler for running periodic tasks.
     * @param auditLogger The logger for auditing cleanup operations.
     * @param metricsRegistry The registry for tracking metrics.
     */
    public MailboxRouter(EnvelopeDAO envelopeDAO,
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
            TimeUnit.SECONDS
        );
    }

    /**
     * Handles the ingress of a new encrypted message.
     * @param message the encrypted message to be ingressed.
     * @return the queued envelope representing the ingressed message.
     */
    public QueuedEnvelope ingress(EncryptedMessage message) {
        QueuedEnvelope envelope = envelopeDAO.insert(message);
        metricsRegistry.increaseQueueDepth();
        dispatch(message.recipientId, envelope);
        return envelope;
    }

    /**
     * Fetches undelivered messages for a specific recipient.
     * @param recipientId The ID of the recipient.
     * @param limit The maximum number of messages to fetch.
     * @return  list of undelivered messages for the recipient.
     */
    public List<QueuedEnvelope> fetchUndelivered(String recipientId, int limit) {
        return envelopeDAO.fetchForRecipient(recipientId, limit);
    }

    /**
     * Acknowledges the delivery of messages by their envelope IDs.
     * @param envelopeIds a collection of envelope IDs to acknowledge.
     * @return true if the acknowledgment was successful, false otherwise.
     */
    public boolean acknowledge(Collection<String> envelopeIds) {
        Map<String, QueuedEnvelope> envelopes = envelopeDAO.fetchByIds(envelopeIds);

        boolean updated = envelopeDAO.markDelivered(List.copyOf(envelopeIds));

        if (updated) {
            long now = System.currentTimeMillis();
            for (QueuedEnvelope envelope : envelopes.values()) {
                long latency = now - envelope.createdAtEpochMs();
                metricsRegistry.recordDeliveryLatency(latency);
            }
            metricsRegistry.decreaseQueueDepth(envelopeIds.size());
        }
        return updated;
    }

    /**
     * Subscribes a recipient to receive notifications for new messages.
     * @param recipientId the ID of the recipient.
     * @param subscriber the subscriber to be notified.
     * @return a MailboxSubscription object representing the subscription.
     */
    public MailboxSubscription subscribe(String recipientId, MailboxSubscriber subscriber) {
        subscribers.computeIfAbsent(recipientId, key -> new CopyOnWriteArraySet<>()).add(subscriber);
        return new MailboxSubscription(recipientId, subscriber);
    }

    /**
     * Unsubscribes a recipient from receiving notifications.
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
     * @param recipientId the ID of the recipient.
     * @param envelope the envelope containing the message.
     */
    private void dispatch(String recipientId, QueuedEnvelope envelope) {
        Set<MailboxSubscriber> subs = subscribers.get(recipientId);
        if (subs != null) {
            // Track delivery latency for WebSocket push.
            // This is done here because the message is being pushed to the client.
            long latency = System.currentTimeMillis() - envelope.createdAtEpochMs();
            metricsRegistry.recordDeliveryLatency(latency);
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
         * @param envelope the new envelope.
         */
        void onEnvelope(QueuedEnvelope envelope);
    }

    /**
     * Represents a subscription to a recipient's mailbox.
     * @param recipientId the ID of the recipient.
     * @param subscriber the subscriber associated with the subscription.
     */
    public record MailboxSubscription(String recipientId, MailboxSubscriber subscriber) {}
}

