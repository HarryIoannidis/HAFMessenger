package com.haf.shared.requests;

import java.io.Serializable;

/**
 * Login request DTO sent by the client.
 *
 * Password is sent in plaintext over TLS; the server verifies it against
 * the stored BCrypt hash.
 */
public class LoginRequest implements Serializable {
    private String email;
    private String password;
    private Boolean forceTakeover;
    private String takeoverPublicKeyPem;
    private String takeoverPublicKeyFingerprint;

    /**
     * Creates an empty login request for JSON deserialization.
     */
    public LoginRequest() {
        // Required for JSON deserialization
    }

    /**
     * Returns the email identifier used for login.
     *
     * @return account email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email identifier used for login.
     *
     * @param email account email address
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the plaintext password submitted over TLS.
     *
     * @return plaintext password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the plaintext password submitted over TLS.
     *
     * @param password plaintext password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns whether this login should force account takeover.
     *
     * @return takeover flag, or {@code null} when not provided
     */
    public Boolean getForceTakeover() {
        return forceTakeover;
    }

    /**
     * Sets whether this login should force account takeover.
     *
     * @param forceTakeover takeover flag
     */
    public void setForceTakeover(Boolean forceTakeover) {
        this.forceTakeover = forceTakeover;
    }

    /**
     * Returns the PEM-encoded public key used for takeover rotation.
     *
     * @return takeover public key PEM
     */
    public String getTakeoverPublicKeyPem() {
        return takeoverPublicKeyPem;
    }

    /**
     * Sets the PEM-encoded public key used for takeover rotation.
     *
     * @param takeoverPublicKeyPem takeover public key PEM
     */
    public void setTakeoverPublicKeyPem(String takeoverPublicKeyPem) {
        this.takeoverPublicKeyPem = takeoverPublicKeyPem;
    }

    /**
     * Returns the SHA-256 fingerprint associated with takeover public key PEM.
     *
     * @return takeover public key fingerprint
     */
    public String getTakeoverPublicKeyFingerprint() {
        return takeoverPublicKeyFingerprint;
    }

    /**
     * Sets the SHA-256 fingerprint associated with takeover public key PEM.
     *
     * @param takeoverPublicKeyFingerprint takeover public key fingerprint
     */
    public void setTakeoverPublicKeyFingerprint(String takeoverPublicKeyFingerprint) {
        this.takeoverPublicKeyFingerprint = takeoverPublicKeyFingerprint;
    }
}
