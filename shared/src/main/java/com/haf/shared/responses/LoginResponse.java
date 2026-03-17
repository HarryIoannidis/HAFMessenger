package com.haf.shared.responses;

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
    private String userId;
    private String sessionId;
    private String fullName;
    private String rank;
    private String status;
    private String error;

    public LoginResponse() {
        // Required for JSON deserialization
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public static LoginResponse success(String userId, String sessionId,
            String fullName, String rank, String status) {
        LoginResponse r = new LoginResponse();
        r.setUserId(userId);
        r.setSessionId(sessionId);
        r.setFullName(fullName);
        r.setRank(rank);
        r.setStatus(status);
        return r;
    }

    public static LoginResponse error(String error) {
        LoginResponse r = new LoginResponse();
        r.setError(error);
        return r;
    }
}
