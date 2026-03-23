package com.haf.shared.requests;

import java.io.Serializable;

/**
 * Binds a completed attachment to a message envelope.
 */
public class AttachmentBindRequest implements Serializable {
    private String envelopeId;

    /**
     * Creates an empty bind request for JSON deserialization.
     */
    public AttachmentBindRequest() {
        // Required for JSON deserialization
    }

    /**
     * Returns the envelope identifier to bind the attachment to.
     *
     * @return envelope identifier
     */
    public String getEnvelopeId() {
        return envelopeId;
    }

    /**
     * Sets the envelope identifier to bind the attachment to.
     *
     * @param envelopeId envelope identifier
     */
    public void setEnvelopeId(String envelopeId) {
        this.envelopeId = envelopeId;
    }
}
