package com.haf.shared.requests;

import java.io.Serializable;

/**
 * Completes an attachment upload.
 */
public class AttachmentCompleteRequest implements Serializable {
    private int expectedChunks;
    private long encryptedSizeBytes;

    public AttachmentCompleteRequest() {
        // Required for JSON deserialization
    }

    public int getExpectedChunks() {
        return expectedChunks;
    }

    public void setExpectedChunks(int expectedChunks) {
        this.expectedChunks = expectedChunks;
    }

    public long getEncryptedSizeBytes() {
        return encryptedSizeBytes;
    }

    public void setEncryptedSizeBytes(long encryptedSizeBytes) {
        this.encryptedSizeBytes = encryptedSizeBytes;
    }
}
