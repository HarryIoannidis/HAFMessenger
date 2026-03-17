package com.haf.shared.requests;

import java.io.Serializable;

/**
 * Binds a completed attachment to a message envelope.
 */
public class AttachmentBindRequest implements Serializable {
    private String envelopeId;

    public AttachmentBindRequest() {
        // Required for JSON deserialization
    }

    public String getEnvelopeId() {
        return envelopeId;
    }

    public void setEnvelopeId(String envelopeId) {
        this.envelopeId = envelopeId;
    }
}
