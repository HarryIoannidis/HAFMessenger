package com.haf.shared.dto;

public class PublicKeyResponse {
    private String userId;
    private String publicKeyPem;
    private String fingerprint;
    private String error;
    private boolean success;

    // Default constructor for Jackson
    public PublicKeyResponse() {
        // Required for JSON deserialization
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public static PublicKeyResponse success(String userId, String publicKeyPem, String fingerprint) {
        PublicKeyResponse r = new PublicKeyResponse();
        r.setSuccess(true);
        r.setUserId(userId);
        r.setPublicKeyPem(publicKeyPem);
        r.setFingerprint(fingerprint);
        return r;
    }

    public static PublicKeyResponse error(String message) {
        PublicKeyResponse r = new PublicKeyResponse();
        r.setSuccess(false);
        r.setError(message);
        return r;
    }
}
