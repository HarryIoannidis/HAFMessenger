package com.haf.shared.dto;

import java.io.Serializable;

/**
 * Login response DTO returned by the server.
 *
 * <p>
 * On success: profile fields are populated and {@code error} is null.
 * On failure: {@code error} contains the reason and other fields are null.
 * </p>
 */
public class LoginResponse implements Serializable {
    public String userId;
    public String sessionId;
    public String fullName;
    public String rank;
    public String status;
    public String error;

    public LoginResponse() {
    }

    /**
     * Convenience factory for a successful login response.
     */
    public static LoginResponse success(String userId, String sessionId,
            String fullName, String rank, String status) {
        LoginResponse r = new LoginResponse();
        r.userId = userId;
        r.sessionId = sessionId;
        r.fullName = fullName;
        r.rank = rank;
        r.status = status;
        return r;
    }

    /**
     * Convenience factory for an error response.
     */
    public static LoginResponse error(String error) {
        LoginResponse r = new LoginResponse();
        r.error = error;
        return r;
    }
}
