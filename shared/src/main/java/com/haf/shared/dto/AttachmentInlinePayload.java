package com.haf.shared.dto;

import java.io.Serializable;

/**
 * Inline attachment payload sent inside an encrypted message.
 */
public class AttachmentInlinePayload implements Serializable {
    private String fileName;
    private String mediaType;
    private long sizeBytes;
    private String dataB64;

    /**
     * Creates an empty inline-attachment payload for JSON deserialization.
     */
    public AttachmentInlinePayload() {
        // Required for JSON deserialization
    }

    /**
     * Returns attachment file name for UI display.
     *
     * @return attachment file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets attachment file name for UI display.
     *
     * @param fileName attachment file name
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * Returns attachment media type.
     *
     * @return attachment media type
     */
    public String getMediaType() {
        return mediaType;
    }

    /**
     * Sets attachment media type.
     *
     * @param mediaType attachment media type
     */
    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    /**
     * Returns plaintext attachment size in bytes.
     *
     * @return plaintext attachment size in bytes
     */
    public long getSizeBytes() {
        return sizeBytes;
    }

    /**
     * Sets plaintext attachment size in bytes.
     *
     * @param sizeBytes plaintext attachment size in bytes
     */
    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    /**
     * Returns base64-encoded inline attachment bytes.
     *
     * @return base64-encoded attachment bytes
     */
    public String getDataB64() {
        return dataB64;
    }

    /**
     * Sets base64-encoded inline attachment bytes.
     *
     * @param dataB64 base64-encoded attachment bytes
     */
    public void setDataB64(String dataB64) {
        this.dataB64 = dataB64;
    }
}
