package com.haf.client.viewmodels;

import com.haf.client.network.MessageReceiver;
import com.haf.client.network.MessageSender;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.MessageValidationException;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ChatViewModelTest {

    @Test
    void switching_recipient_preserves_drafts_and_acknowledges_active_chat() {
        RecordingSender sender = new RecordingSender();
        AckRecordingReceiver receiver = new AckRecordingReceiver();
        MessagesViewModel messageViewModel = new MessagesViewModel(sender, receiver);
        ChatViewModel viewModel = new ChatViewModel(messageViewModel);

        viewModel.setRecipient("alice");
        viewModel.draftTextProperty().set("draft-a");

        viewModel.setRecipient("bob");
        assertEquals("", viewModel.draftTextProperty().get());
        viewModel.draftTextProperty().set("draft-b");

        viewModel.setRecipient("alice");
        assertEquals("draft-a", viewModel.draftTextProperty().get());

        assertEquals(List.of("alice", "bob", "alice"), receiver.acknowledgedSenderIds);
    }

    @Test
    void can_send_requires_non_blank_recipient_and_message() {
        RecordingSender sender = new RecordingSender();
        AckRecordingReceiver receiver = new AckRecordingReceiver();
        MessagesViewModel messageViewModel = new MessagesViewModel(sender, receiver);
        ChatViewModel viewModel = new ChatViewModel(messageViewModel);

        assertFalse(viewModel.canSendProperty().get());

        viewModel.setRecipient("alice");
        assertFalse(viewModel.canSendProperty().get());

        viewModel.draftTextProperty().set("   ");
        assertFalse(viewModel.canSendProperty().get());

        viewModel.draftTextProperty().set("hello");
        assertTrue(viewModel.canSendProperty().get());

        viewModel.setRecipient("");
        assertFalse(viewModel.canSendProperty().get());
    }

    @Test
    void send_current_draft_trims_sends_and_clears_draft() {
        RecordingSender sender = new RecordingSender();
        AckRecordingReceiver receiver = new AckRecordingReceiver();
        MessagesViewModel messageViewModel = new MessagesViewModel(sender, receiver);
        ChatViewModel viewModel = new ChatViewModel(messageViewModel);

        viewModel.setRecipient("alice");
        viewModel.draftTextProperty().set("  hello world  ");
        viewModel.sendCurrentDraft();

        awaitCondition(() -> sender.sentMessages.size() == 1 && messageViewModel.getMessages("alice").size() == 1);
        assertEquals(1, sender.sentMessages.size());
        SentMessage sent = sender.sentMessages.getFirst();
        assertEquals("alice", sent.recipientId);
        assertEquals("text/plain", sent.contentType);
        assertEquals("hello world", new String(sent.payload, StandardCharsets.UTF_8));

        assertEquals("", viewModel.draftTextProperty().get());
        assertFalse(viewModel.canSendProperty().get());
        assertEquals(1, messageViewModel.getMessages("alice").size());
        assertEquals("hello world", messageViewModel.getMessages("alice").getFirst().content());
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

    private static final class RecordingSender implements MessageSender {
        private final List<SentMessage> sentMessages = new CopyOnWriteArrayList<>();

        @Override
        public void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds)
                throws MessageValidationException, KeyNotFoundException, IOException {
            sentMessages.add(new SentMessage(payload, recipientId, contentType));
        }
    }

    private static final class AckRecordingReceiver implements MessageReceiver {
        private final List<String> acknowledgedSenderIds = new ArrayList<>();

        @Override
        public void setMessageListener(MessageListener listener) {
            // no-op for this test double
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
            acknowledgedSenderIds.add(senderId);
        }
    }

    private record SentMessage(byte[] payload, String recipientId, String contentType) {
    }
}
