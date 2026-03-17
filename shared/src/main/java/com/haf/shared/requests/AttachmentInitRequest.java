package com.haf.shared.requests;

import java.io.Serializable;

/**
 * Starts an attachment upload session.
 */
public class AttachmentInitRequest implements Serializable {
    private String recipientId;
    private String contentType;
    private long plaintextSizeBytes;
    private long encryptedSizeBytes;
    private int expectedChunks;

    public AttachmentInitRequest() {
        // Required for JSON deserialization
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

    public long getPlaintextSizeBytes() {
        return plaintextSizeBytes;
    }

    public void setPlaintextSizeBytes(long plaintextSizeBytes) {
        this.plaintextSizeBytes = plaintextSizeBytes;
    }

    public long getEncryptedSizeBytes() {
        return encryptedSizeBytes;
    }

    public void setEncryptedSizeBytes(long encryptedSizeBytes) {
        this.encryptedSizeBytes = encryptedSizeBytes;
    }

    public int getExpectedChunks() {
        return expectedChunks;
    }

    public void setExpectedChunks(int expectedChunks) {
        this.expectedChunks = expectedChunks;
    }
}
