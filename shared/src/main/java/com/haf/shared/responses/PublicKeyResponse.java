package com.haf.shared.responses;

/**
 * Response DTO for public-key lookup requests.
 */
public class PublicKeyResponse {
    private String userId;
    private String publicKeyPem;
    private String fingerprint;
    private String signingPublicKeyPem;
    private String signingFingerprint;
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
     * Returns signing public key in PEM format.
     *
     * @return signing public key PEM
     */
    public String getSigningPublicKeyPem() {
        return signingPublicKeyPem;
    }

    /**
     * Sets signing public key in PEM format.
     *
     * @param signingPublicKeyPem signing public key PEM
     */
    public void setSigningPublicKeyPem(String signingPublicKeyPem) {
        this.signingPublicKeyPem = signingPublicKeyPem;
    }

    /**
     * Returns signing-key fingerprint value.
     *
     * @return signing-key fingerprint
     */
    public String getSigningFingerprint() {
        return signingFingerprint;
    }

    /**
     * Sets signing-key fingerprint value.
     *
     * @param signingFingerprint signing-key fingerprint
     */
    public void setSigningFingerprint(String signingFingerprint) {
        this.signingFingerprint = signingFingerprint;
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
        return success(userId, publicKeyPem, fingerprint, null, null);
    }

    /**
     * Creates a successful public-key response including signing-key metadata.
     *
     * @param userId target user id
     * @param publicKeyPem encryption public key PEM
     * @param fingerprint encryption key fingerprint
     * @param signingPublicKeyPem signing public key PEM
     * @param signingFingerprint signing key fingerprint
     * @return populated success response
     */
    public static PublicKeyResponse success(
            String userId,
            String publicKeyPem,
            String fingerprint,
            String signingPublicKeyPem,
            String signingFingerprint) {
        PublicKeyResponse r = new PublicKeyResponse();
        r.setSuccess(true);
        r.setUserId(userId);
        r.setPublicKeyPem(publicKeyPem);
        r.setFingerprint(fingerprint);
        r.setSigningPublicKeyPem(signingPublicKeyPem);
        r.setSigningFingerprint(signingFingerprint);
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
