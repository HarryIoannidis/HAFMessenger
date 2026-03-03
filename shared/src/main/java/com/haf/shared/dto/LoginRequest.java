package com.haf.shared.dto;

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
    public String email;
    public String password;
}
