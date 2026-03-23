package com.haf.shared.requests;

import java.io.Serializable;

/**
 * Completes an attachment upload.
 */
public class AttachmentCompleteRequest implements Serializable {
    private int expectedChunks;
    private long encryptedSizeBytes;

    /**
     * Creates an empty completion request for JSON deserialization.
     */
    public AttachmentCompleteRequest() {
        // Required for JSON deserialization
    }

    /**
     * Returns the total number of chunks expected for this upload.
     *
     * @return expected chunk count
     */
    public int getExpectedChunks() {
        return expectedChunks;
    }

    /**
     * Sets the total number of chunks expected for this upload.
     *
     * @param expectedChunks expected chunk count
     */
    public void setExpectedChunks(int expectedChunks) {
        this.expectedChunks = expectedChunks;
    }

    /**
     * Returns the expected total encrypted payload size.
     *
     * @return encrypted size in bytes
     */
    public long getEncryptedSizeBytes() {
        return encryptedSizeBytes;
    }

    /**
     * Sets the expected total encrypted payload size.
     *
     * @param encryptedSizeBytes encrypted size in bytes
     */
    public void setEncryptedSizeBytes(long encryptedSizeBytes) {
        this.encryptedSizeBytes = encryptedSizeBytes;
    }
}
