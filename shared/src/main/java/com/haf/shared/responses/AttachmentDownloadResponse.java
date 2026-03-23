package com.haf.shared.responses;

import java.io.Serializable;

/**
 * Download response containing encrypted attachment blob bytes.
 */
public class AttachmentDownloadResponse implements Serializable {
    private String attachmentId;
    private String senderId;
    private String recipientId;
    private String contentType;
    private long encryptedSizeBytes;
    private int chunkCount;
    private String encryptedBlobB64;
    private String error;

    /**
     * Creates an empty download response DTO for JSON deserialization.
     */
    public AttachmentDownloadResponse() {
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
     * Returns original sender user id.
     *
     * @return sender user id
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * Sets original sender user id.
     *
     * @param senderId sender user id
     */
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    /**
     * Returns recipient user id.
     *
     * @return recipient user id
     */
    public String getRecipientId() {
        return recipientId;
    }

    /**
     * Sets recipient user id.
     *
     * @param recipientId recipient user id
     */
    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    /**
     * Returns attachment content type.
     *
     * @return attachment content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Sets attachment content type.
     *
     * @param contentType attachment content type
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Returns encrypted attachment size in bytes.
     *
     * @return encrypted attachment size in bytes
     */
    public long getEncryptedSizeBytes() {
        return encryptedSizeBytes;
    }

    /**
     * Sets encrypted attachment size in bytes.
     *
     * @param encryptedSizeBytes encrypted attachment size in bytes
     */
    public void setEncryptedSizeBytes(long encryptedSizeBytes) {
        this.encryptedSizeBytes = encryptedSizeBytes;
    }

    /**
     * Returns number of stored chunks.
     *
     * @return chunk count
     */
    public int getChunkCount() {
        return chunkCount;
    }

    /**
     * Sets number of stored chunks.
     *
     * @param chunkCount chunk count
     */
    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    /**
     * Returns base64-encoded encrypted attachment blob.
     *
     * @return base64-encoded encrypted blob
     */
    public String getEncryptedBlobB64() {
        return encryptedBlobB64;
    }

    /**
     * Sets base64-encoded encrypted attachment blob.
     *
     * @param encryptedBlobB64 base64-encoded encrypted blob
     */
    public void setEncryptedBlobB64(String encryptedBlobB64) {
        this.encryptedBlobB64 = encryptedBlobB64;
    }

    /**
     * Returns error text for failed download responses.
     *
     * @return error text
     */
    public String getError() {
        return error;
    }

    /**
     * Sets error text for failed download responses.
     *
     * @param error error text
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Creates a successful download response payload.
     *
     * @param attachmentId attachment id
     * @param senderId sender user id
     * @param recipientId recipient user id
     * @param contentType content type
     * @param encryptedSizeBytes encrypted size in bytes
     * @param chunkCount number of chunks
     * @param encryptedBlobB64 base64-encoded encrypted blob
     * @return populated success response
     */
    public static AttachmentDownloadResponse success(String attachmentId,
            String senderId,
            String recipientId,
            String contentType,
            long encryptedSizeBytes,
            int chunkCount,
            String encryptedBlobB64) {
        AttachmentDownloadResponse response = new AttachmentDownloadResponse();
        response.setAttachmentId(attachmentId);
        response.setSenderId(senderId);
        response.setRecipientId(recipientId);
        response.setContentType(contentType);
        response.setEncryptedSizeBytes(encryptedSizeBytes);
        response.setChunkCount(chunkCount);
        response.setEncryptedBlobB64(encryptedBlobB64);
        return response;
    }

    /**
     * Creates an error download response payload.
     *
     * @param message error text
     * @return populated error response
     */
    public static AttachmentDownloadResponse error(String message) {
        AttachmentDownloadResponse response = new AttachmentDownloadResponse();
        response.setError(message);
        return response;
    }
}
