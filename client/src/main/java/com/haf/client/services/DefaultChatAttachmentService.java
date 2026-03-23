package com.haf.client.services;

import com.haf.client.core.ChatSession;
import com.haf.client.viewmodels.MessageViewModel;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Default {@link ChatAttachmentService} that delegates to the active chat session.
 */
public class DefaultChatAttachmentService implements ChatAttachmentService {

    @FunctionalInterface
    interface AttachmentGateway {
        void sendAttachment(String recipientId, Path filePath, String mimeType);
    }

    @FunctionalInterface
    interface AttachmentGatewayProvider {
        AttachmentGateway current();
    }

    private final AttachmentGatewayProvider attachmentGatewayProvider;

    public DefaultChatAttachmentService() {
        this(() -> {
            MessageViewModel vm = ChatSession.get();
            return vm == null ? null : vm::sendAttachment;
        });
    }

    DefaultChatAttachmentService(AttachmentGatewayProvider attachmentGatewayProvider) {
        this.attachmentGatewayProvider = Objects.requireNonNull(attachmentGatewayProvider, "attachmentGatewayProvider");
    }

    @Override
    public void sendAttachment(String recipientId, Path filePath) {
        if (recipientId == null || recipientId.isBlank() || filePath == null) {
            return;
        }

        AttachmentGateway gateway = attachmentGatewayProvider.current();
        if (gateway == null) {
            return;
        }

        gateway.sendAttachment(recipientId, filePath, resolveMimeType(filePath));
    }

    static String resolveMimeType(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }

        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (name.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (name.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        return null;
    }
}
