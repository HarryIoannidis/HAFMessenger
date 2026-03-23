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

    /**
     * Creates an empty init request for JSON deserialization.
     */
    public AttachmentInitRequest() {
        // Required for JSON deserialization
    }

    /**
     * Returns the recipient user identifier for the attachment.
     *
     * @return recipient user identifier
     */
    public String getRecipientId() {
        return recipientId;
    }

    /**
     * Sets the recipient user identifier for the attachment.
     *
     * @param recipientId recipient user identifier
     */
    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    /**
     * Returns the declared attachment content type.
     *
     * @return content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Sets the declared attachment content type.
     *
     * @param contentType MIME type for the uploaded attachment
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Returns the original plaintext attachment size.
     *
     * @return plaintext size in bytes
     */
    public long getPlaintextSizeBytes() {
        return plaintextSizeBytes;
    }

    /**
     * Sets the original plaintext attachment size.
     *
     * @param plaintextSizeBytes plaintext size in bytes
     */
    public void setPlaintextSizeBytes(long plaintextSizeBytes) {
        this.plaintextSizeBytes = plaintextSizeBytes;
    }

    /**
     * Returns the encrypted attachment size expected to be uploaded.
     *
     * @return encrypted size in bytes
     */
    public long getEncryptedSizeBytes() {
        return encryptedSizeBytes;
    }

    /**
     * Sets the encrypted attachment size expected to be uploaded.
     *
     * @param encryptedSizeBytes encrypted size in bytes
     */
    public void setEncryptedSizeBytes(long encryptedSizeBytes) {
        this.encryptedSizeBytes = encryptedSizeBytes;
    }

    /**
     * Returns the number of chunks expected for this upload.
     *
     * @return expected chunk count
     */
    public int getExpectedChunks() {
        return expectedChunks;
    }

    /**
     * Sets the number of chunks expected for this upload.
     *
     * @param expectedChunks expected chunk count
     */
    public void setExpectedChunks(int expectedChunks) {
        this.expectedChunks = expectedChunks;
    }
}
