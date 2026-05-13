package com.haf.client.viewmodels;

import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.client.network.MessageReceiver;
import com.haf.client.network.MessageSender;
import com.haf.client.utils.RuntimeIssue;
import com.haf.client.models.MessageVM;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.MessageValidationException;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MessageViewModelRuntimeTest {

    @Test
    void send_failure_emits_runtime_issue_and_retry_reconnects_and_resends() throws Exception {
        FailOnceSender sender = new FailOnceSender();
        CountingReceiver receiver = new CountingReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        viewModel.sendTextMessage("bob", "hello");

        awaitCondition(() -> sender.sendCalls.get() == 1 && !issues.isEmpty());
        assertEquals(1, sender.sendCalls.get());
        assertEquals(1, viewModel.getMessages("bob").size());
        MessageVM pending = viewModel.getMessages("bob").getFirst();
        assertTrue(pending.isOutgoing());
        assertTrue(pending.isLoading());
        assertEquals("hello", pending.content());
        RuntimeIssue issue = issues.getFirst();
        assertEquals("messaging.send.failed", issue.dedupeKey());

        issue.retryAction().run();
        awaitCondition(() -> sender.sendCalls.get() == 2
                && receiver.startCalls.get() == 1
                && receiver.stopCalls.get() == 1
                && viewModel.getMessages("bob").size() == 1
                && !viewModel.getMessages("bob").getFirst().isLoading());

        assertEquals("hello", viewModel.getMessages("bob").getFirst().content());
        assertTrue(viewModel.getMessages("bob").getFirst().isOutgoing());
    }

    @Test
    void send_retry_skips_reconnect_when_receiver_is_already_healthy() throws Exception {
        FailOnceSender sender = new FailOnceSender();
        HealthyReceiver receiver = new HealthyReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);
        viewModel.startReceiving();

        viewModel.sendTextMessage("bob", "hello");

        awaitCondition(() -> sender.sendCalls.get() == 1 && !issues.isEmpty());
        RuntimeIssue issue = issues.getFirst();
        issue.retryAction().run();

        awaitCondition(() -> sender.sendCalls.get() == 2
                && receiver.startCalls.get() == 1
                && receiver.stopCalls.get() == 0
                && viewModel.getMessages("bob").size() == 1
                && !viewModel.getMessages("bob").getFirst().isLoading());

        assertTrue(receiver.isConnectedCalls.get() > 0);
    }

    @Test
    void start_receiving_failure_emits_runtime_issue() {
        FailStartReceiver receiver = new FailStartReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(new NoopSender(), receiver);
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        viewModel.startReceiving();

        assertEquals(1, receiver.startCalls.get());
        assertFalse(issues.isEmpty());
        assertEquals("messaging.receive.start.failed", issues.getFirst().dedupeKey());
    }

    @Test
    void send_connection_failure_is_marked_as_connection_issue() throws Exception {
        MessageSender sender = (payload, recipientId, contentType, ttlSeconds) -> {
            throw new IOException("network down", new ConnectException("refused"));
        };
        MessagesViewModel viewModel = new MessagesViewModel(sender, new CountingReceiver());
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        viewModel.sendTextMessage("bob", "hello");
        awaitCondition(() -> !issues.isEmpty());

        assertEquals("messaging.send.failed", issues.getFirst().dedupeKey());
        assertTrue(issues.getFirst().connectionIssue());
    }

    @Test
    void session_refresh_transport_restore_reconnects_receiver_without_resending_messages() throws Exception {
        CountingReceiver receiver = new CountingReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(new NoopSender(), receiver);

        viewModel.restoreReceiverTransportAfterSessionRefresh();

        awaitCondition(() -> receiver.startCalls.get() == 1 && receiver.stopCalls.get() == 1);
        assertEquals(1, receiver.startCalls.get());
        assertEquals(1, receiver.stopCalls.get());
    }

    @Test
    void session_refresh_transport_restore_skips_reconnect_when_receiver_is_already_healthy() throws Exception {
        HealthyReceiver receiver = new HealthyReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(new NoopSender(), receiver);
        viewModel.startReceiving();

        awaitCondition(() -> receiver.startCalls.get() == 1);
        viewModel.restoreReceiverTransportAfterSessionRefresh();

        awaitCondition(() -> receiver.isConnectedCalls.get() > 0);
        assertEquals(1, receiver.startCalls.get());
        assertEquals(0, receiver.stopCalls.get());
    }

    @Test
    void session_refresh_transport_restore_failure_emits_runtime_issue() throws Exception {
        FailStartReceiver receiver = new FailStartReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(new NoopSender(), receiver);
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        viewModel.restoreReceiverTransportAfterSessionRefresh();

        awaitCondition(() -> !issues.isEmpty());
        assertEquals("messaging.refresh.reconnect.failed", issues.getFirst().dedupeKey());
    }

    @Test
    void read_receipt_before_outgoing_envelope_is_rendered_is_applied_later() throws Exception {
        ListenerCapturingReceiver receiver = new ListenerCapturingReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(new EnvelopeSender("env-1"), receiver);

        receiver.listener().onMessageRead("bob", List.of("env-1"));
        viewModel.sendTextMessage("bob", "hello");

        awaitCondition(() -> viewModel.getMessages("bob").size() == 1
                && !viewModel.getMessages("bob").getFirst().isLoading());

        MessageVM rendered = viewModel.getMessages("bob").getFirst();
        assertEquals("env-1", rendered.envelopeId());
        assertTrue(rendered.isRead());
    }

    @Test
    void receiver_takeover_error_emits_takeover_runtime_issue() throws Exception {
        ListenerCapturingReceiver receiver = new ListenerCapturingReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(new NoopSender(), receiver);
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        receiver.listener().onError(new HttpCommunicationException(
                "HTTP GET failed with status 401: {\"error\":\"session revoked by takeover\"}",
                401,
                "{\"error\":\"session revoked by takeover\"}"));

        awaitCondition(() -> !issues.isEmpty());
        assertEquals("messaging.session.takeover", issues.getFirst().dedupeKey());
        assertEquals("Logged out", issues.getFirst().title());
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long timeoutAt = System.currentTimeMillis() + 2_500L;
        CountDownLatch latch = new CountDownLatch(1);
        while (System.currentTimeMillis() < timeoutAt) {
            if (condition.getAsBoolean()) {
                return;
            }
            latch.await(10, TimeUnit.MILLISECONDS);
        }
        fail("Condition not met within timeout");
    }

    private static final class FailOnceSender implements MessageSender {
        private final AtomicInteger sendCalls = new AtomicInteger();

        @Override
        public void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds)
                throws MessageValidationException, KeyNotFoundException, IOException {
            int call = sendCalls.incrementAndGet();
            if (call == 1) {
                throw new IOException("network down");
            }
            if (!"hello".equals(new String(payload, StandardCharsets.UTF_8))) {
                throw new IOException("unexpected payload");
            }
        }
    }

    private static final class NoopSender implements MessageSender {
        @Override
        public void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds)
                throws MessageValidationException, KeyNotFoundException, IOException {
            // no-op
        }
    }

    private static final class EnvelopeSender implements MessageSender {
        private final String envelopeId;

        private EnvelopeSender(String envelopeId) {
            this.envelopeId = envelopeId;
        }

        @Override
        public void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds)
                throws MessageValidationException, KeyNotFoundException, IOException {
            // no-op
        }

        @Override
        public SendResult sendMessageWithResult(byte[] payload, String recipientId, String contentType, long ttlSeconds)
                throws MessageValidationException, KeyNotFoundException, IOException {
            return new SendResult(envelopeId, System.currentTimeMillis() + 60_000L);
        }
    }

    private static class CountingReceiver implements MessageReceiver {
        protected final AtomicInteger startCalls = new AtomicInteger();
        protected final AtomicInteger stopCalls = new AtomicInteger();

        @Override
        public void setMessageListener(MessageListener listener) {
            // no-op
        }

        @Override
        public void start() throws IOException {
            startCalls.incrementAndGet();
        }

        @Override
        public void stop() {
            stopCalls.incrementAndGet();
        }

        @Override
        public void acknowledgeEnvelopes(String senderId) {
            // no-op
        }
    }

    private static final class FailStartReceiver extends CountingReceiver {
        @Override
        public void start() throws IOException {
            startCalls.incrementAndGet();
            throw new IOException("cannot connect");
        }
    }

    private static final class ListenerCapturingReceiver extends CountingReceiver {
        private MessageListener listener;

        @Override
        public void setMessageListener(MessageListener listener) {
            this.listener = listener;
        }

        private MessageListener listener() {
            if (listener == null) {
                throw new IllegalStateException("listener");
            }
            return listener;
        }
    }

    private static final class HealthyReceiver extends CountingReceiver {
        private final AtomicInteger isConnectedCalls = new AtomicInteger();

        @Override
        public boolean isConnected() {
            isConnectedCalls.incrementAndGet();
            return true;
        }
    }
}
