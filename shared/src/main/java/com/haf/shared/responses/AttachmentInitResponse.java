package com.haf.shared.responses;

import java.io.Serializable;

/**
 * Response returned by attachment upload init endpoint.
 */
public class AttachmentInitResponse implements Serializable {
    private String attachmentId;
    private int chunkBytes;
    private long expiresAtEpochMs;
    private String error;

    public AttachmentInitResponse() {
        // Required for JSON deserialization
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public int getChunkBytes() {
        return chunkBytes;
    }

    public void setChunkBytes(int chunkBytes) {
        this.chunkBytes = chunkBytes;
    }

    public long getExpiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    public void setExpiresAtEpochMs(long expiresAtEpochMs) {
        this.expiresAtEpochMs = expiresAtEpochMs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public static AttachmentInitResponse success(String attachmentId, int chunkBytes, long expiresAtEpochMs) {
        AttachmentInitResponse response = new AttachmentInitResponse();
        response.setAttachmentId(attachmentId);
        response.setChunkBytes(chunkBytes);
        response.setExpiresAtEpochMs(expiresAtEpochMs);
        return response;
    }

    public static AttachmentInitResponse error(String message) {
        AttachmentInitResponse response = new AttachmentInitResponse();
        response.setError(message);
        return response;
    }
}
