package com.haf.shared.requests;

import java.io.Serializable;

/**
 * Login request DTO sent by the client.
 *
 * <p>
 * Password is sent in plaintext over TLS; the server verifies it against
 * the stored BCrypt hash.
 * </p>
 */
public class LoginRequest implements Serializable {
    private String email;
    private String password;

    public LoginRequest() {
        // Required for JSON deserialization
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
