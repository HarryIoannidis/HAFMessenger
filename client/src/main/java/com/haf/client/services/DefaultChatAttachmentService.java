package com.haf.client.services;

import com.haf.client.core.ChatSession;
import com.haf.client.viewmodels.MessageViewModel;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Default {@link ChatAttachmentService} that delegates to the active chat
 * session.
 */
public class DefaultChatAttachmentService implements ChatAttachmentService {

    @FunctionalInterface
    interface AttachmentGateway {
        /**
         * Sends an attachment through the active message pipeline.
         *
         * @param recipientId recipient identifier
         * @param filePath    path to the file to send
         * @param mimeType    MIME type hint for transport metadata
         */
        void sendAttachment(String recipientId, Path filePath, String mimeType);
    }

    @FunctionalInterface
    interface AttachmentGatewayProvider {
        /**
         * Returns the currently available attachment gateway.
         *
         * @return gateway instance, or {@code null} when chat session is unavailable
         */
        AttachmentGateway current();
    }

    private final AttachmentGatewayProvider attachmentGatewayProvider;

    /**
     * Creates a service bound to the current {@link ChatSession} message
     * view-model.
     */
    public DefaultChatAttachmentService() {
        this(() -> {
            MessageViewModel vm = ChatSession.get();
            return vm == null ? null : vm::sendAttachment;
        });
    }

    /**
     * Creates a service with an injected gateway provider (primarily for tests).
     *
     * @param attachmentGatewayProvider provider used to resolve the current send
     *                                  gateway
     */
    DefaultChatAttachmentService(AttachmentGatewayProvider attachmentGatewayProvider) {
        this.attachmentGatewayProvider = Objects.requireNonNull(attachmentGatewayProvider, "attachmentGatewayProvider");
    }

    /**
     * Sends a local file attachment to the provided recipient when both inputs are
     * valid.
     *
     * @param recipientId recipient identifier
     * @param filePath    local path of attachment to send
     */
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

    /**
     * Derives a MIME type from attachment filename extension.
     *
     * @param path file path whose extension should be mapped
     * @return known MIME type, or {@code null} when extension is unsupported
     */
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
