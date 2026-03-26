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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(seen.isEmpty());
        assertEquals(1, viewModel.getMessages("bob").size());
        assertTrue(viewModel.getMessages("bob").getFirst().isOutgoing());
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
