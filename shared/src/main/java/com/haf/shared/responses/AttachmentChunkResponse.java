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

    /**
     * Creates an empty response instance for JSON deserialization.
     */
    public AttachmentChunkResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns the attachment identifier associated with this chunk result.
     *
     * @return attachment identifier
     */
    public String getAttachmentId() {
        return attachmentId;
    }

    /**
     * Sets the attachment identifier associated with this chunk result.
     *
     * @param attachmentId attachment identifier
     */
    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    /**
     * Returns the uploaded chunk index.
     *
     * @return zero-based chunk index
     */
    public int getChunkIndex() {
        return chunkIndex;
    }

    /**
     * Sets the uploaded chunk index.
     *
     * @param chunkIndex zero-based chunk index
     */
    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    /**
     * Indicates whether the chunk was persisted successfully.
     *
     * @return {@code true} when the chunk was stored, otherwise {@code false}
     */
    public boolean isStored() {
        return stored;
    }

    /**
     * Sets whether the chunk was persisted successfully.
     *
     * @param stored {@code true} when the chunk was stored
     */
    public void setStored(boolean stored) {
        this.stored = stored;
    }

    /**
     * Returns the error message for failed chunk uploads.
     *
     * @return error message, or {@code null} on success
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error message for failed chunk uploads.
     *
     * @param error failure reason
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Creates a successful chunk upload response.
     *
     * @param attachmentId attachment identifier
     * @param chunkIndex   zero-based uploaded chunk index
     * @param stored       storage outcome flag
     * @return populated success response
     */
    public static AttachmentChunkResponse success(String attachmentId, int chunkIndex, boolean stored) {
        AttachmentChunkResponse response = new AttachmentChunkResponse();
        response.setAttachmentId(attachmentId);
        response.setChunkIndex(chunkIndex);
        response.setStored(stored);
        return response;
    }

    /**
     * Creates an error chunk upload response.
     *
     * @param message failure message
     * @return populated error response
     */
    public static AttachmentChunkResponse error(String message) {
        AttachmentChunkResponse response = new AttachmentChunkResponse();
        response.setError(message);
        return response;
    }
}
