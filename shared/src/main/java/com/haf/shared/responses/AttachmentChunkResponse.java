package com.haf.shared.responses;

import java.io.Serializable;

/**
 * Response for one uploaded chunk.
 */
public class AttachmentChunkResponse implements Serializable {
    private String attachmentId;
    private int chunkIndex;
    private boolean stored;
    private String error;

    public AttachmentChunkResponse() {
        // Required for JSON deserialization
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public boolean isStored() {
        return stored;
    }

    public void setStored(boolean stored) {
        this.stored = stored;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public static AttachmentChunkResponse success(String attachmentId, int chunkIndex, boolean stored) {
        AttachmentChunkResponse response = new AttachmentChunkResponse();
        response.setAttachmentId(attachmentId);
        response.setChunkIndex(chunkIndex);
        response.setStored(stored);
        return response;
    }

    public static AttachmentChunkResponse error(String message) {
        AttachmentChunkResponse response = new AttachmentChunkResponse();
        response.setError(message);
        return response;
    }
}
