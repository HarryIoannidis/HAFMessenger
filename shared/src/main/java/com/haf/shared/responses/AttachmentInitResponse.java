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

    /**
     * Creates an empty response instance for JSON deserialization.
     */
    public AttachmentInitResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns the allocated attachment identifier.
     *
     * @return the attachment identifier, or {@code null} when unavailable
     */
    public String getAttachmentId() {
        return attachmentId;
    }

    /**
     * Sets the allocated attachment identifier.
     *
     * @param attachmentId attachment identifier returned by the server
     */
    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    /**
     * Returns the chunk size that clients should use for uploads.
     *
     * @return chunk size in bytes
     */
    public int getChunkBytes() {
        return chunkBytes;
    }

    /**
     * Sets the chunk size that clients should use for uploads.
     *
     * @param chunkBytes upload chunk size in bytes
     */
    public void setChunkBytes(int chunkBytes) {
        this.chunkBytes = chunkBytes;
    }

    /**
     * Returns the upload session expiration time.
     *
     * @return expiration timestamp in epoch milliseconds
     */
    public long getExpiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    /**
     * Sets the upload session expiration time.
     *
     * @param expiresAtEpochMs expiration timestamp in epoch milliseconds
     */
    public void setExpiresAtEpochMs(long expiresAtEpochMs) {
        this.expiresAtEpochMs = expiresAtEpochMs;
    }

    /**
     * Returns the error message for failed initialization attempts.
     *
     * @return error message, or {@code null} when initialization succeeded
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error message for failed initialization attempts.
     *
     * @param error failure reason
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Creates a successful init response.
     *
     * @param attachmentId    allocated attachment identifier
     * @param chunkBytes      upload chunk size in bytes
     * @param expiresAtEpochMs upload session expiration timestamp in epoch milliseconds
     * @return populated success response
     */
    public static AttachmentInitResponse success(String attachmentId, int chunkBytes, long expiresAtEpochMs) {
        AttachmentInitResponse response = new AttachmentInitResponse();
        response.setAttachmentId(attachmentId);
        response.setChunkBytes(chunkBytes);
        response.setExpiresAtEpochMs(expiresAtEpochMs);
        return response;
    }

    /**
     * Creates an error init response.
     *
     * @param message failure message
     * @return populated error response
     */
    public static AttachmentInitResponse error(String message) {
        AttachmentInitResponse response = new AttachmentInitResponse();
        response.setError(message);
        return response;
    }
}
