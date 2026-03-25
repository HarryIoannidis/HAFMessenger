package com.haf.server.router;

import com.haf.server.db.EnvelopeDAO;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailboxRouterTest {

    @Mock
    private EnvelopeDAO envelopeDAO;

    @Mock
    private ScheduledExecutorService scheduler;

    @Mock
    private AuditLogger auditLogger;

    private MetricsRegistry metricsRegistry;
    private MailboxRouter router;

    @BeforeEach
    void setUp() {
        metricsRegistry = new MetricsRegistry();
        router = new MailboxRouter(envelopeDAO, scheduler, auditLogger, metricsRegistry);
    }

    @Test
    void ingress_stores_and_dispatches_envelope() {
        EncryptedMessage message = createValidMessage();
        QueuedEnvelope envelope = new QueuedEnvelope(
                "envelope-1", message, System.currentTimeMillis(), System.currentTimeMillis() + 3600000);

        when(envelopeDAO.insert(message)).thenReturn(envelope);

        QueuedEnvelope result = router.ingress(message);

        assertEquals(envelope, result);
        verify(envelopeDAO, times(1)).insert(message);
        assertEquals(1, metricsRegistry.snapshot().queueDepth());
    }

    @Test
    void ingress_notifies_subscribers() {
        EncryptedMessage message = createValidMessage();
        QueuedEnvelope envelope = new QueuedEnvelope(
                "envelope-1", message, System.currentTimeMillis(), System.currentTimeMillis() + 3600000);

        when(envelopeDAO.insert(message)).thenReturn(envelope);

        MailboxRouter.MailboxSubscriber subscriber = mock(MailboxRouter.MailboxSubscriber.class);
        router.subscribe(message.getRecipientId(), subscriber);

        router.ingress(message);

        verify(subscriber, times(1)).onEnvelope(envelope);
    }

    @Test
    void fetchUndelivered_delegates_to_dao() {
        String recipientId = "recipient-123";
        List<QueuedEnvelope> envelopes = List.of(
                new QueuedEnvelope("id1", createValidMessage(), System.currentTimeMillis(),
                        System.currentTimeMillis() + 3600000));

        when(envelopeDAO.fetchForRecipient(recipientId, 100)).thenReturn(envelopes);

        List<QueuedEnvelope> result = router.fetchUndelivered(recipientId, 100);

        assertEquals(envelopes, result);
        verify(envelopeDAO, times(1)).fetchForRecipient(recipientId, 100);
    }

    @Test
    void acknowledge_marks_delivered_and_decreases_queue_depth() {
        List<String> envelopeIds = List.of("id1", "id2", "id3");

        // Mock fetchByIds to return envelopes with timestamps
        long now = System.currentTimeMillis();
        QueuedEnvelope env1 = new QueuedEnvelope("id1", createValidMessage(), now - 100, now + 3600000);
        QueuedEnvelope env2 = new QueuedEnvelope("id2", createValidMessage(), now - 200, now + 3600000);
        QueuedEnvelope env3 = new QueuedEnvelope("id3", createValidMessage(), now - 150, now + 3600000);

        when(envelopeDAO.fetchByIds(envelopeIds))
                .thenReturn(Map.of("id1", env1, "id2", env2, "id3", env3));
        when(envelopeDAO.markDelivered(envelopeIds)).thenReturn(true);

        metricsRegistry.increaseQueueDepth();
        metricsRegistry.increaseQueueDepth();
        metricsRegistry.increaseQueueDepth();

        boolean result = router.acknowledge(envelopeIds);

        assertTrue(result);
        verify(envelopeDAO, times(1)).fetchByIds(envelopeIds);
        verify(envelopeDAO, times(1)).markDelivered(envelopeIds);
        assertEquals(0, metricsRegistry.snapshot().queueDepth());
        assertEquals(3, metricsRegistry.snapshot().deliveredCount());
        assertTrue(metricsRegistry.snapshot().avgDeliveryLatencyMs() > 0);
    }

    @Test
    void acknowledge_does_not_decrease_when_dao_fails() {
        List<String> envelopeIds = List.of("id1");
        when(envelopeDAO.markDelivered(envelopeIds)).thenReturn(false);
        metricsRegistry.increaseQueueDepth();

        boolean result = router.acknowledge(envelopeIds);

        assertFalse(result);
        assertEquals(1, metricsRegistry.snapshot().queueDepth());
    }

    @Test
    void acknowledge_records_delivery_latency() {
        List<String> envelopeIds = List.of("id1", "id2");

        long now = System.currentTimeMillis();
        QueuedEnvelope env1 = new QueuedEnvelope("id1", createValidMessage(), now - 100, now + 3600000);
        QueuedEnvelope env2 = new QueuedEnvelope("id2", createValidMessage(), now - 200, now + 3600000);

        when(envelopeDAO.fetchByIds(envelopeIds))
                .thenReturn(Map.of("id1", env1, "id2", env2));
        when(envelopeDAO.markDelivered(envelopeIds)).thenReturn(true);

        metricsRegistry.increaseQueueDepth();
        metricsRegistry.increaseQueueDepth();

        router.acknowledge(envelopeIds);

        assertEquals(2, metricsRegistry.snapshot().deliveredCount());
        assertTrue(metricsRegistry.snapshot().avgDeliveryLatencyMs() >= 100);
        assertTrue(metricsRegistry.snapshot().avgDeliveryLatencyMs() <= 200);
    }

    @Test
    void acknowledge_handles_empty_fetchByIds_result() {
        List<String> envelopeIds = List.of("id1");

        when(envelopeDAO.fetchByIds(envelopeIds)).thenReturn(Map.of());
        when(envelopeDAO.markDelivered(envelopeIds)).thenReturn(true);

        metricsRegistry.increaseQueueDepth();

        boolean result = router.acknowledge(envelopeIds);

        assertTrue(result);
        assertEquals(0, metricsRegistry.snapshot().queueDepth());
        assertEquals(0, metricsRegistry.snapshot().deliveredCount()); // No latency recorded
    }

    @Test
    void subscribe_registers_subscriber() {
        String recipientId = "recipient-123";
        MailboxRouter.MailboxSubscriber subscriber = mock(MailboxRouter.MailboxSubscriber.class);

        MailboxRouter.MailboxSubscription subscription = router.subscribe(recipientId, subscriber);

        assertNotNull(subscription);
        assertEquals(recipientId, subscription.recipientId());
        assertEquals(subscriber, subscription.subscriber());
    }

    @Test
    void unsubscribe_removes_subscriber() {
        String recipientId = "recipient-123";
        MailboxRouter.MailboxSubscriber subscriber = mock(MailboxRouter.MailboxSubscriber.class);

        MailboxRouter.MailboxSubscription subscription = router.subscribe(recipientId, subscriber);
        router.unsubscribe(subscription);

        // Verify subscriber is not called after unsubscription
        EncryptedMessage message = createValidMessage();
        message.setRecipientId(recipientId);
        QueuedEnvelope envelope = new QueuedEnvelope(
                "envelope-1", message, System.currentTimeMillis(), System.currentTimeMillis() + 3600000);
        when(envelopeDAO.insert(message)).thenReturn(envelope);

        router.ingress(message);

        verify(subscriber, never()).onEnvelope(any());
    }

    @Test
    void unsubscribe_handles_null_gracefully() {
        assertDoesNotThrow(() -> router.unsubscribe(null));
    }

    @Test
    void start_schedules_ttl_cleanup() {
        router.start();

        verify(scheduler, times(1)).scheduleAtFixedRate(
                any(Runnable.class),
                eq(300L), // 5 minutes
                eq(300L),
                eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void close_cancels_cleanup() {
        router.start();
        router.close();

        // Verify cleanup future would be cancelled (requires access to internal state
        // or integration test)
        assertDoesNotThrow(() -> router.close());
    }

    @Test
    void ttl_cleanup_deletes_expired_and_logs() throws Exception {
        metricsRegistry.increaseQueueDepth();
        metricsRegistry.increaseQueueDepth();
        metricsRegistry.increaseQueueDepth();
        metricsRegistry.increaseQueueDepth();
        metricsRegistry.increaseQueueDepth();

        when(envelopeDAO.deleteExpired()).thenReturn(3);

        var cleanupMethod = MailboxRouter.class.getDeclaredMethod("runTtlCleanup");
        cleanupMethod.setAccessible(true);
        cleanupMethod.invoke(router);

        verify(envelopeDAO, times(1)).deleteExpired();
        verify(auditLogger, times(1)).logCleanup(eq(3), anyLong());
        assertEquals(2, metricsRegistry.snapshot().queueDepth());
    }

    private EncryptedMessage createValidMessage() {
        EncryptedMessage message = new EncryptedMessage();
        message.setVersion(MessageHeader.VERSION);
        message.setAlgorithm(MessageHeader.ALGO_AEAD);
        message.setSenderId("sender-123");
        message.setRecipientId("recipient-456");
        message.setTimestampEpochMs(System.currentTimeMillis());
        message.setTtlSeconds((int) Duration.ofDays(1).toSeconds());
        message.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
        message.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[256]));
        message.setCiphertextB64(Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8)));
        message.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
        message.setContentType("text/plain");
        message.setContentLength(4);
        message.setAadB64(Base64.getEncoder().encodeToString("aad".getBytes(StandardCharsets.UTF_8)));
        message.setE2e(true);
        return message;
    }
}
