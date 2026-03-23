package com.haf.client.services;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultChatAttachmentServiceTest {

    @Test
    void resolve_mime_type_maps_supported_extensions() {
        assertEquals("image/png", DefaultChatAttachmentService.resolveMimeType(Path.of("image.png")));
        assertEquals("image/jpeg", DefaultChatAttachmentService.resolveMimeType(Path.of("image.jpg")));
        assertEquals("image/jpeg", DefaultChatAttachmentService.resolveMimeType(Path.of("image.jpeg")));
        assertEquals("image/gif", DefaultChatAttachmentService.resolveMimeType(Path.of("image.gif")));
        assertEquals("image/webp", DefaultChatAttachmentService.resolveMimeType(Path.of("image.webp")));
        assertEquals("application/pdf", DefaultChatAttachmentService.resolveMimeType(Path.of("file.pdf")));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                DefaultChatAttachmentService.resolveMimeType(Path.of("file.docx")));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                DefaultChatAttachmentService.resolveMimeType(Path.of("file.xlsx")));
        assertNull(DefaultChatAttachmentService.resolveMimeType(Path.of("file.unknown")));
    }

    @Test
    void send_attachment_is_safe_when_chat_session_gateway_is_missing() {
        DefaultChatAttachmentService service = new DefaultChatAttachmentService(() -> null);

        assertDoesNotThrow(() -> service.sendAttachment("recipient-1", Path.of("doc.pdf")));
    }

    @Test
    void send_attachment_delegates_recipient_path_and_mime_to_gateway() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> recipientRef = new AtomicReference<>();
        AtomicReference<Path> pathRef = new AtomicReference<>();
        AtomicReference<String> mimeRef = new AtomicReference<>();

        DefaultChatAttachmentService service = new DefaultChatAttachmentService(
                () -> (recipientId, filePath, mimeType) -> {
                    calls.incrementAndGet();
                    recipientRef.set(recipientId);
                    pathRef.set(filePath);
                    mimeRef.set(mimeType);
                });

        Path path = Path.of("attachment.pdf");
        service.sendAttachment("recipient-1", path);

        assertEquals(1, calls.get());
        assertEquals("recipient-1", recipientRef.get());
        assertEquals(path, pathRef.get());
        assertEquals("application/pdf", mimeRef.get());
    }
}
