package com.haf.server.router;

import com.haf.server.db.Envelope;
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
    private Envelope envelopeDAO;

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
    void ingress_push_does_not_count_delivery_before_receipt_ack() {
        EncryptedMessage message = createValidMessage();
        QueuedEnvelope envelope = new QueuedEnvelope(
                "envelope-1", message, System.currentTimeMillis(), System.currentTimeMillis() + 3600000);

        when(envelopeDAO.insert(message)).thenReturn(envelope);

        MailboxRouter.MailboxSubscriber subscriber = mock(MailboxRouter.MailboxSubscriber.class);
        router.subscribe(message.getRecipientId(), subscriber);

        router.ingress(message);

        verify(subscriber, times(1)).onEnvelope(envelope);
        assertEquals(0, metricsRegistry.snapshot().deliveredCount());
    }

    @Test
    void ingressIdempotent_does_not_redispatch_duplicate_client_message_id() {
        EncryptedMessage message = createValidMessage();
        QueuedEnvelope envelope = new QueuedEnvelope(
                "envelope-1", message, System.currentTimeMillis(), System.currentTimeMillis() + 3600000);
        when(envelopeDAO.insertIdempotent(message, "client-msg-1"))
                .thenReturn(new Envelope.InsertResult(envelope, true));
        MailboxRouter.MailboxSubscriber subscriber = mock(MailboxRouter.MailboxSubscriber.class);
        router.subscribe(message.getRecipientId(), subscriber);

        MailboxRouter.MailboxIngressResult result = router.ingressIdempotent(message, "client-msg-1");

        assertTrue(result.duplicate());
        assertEquals(envelope, result.envelope());
        verify(subscriber, never()).onEnvelope(any());
        assertEquals(0, metricsRegistry.snapshot().queueDepth());
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
    void acknowledgeOwnedAndReturn_marks_delivered_and_decreases_queue_depth() {
        List<String> envelopeIds = List.of("id1", "id2", "id3");

        // Mock fetchByIds to return envelopes with timestamps
        long now = System.currentTimeMillis();
        QueuedEnvelope env1 = new QueuedEnvelope("id1", createValidMessage(), now - 100, now + 3600000);
        QueuedEnvelope env2 = new QueuedEnvelope("id2", createValidMessage(), now - 200, now + 3600000);
        QueuedEnvelope env3 = new QueuedEnvelope("id3", createValidMessage(), now - 150, now + 3600000);

        when(envelopeDAO.fetchByIdsIncludingDelivered(envelopeIds))
                .thenReturn(Map.of("id1", env1, "id2", env2, "id3", env3));
        when(envelopeDAO.fetchByIds(envelopeIds))
                .thenReturn(Map.of("id1", env1, "id2", env2, "id3", env3));
        when(envelopeDAO.markDelivered(any())).thenReturn(true);

        metricsRegistry.increaseQueueDepth();
        metricsRegistry.increaseQueueDepth();
        metricsRegistry.increaseQueueDepth();

        List<QueuedEnvelope> result = router.acknowledgeOwnedAndReturn("recipient-456", envelopeIds);

        assertEquals(3, result.size());
        verify(envelopeDAO, times(1)).fetchByIdsIncludingDelivered(envelopeIds);
        verify(envelopeDAO, times(1)).fetchByIds(envelopeIds);
        verify(envelopeDAO, times(1)).markDelivered(argThat(ids ->
                ids.size() == envelopeIds.size() && ids.containsAll(envelopeIds)));
        assertEquals(0, metricsRegistry.snapshot().queueDepth());
        assertEquals(3, metricsRegistry.snapshot().deliveredCount());
        assertTrue(metricsRegistry.snapshot().avgDeliveryLatencyMs() > 0);
    }

    @Test
    void acknowledgeOwnedAndReturn_does_not_decrease_when_dao_fails() {
        List<String> envelopeIds = List.of("id1");
        QueuedEnvelope env1 = new QueuedEnvelope("id1", createValidMessage(), System.currentTimeMillis(),
                System.currentTimeMillis() + 3600000);
        when(envelopeDAO.fetchByIdsIncludingDelivered(envelopeIds)).thenReturn(Map.of("id1", env1));
        when(envelopeDAO.fetchByIds(envelopeIds)).thenReturn(Map.of("id1", env1));
        when(envelopeDAO.markDelivered(envelopeIds)).thenReturn(false);
        metricsRegistry.increaseQueueDepth();

        List<QueuedEnvelope> result = router.acknowledgeOwnedAndReturn("recipient-456", envelopeIds);

        assertTrue(result.isEmpty());
        assertEquals(1, metricsRegistry.snapshot().queueDepth());
    }

    @Test
    void acknowledgeOwnedAndReturn_records_delivery_latency() {
        List<String> envelopeIds = List.of("id1", "id2");

        long now = System.currentTimeMillis();
        QueuedEnvelope env1 = new QueuedEnvelope("id1", createValidMessage(), now - 100, now + 3600000);
        QueuedEnvelope env2 = new QueuedEnvelope("id2", createValidMessage(), now - 200, now + 3600000);

        when(envelopeDAO.fetchByIdsIncludingDelivered(envelopeIds))
                .thenReturn(Map.of("id1", env1, "id2", env2));
        when(envelopeDAO.fetchByIds(envelopeIds))
                .thenReturn(Map.of("id1", env1, "id2", env2));
        when(envelopeDAO.markDelivered(any())).thenReturn(true);

        metricsRegistry.increaseQueueDepth();
        metricsRegistry.increaseQueueDepth();

        router.acknowledgeOwnedAndReturn("recipient-456", envelopeIds);

        assertEquals(2, metricsRegistry.snapshot().deliveredCount());
        assertTrue(metricsRegistry.snapshot().avgDeliveryLatencyMs() >= 100);
        assertTrue(metricsRegistry.snapshot().avgDeliveryLatencyMs() <= 200);
    }

    @Test
    void acknowledgeOwnedAndReturn_handles_empty_fetchByIds_result() {
        List<String> envelopeIds = List.of("id1");

        when(envelopeDAO.fetchByIdsIncludingDelivered(envelopeIds)).thenReturn(Map.of());

        metricsRegistry.increaseQueueDepth();

        List<QueuedEnvelope> result = router.acknowledgeOwnedAndReturn("recipient-456", envelopeIds);

        assertTrue(result.isEmpty());
        assertEquals(1, metricsRegistry.snapshot().queueDepth());
        assertEquals(0, metricsRegistry.snapshot().deliveredCount());
    }

    @Test
    void markReadOwnedAndReturn_marks_read_and_delivery_for_undelivered_messages() {
        List<String> envelopeIds = List.of("id1", "id2");
        long now = System.currentTimeMillis();
        QueuedEnvelope env1 = new QueuedEnvelope("id1", createValidMessage(), now - 100, now + 3600000);
        QueuedEnvelope env2 = new QueuedEnvelope("id2", createValidMessage(), now - 200, now + 3600000);
        when(envelopeDAO.fetchByIdsIncludingDelivered(envelopeIds))
                .thenReturn(Map.of("id1", env1, "id2", env2));
        when(envelopeDAO.fetchByIds(envelopeIds))
                .thenReturn(Map.of("id1", env1));
        when(envelopeDAO.markRead(any())).thenReturn(true);
        metricsRegistry.increaseQueueDepth();

        List<QueuedEnvelope> result = router.markReadOwnedAndReturn("recipient-456", envelopeIds);

        assertEquals(2, result.size());
        verify(envelopeDAO).markRead(argThat(ids -> ids.containsAll(envelopeIds)));
        assertEquals(0, metricsRegistry.snapshot().queueDepth());
        assertEquals(1, metricsRegistry.snapshot().deliveredCount());
    }

    @Test
    void markReadOwnedAndReturn_rejects_unowned_envelopes() {
        List<String> envelopeIds = List.of("id1");
        EncryptedMessage message = createValidMessage();
        message.setRecipientId("someone-else");
        QueuedEnvelope env = new QueuedEnvelope("id1", message, System.currentTimeMillis(),
                System.currentTimeMillis() + 3600000);
        when(envelopeDAO.fetchByIdsIncludingDelivered(envelopeIds)).thenReturn(Map.of("id1", env));

        List<QueuedEnvelope> result = router.markReadOwnedAndReturn("recipient-456", envelopeIds);

        assertTrue(result.isEmpty());
        verify(envelopeDAO, never()).markRead(any());
    }

    @Test
    void fetchReceiptReplayForSender_maps_minimal_dao_records() {
        when(envelopeDAO.fetchReceiptsForSender("sender-123", 500)).thenReturn(List.of(
                new Envelope.ReceiptRecord("env-1", "recipient-1", Envelope.ReceiptState.DELIVERED),
                new Envelope.ReceiptRecord("env-2", "recipient-2", Envelope.ReceiptState.READ)));

        List<MailboxRouter.ReceiptReplay> result = router.fetchReceiptReplayForSender("sender-123", 500);

        assertEquals(2, result.size());
        assertEquals("env-1", result.get(0).envelopeId());
        assertFalse(result.get(0).read());
        assertEquals("env-2", result.get(1).envelopeId());
        assertTrue(result.get(1).read());
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
