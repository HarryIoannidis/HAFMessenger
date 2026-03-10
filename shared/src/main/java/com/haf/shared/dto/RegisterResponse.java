package com.haf.shared.dto;

import java.io.Serializable;

/**
 * Registration response DTO returned by the server.
 *
 * On success: {@code userId} is set and {@code error} is null.
 * On failure: {@code error} contains the reason and {@code userId} is null.
 */
public class RegisterResponse implements Serializable {
    private String userId;
    private String status;
    private String message;
    private String error;

    public RegisterResponse() {
        // Required for JSON deserialization
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * Convenience factory for a successful response.
     */
    public static RegisterResponse success(String userId) {
        RegisterResponse r = new RegisterResponse();
        r.setUserId(userId);
        r.setStatus("PENDING");
        r.setMessage("Registration submitted successfully. Awaiting approval.");
        return r;
    }

    /**
     * Convenience factory for an error response.
     */
    public static RegisterResponse error(String error) {
        RegisterResponse r = new RegisterResponse();
        r.setError(error);
        return r;
    }
}
