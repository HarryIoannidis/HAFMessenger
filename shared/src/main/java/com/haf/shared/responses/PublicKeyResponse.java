package com.haf.shared.responses;

/**
 * Response DTO for public-key lookup requests.
 */
public class PublicKeyResponse {
    private String userId;
    private String publicKeyPem;
    private String fingerprint;
    private String error;
    private boolean success;

    /**
     * Creates an empty public-key response DTO for JSON deserialization.
     */
    public PublicKeyResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns target user id.
     *
     * @return target user id
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets target user id.
     *
     * @param userId target user id
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns public key in PEM format.
     *
     * @return public key PEM
     */
    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    /**
     * Sets public key in PEM format.
     *
     * @param publicKeyPem public key PEM
     */
    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    /**
     * Returns key fingerprint value.
     *
     * @return key fingerprint
     */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Sets key fingerprint value.
     *
     * @param fingerprint key fingerprint
     */
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    /**
     * Returns error text for failed responses.
     *
     * @return error text
     */
    public String getError() {
        return error;
    }

    /**
     * Sets error text for failed responses.
     *
     * @param error error text
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Returns whether the response indicates success.
     *
     * @return {@code true} when response indicates success
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Sets whether the response indicates success.
     *
     * @param success success flag
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Creates a successful public-key response.
     *
     * @param userId target user id
     * @param publicKeyPem public key PEM
     * @param fingerprint key fingerprint
     * @return populated success response
     */
    public static PublicKeyResponse success(String userId, String publicKeyPem, String fingerprint) {
        PublicKeyResponse r = new PublicKeyResponse();
        r.setSuccess(true);
        r.setUserId(userId);
        r.setPublicKeyPem(publicKeyPem);
        r.setFingerprint(fingerprint);
        return r;
    }

    /**
     * Creates a failed public-key response.
     *
     * @param message error text
     * @return populated error response
     */
    public static PublicKeyResponse error(String message) {
        PublicKeyResponse r = new PublicKeyResponse();
        r.setSuccess(false);
        r.setError(message);
        return r;
    }
}
