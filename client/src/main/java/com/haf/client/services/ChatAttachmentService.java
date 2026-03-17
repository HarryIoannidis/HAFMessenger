package com.haf.client.services;

import java.nio.file.Path;

/**
 * Application service for sending chat attachments.
 */
public interface ChatAttachmentService {

    /**
     * Sends an attachment for the given recipient.
     */
    void sendAttachment(String recipientId, Path filePath);
}
