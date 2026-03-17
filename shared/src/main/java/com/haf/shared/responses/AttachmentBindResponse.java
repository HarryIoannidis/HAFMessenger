package com.haf.shared.responses;

import java.io.Serializable;

/**
 * Response for attachment bind operation.
 */
public class AttachmentBindResponse implements Serializable {
    private String attachmentId;
    private String envelopeId;
    private long expiresAtEpochMs;
    private String error;

    public AttachmentBindResponse() {
        // Required for JSON deserialization
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getEnvelopeId() {
        return envelopeId;
    }

    public void setEnvelopeId(String envelopeId) {
        this.envelopeId = envelopeId;
    }

    public long getExpiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    public void setExpiresAtEpochMs(long expiresAtEpochMs) {
        this.expiresAtEpochMs = expiresAtEpochMs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public static AttachmentBindResponse success(String attachmentId, String envelopeId, long expiresAtEpochMs) {
        AttachmentBindResponse response = new AttachmentBindResponse();
        response.setAttachmentId(attachmentId);
        response.setEnvelopeId(envelopeId);
        response.setExpiresAtEpochMs(expiresAtEpochMs);
        return response;
    }

    public static AttachmentBindResponse error(String message) {
        AttachmentBindResponse response = new AttachmentBindResponse();
        response.setError(message);
        return response;
    }
}
