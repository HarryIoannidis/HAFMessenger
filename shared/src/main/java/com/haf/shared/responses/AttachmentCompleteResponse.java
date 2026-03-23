package com.haf.shared.responses;

import java.io.Serializable;

/**
 * Response for attachment upload completion.
 */
public class AttachmentCompleteResponse implements Serializable {
    private String attachmentId;
    private int receivedChunks;
    private long receivedBytes;
    private String status;
    private String error;

    /**
     * Creates an empty attachment-complete response DTO for JSON deserialization.
     */
    public AttachmentCompleteResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns attachment id.
     *
     * @return attachment id
     */
    public String getAttachmentId() {
        return attachmentId;
    }

    /**
     * Sets attachment id.
     *
     * @param attachmentId attachment id
     */
    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    /**
     * Returns number of received chunks.
     *
     * @return number of received chunks
     */
    public int getReceivedChunks() {
        return receivedChunks;
    }

    /**
     * Sets number of received chunks.
     *
     * @param receivedChunks number of received chunks
     */
    public void setReceivedChunks(int receivedChunks) {
        this.receivedChunks = receivedChunks;
    }

    /**
     * Returns number of received bytes.
     *
     * @return number of received bytes
     */
    public long getReceivedBytes() {
        return receivedBytes;
    }

    /**
     * Sets number of received bytes.
     *
     * @param receivedBytes number of received bytes
     */
    public void setReceivedBytes(long receivedBytes) {
        this.receivedBytes = receivedBytes;
    }

    /**
     * Returns upload status after completion.
     *
     * @return upload status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets upload status after completion.
     *
     * @param status upload status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns error text for failed completion responses.
     *
     * @return error text
     */
    public String getError() {
        return error;
    }

    /**
     * Sets error text for failed completion responses.
     *
     * @param error error text
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Creates a successful attachment-complete response.
     *
     * @param attachmentId attachment id
     * @param receivedChunks number of received chunks
     * @param receivedBytes number of received bytes
     * @param status resulting upload status
     * @return populated success response
     */
    public static AttachmentCompleteResponse success(String attachmentId, int receivedChunks, long receivedBytes,
            String status) {
        AttachmentCompleteResponse response = new AttachmentCompleteResponse();
        response.setAttachmentId(attachmentId);
        response.setReceivedChunks(receivedChunks);
        response.setReceivedBytes(receivedBytes);
        response.setStatus(status);
        return response;
    }

    /**
     * Creates a failed attachment-complete response.
     *
     * @param message error text
     * @return populated error response
     */
    public static AttachmentCompleteResponse error(String message) {
        AttachmentCompleteResponse response = new AttachmentCompleteResponse();
        response.setError(message);
        return response;
    }
}
