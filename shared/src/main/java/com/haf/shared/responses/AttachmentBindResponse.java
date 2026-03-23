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

    /**
     * Creates an empty response instance for JSON deserialization.
     */
    public AttachmentBindResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns the bound attachment identifier.
     *
     * @return attachment identifier
     */
    public String getAttachmentId() {
        return attachmentId;
    }

    /**
     * Sets the bound attachment identifier.
     *
     * @param attachmentId attachment identifier
     */
    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    /**
     * Returns the message envelope identifier the attachment was bound to.
     *
     * @return envelope identifier
     */
    public String getEnvelopeId() {
        return envelopeId;
    }

    /**
     * Sets the message envelope identifier the attachment was bound to.
     *
     * @param envelopeId envelope identifier
     */
    public void setEnvelopeId(String envelopeId) {
        this.envelopeId = envelopeId;
    }

    /**
     * Returns the attachment expiration timestamp.
     *
     * @return expiration time in epoch milliseconds
     */
    public long getExpiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    /**
     * Sets the attachment expiration timestamp.
     *
     * @param expiresAtEpochMs expiration time in epoch milliseconds
     */
    public void setExpiresAtEpochMs(long expiresAtEpochMs) {
        this.expiresAtEpochMs = expiresAtEpochMs;
    }

    /**
     * Returns the error message for failed bind operations.
     *
     * @return error message, or {@code null} on success
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error message for failed bind operations.
     *
     * @param error failure reason
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Creates a successful bind response.
     *
     * @param attachmentId    bound attachment identifier
     * @param envelopeId      target envelope identifier
     * @param expiresAtEpochMs attachment expiration timestamp in epoch milliseconds
     * @return populated success response
     */
    public static AttachmentBindResponse success(String attachmentId, String envelopeId, long expiresAtEpochMs) {
        AttachmentBindResponse response = new AttachmentBindResponse();
        response.setAttachmentId(attachmentId);
        response.setEnvelopeId(envelopeId);
        response.setExpiresAtEpochMs(expiresAtEpochMs);
        return response;
    }

    /**
     * Creates an error bind response.
     *
     * @param message failure message
     * @return populated error response
     */
    public static AttachmentBindResponse error(String message) {
        AttachmentBindResponse response = new AttachmentBindResponse();
        response.setError(message);
        return response;
    }
}
