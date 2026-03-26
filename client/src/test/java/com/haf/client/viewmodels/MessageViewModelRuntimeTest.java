package com.haf.client.viewmodels;

import com.haf.client.network.MessageReceiver;
import com.haf.client.network.MessageSender;
import com.haf.client.utils.RuntimeIssue;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.MessageValidationException;
import org.junit.jupiter.api.Test;
import java.io.IOException;
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

        assertEquals(1, sender.sendCalls.get());
        assertTrue(viewModel.getMessages("bob").isEmpty());
        assertFalse(issues.isEmpty());
        RuntimeIssue issue = issues.getFirst();
        assertEquals("messaging.send.failed", issue.dedupeKey());

        issue.retryAction().run();
        awaitCondition(() -> sender.sendCalls.get() == 2
                && receiver.startCalls.get() == 1
                && receiver.stopCalls.get() == 1
                && viewModel.getMessages("bob").size() == 1);

        assertEquals("hello", viewModel.getMessages("bob").getFirst().content());
        assertTrue(viewModel.getMessages("bob").getFirst().isOutgoing());
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
}
