package com.haf.shared.dto;

public class PublicKeyResponse {
    public String userId;
    public String publicKeyPem;
    public String fingerprint;
    public String error;
    public boolean success;

    // Default constructor for Jackson
    public PublicKeyResponse() {
    }

    public static PublicKeyResponse success(String userId, String publicKeyPem, String fingerprint) {
        PublicKeyResponse r = new PublicKeyResponse();
        r.success = true;
        r.userId = userId;
        r.publicKeyPem = publicKeyPem;
        r.fingerprint = fingerprint;
        return r;
    }

    public static PublicKeyResponse error(String message) {
        PublicKeyResponse r = new PublicKeyResponse();
        r.success = false;
        r.error = message;
        return r;
    }
}
