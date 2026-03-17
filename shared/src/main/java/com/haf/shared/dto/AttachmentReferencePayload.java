package com.haf.shared.dto;

import java.io.Serializable;

/**
 * Reference payload sent inside an encrypted message for chunked attachments.
 */
public class AttachmentReferencePayload implements Serializable {
    private String attachmentId;
    private String fileName;
    private String mediaType;
    private long sizeBytes;

    public AttachmentReferencePayload() {
        // Required for JSON deserialization
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }
}
