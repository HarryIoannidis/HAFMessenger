package com.haf.client.controllers;

import com.haf.client.services.ChatAttachmentService;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatControllerTest {

    @Test
    void constructor_rejects_null_attachment_service() {
        assertThrows(NullPointerException.class, () -> new ChatController(null));
    }

    @Test
    void selected_attachment_delegates_recipient_and_path_to_service() {
        StubChatAttachmentService attachmentService = new StubChatAttachmentService();
        ChatController controller = new ChatController(attachmentService);
        File selected = new File("doc.pdf");

        controller.dispatchSelectedAttachment("recipient-1", selected);

        assertEquals(1, attachmentService.calls.get());
        assertEquals("recipient-1", attachmentService.recipientId.get());
        assertEquals(selected.toPath(), attachmentService.filePath.get());
    }

    @Test
    void selected_attachment_is_noop_when_recipient_is_missing_or_blank() {
        StubChatAttachmentService attachmentService = new StubChatAttachmentService();
        ChatController controller = new ChatController(attachmentService);
        File selected = new File("doc.pdf");

        controller.dispatchSelectedAttachment(null, selected);
        controller.dispatchSelectedAttachment("", selected);
        controller.dispatchSelectedAttachment("   ", selected);

        assertEquals(0, attachmentService.calls.get());
    }

    private static final class StubChatAttachmentService implements ChatAttachmentService {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> recipientId = new AtomicReference<>();
        private final AtomicReference<Path> filePath = new AtomicReference<>();

        @Override
        public void sendAttachment(String recipientId, Path filePath) {
            calls.incrementAndGet();
            this.recipientId.set(recipientId);
            this.filePath.set(filePath);
        }
    }
}
