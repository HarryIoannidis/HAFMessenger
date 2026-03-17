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

    public AttachmentInlinePayload() {
        // Required for JSON deserialization
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

    public String getDataB64() {
        return dataB64;
    }

    public void setDataB64(String dataB64) {
        this.dataB64 = dataB64;
    }
}
