package com.haf.shared.dto;

import java.io.Serializable;

/**
 * Reference payload sent inside an encrypted message for chunked attachments.
 *
 * Field meanings:
 * - {@code attachmentId}: attachment id used by the attachment API.
 * - {@code fileName}: attachment file name for UI display/save dialogs.
 * - {@code mediaType}: attachment media type.
 * - {@code sizeBytes}: plaintext attachment size in bytes.
 */
public class AttachmentReferencePayload implements Serializable {
    private String attachmentId;
    private String fileName;
    private String mediaType;
    private long sizeBytes;

    /**
     * Creates an empty attachment-reference payload for JSON deserialization.
     */
    public AttachmentReferencePayload() {
        // Required for JSON deserialization
    }

    /**
     * Returns attachment id used by the attachment API.
     *
     * @return attachment id
     */
    public String getAttachmentId() {
        return attachmentId;
    }

    /**
     * Sets attachment id used by the attachment API.
     *
     * @param attachmentId attachment id
     */
    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    /**
     * Returns attachment file name for UI display/save dialogs.
     *
     * @return attachment file name
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * Sets attachment file name for UI display/save dialogs.
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
}
