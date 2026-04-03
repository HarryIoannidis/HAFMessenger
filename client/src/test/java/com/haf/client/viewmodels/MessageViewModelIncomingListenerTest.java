package com.haf.client.viewmodels;

import com.haf.client.models.MessageVM;
import com.haf.client.network.MessageReceiver;
import com.haf.client.network.MessageSender;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.MessageValidationException;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MessageViewModelIncomingListenerTest {

    @Test
    void incoming_message_listener_is_notified_for_incoming_messages() {
        StubMessageReceiver receiver = new StubMessageReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(new NoopMessageSender(), receiver);
        List<String> senders = new CopyOnWriteArrayList<>();

        viewModel.addIncomingMessageListener((senderId, message) -> senders.add(senderId + ":" + message.type()));
        receiver.emitMessage("hello".getBytes(StandardCharsets.UTF_8), "alice", "text/plain");

        assertEquals(List.of("alice:TEXT"), senders);
        assertEquals(1, viewModel.getMessages("alice").size());
    }

    @Test
    void incoming_message_listener_is_not_notified_for_outgoing_messages() {
        StubMessageReceiver receiver = new StubMessageReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(new NoopMessageSender(), receiver);
        List<MessageVM> seen = new CopyOnWriteArrayList<>();

        viewModel.addIncomingMessageListener((senderId, message) -> seen.add(message));
        viewModel.sendTextMessage("bob", "hello");

        awaitCondition(() -> viewModel.getMessages("bob").size() == 1);
        assertTrue(seen.isEmpty());
        assertEquals(1, viewModel.getMessages("bob").size());
        assertTrue(viewModel.getMessages("bob").getFirst().isOutgoing());
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long timeoutAt = System.currentTimeMillis() + 2_500L;
        CountDownLatch latch = new CountDownLatch(1);
        while (System.currentTimeMillis() < timeoutAt) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                latch.await(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for condition", ex);
            }
        }
        fail("Condition not met within timeout");
    }

    private static final class NoopMessageSender implements MessageSender {
        @Override
        public void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds)
                throws MessageValidationException, KeyNotFoundException, IOException {
            // no-op
        }
    }

    private static final class StubMessageReceiver implements MessageReceiver {
        private MessageListener listener;

        @Override
        public void setMessageListener(MessageListener listener) {
            this.listener = listener;
        }

        @Override
        public void start() {
            // no-op
        }

        @Override
        public void stop() {
            // no-op
        }

        @Override
        public void acknowledgeEnvelopes(String senderId) {
            // no-op
        }

        void emitMessage(byte[] plaintext, String senderId, String contentType) {
            if (listener != null) {
                listener.onMessage(plaintext, senderId, contentType, System.currentTimeMillis(), "env-1");
            }
        }
    }
}
