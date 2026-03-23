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
}
