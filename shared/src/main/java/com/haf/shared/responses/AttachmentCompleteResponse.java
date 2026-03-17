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

    public AttachmentCompleteResponse() {
        // Required for JSON deserialization
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public int getReceivedChunks() {
        return receivedChunks;
    }

    public void setReceivedChunks(int receivedChunks) {
        this.receivedChunks = receivedChunks;
    }

    public long getReceivedBytes() {
        return receivedBytes;
    }

    public void setReceivedBytes(long receivedBytes) {
        this.receivedBytes = receivedBytes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public static AttachmentCompleteResponse success(String attachmentId, int receivedChunks, long receivedBytes,
            String status) {
        AttachmentCompleteResponse response = new AttachmentCompleteResponse();
        response.setAttachmentId(attachmentId);
        response.setReceivedChunks(receivedChunks);
        response.setReceivedBytes(receivedBytes);
        response.setStatus(status);
        return response;
    }

    public static AttachmentCompleteResponse error(String message) {
        AttachmentCompleteResponse response = new AttachmentCompleteResponse();
        response.setError(message);
        return response;
    }
}
