package com.haf.client.services;

import java.nio.file.Path;

/**
 * Application service for sending chat attachments.
 */
public interface ChatAttachmentService {

    /**
     * Sends an attachment for the given recipient.
     * 
     * @param recipientId recipient identifier
     * @param filePath    local path of attachment to send
     */
    void sendAttachment(String recipientId, Path filePath);
}
