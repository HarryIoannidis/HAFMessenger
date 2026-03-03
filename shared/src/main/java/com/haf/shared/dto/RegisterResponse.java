package com.haf.shared.dto;

import java.io.Serializable;

/**
 * Registration response DTO returned by the server.
 *
 * On success: {@code userId} is set and {@code error} is null.
 * On failure: {@code error} contains the reason and {@code userId} is null.
 */
public class RegisterResponse implements Serializable {
    public String userId;
    public String status;
    public String message;
    public String error;

    public RegisterResponse() {
    }

    /**
     * Convenience factory for a successful response.
     */
    public static RegisterResponse success(String userId) {
        RegisterResponse r = new RegisterResponse();
        r.userId = userId;
        r.status = "PENDING";
        r.message = "Registration submitted successfully. Awaiting approval.";
        return r;
    }

    /**
     * Convenience factory for an error response.
     */
    public static RegisterResponse error(String error) {
        RegisterResponse r = new RegisterResponse();
        r.error = error;
        return r;
    }
}
