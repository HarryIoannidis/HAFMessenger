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

    public AttachmentDownloadResponse() {
        // Required for JSON deserialization
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getEncryptedSizeBytes() {
        return encryptedSizeBytes;
    }

    public void setEncryptedSizeBytes(long encryptedSizeBytes) {
        this.encryptedSizeBytes = encryptedSizeBytes;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getEncryptedBlobB64() {
        return encryptedBlobB64;
    }

    public void setEncryptedBlobB64(String encryptedBlobB64) {
        this.encryptedBlobB64 = encryptedBlobB64;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

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

    public static AttachmentDownloadResponse error(String message) {
        AttachmentDownloadResponse response = new AttachmentDownloadResponse();
        response.setError(message);
        return response;
    }
}
