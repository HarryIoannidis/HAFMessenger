package com.haf.shared.responses;

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

    /**
     * Creates an empty registration response for JSON deserialization.
     */
    public RegisterResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns the created user identifier on success.
     *
     * @return created user identifier, or {@code null} on failure
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the created user identifier.
     *
     * @param userId created user identifier
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns the registration state reported by the server.
     *
     * @return registration status string
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the registration state reported by the server.
     *
     * @param status registration status string
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns the human-readable status message.
     *
     * @return status message text
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the human-readable status message.
     *
     * @param message status message text
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Returns the error reason when registration fails.
     *
     * @return error message, or {@code null} on success
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error reason when registration fails.
     *
     * @param error registration failure reason
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Convenience factory for a successful response.
     *
     * @param userId created user identifier
     * @return populated success response
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
     *
     * @param error registration failure reason
     * @return populated error response
     */
    public static RegisterResponse error(String error) {
        RegisterResponse r = new RegisterResponse();
        r.setError(error);
        return r;
    }
}
