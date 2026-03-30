package com.haf.client.viewmodels;

import com.haf.client.network.MessageReceiver;
import com.haf.client.network.MessageSender;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.MessageValidationException;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageViewModelPresenceTest {

    @Test
    void presence_listener_receives_updates() throws Exception {
        StubMessageReceiver receiver = new StubMessageReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(new NoopMessageSender(), receiver);

        List<String> updates = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        viewModel.addPresenceListener((userId, active) -> {
            updates.add(userId + ":" + active);
            latch.countDown();
        });

        receiver.emitPresence("user-123", true);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("user-123:true"), updates);
    }

    @Test
    void hidden_presence_metadata_is_forwarded_to_listener_override() throws Exception {
        StubMessageReceiver receiver = new StubMessageReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(new NoopMessageSender(), receiver);

        List<String> updates = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        viewModel.addPresenceListener(new MessagesViewModel.PresenceListener() {
            @Override
            public void onPresenceUpdate(String userId, boolean active) {
                // No-op: this test validates 3-arg override dispatch.
            }

            @Override
            public void onPresenceUpdate(String userId, boolean active, boolean hidden) {
                updates.add(userId + ":" + active + ":" + hidden);
                latch.countDown();
            }
        });

        receiver.emitPresence("user-123", false, true);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("user-123:false:true"), updates);
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

        void emitPresence(String userId, boolean active) {
            emitPresence(userId, active, false);
        }

        void emitPresence(String userId, boolean active, boolean hidden) {
            if (listener != null) {
                listener.onPresenceUpdate(userId, active, hidden);
            }
        }
    }
}
