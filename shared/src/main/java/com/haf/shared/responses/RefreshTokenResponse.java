package com.haf.shared.responses;

import java.io.Serializable;

/**
 * Refresh-token response DTO returned by the server.
 */
public class RefreshTokenResponse implements Serializable {
    private String sessionId;
    private String refreshToken;
    private Long accessExpiresAtEpochSeconds;
    private Long refreshExpiresAtEpochSeconds;
    private String error;

    /**
     * Creates an empty response for JSON deserialization.
     */
    public RefreshTokenResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns access JWT.
     *
     * @return access JWT
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets access JWT.
     *
     * @param sessionId access JWT
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Returns refresh token.
     *
     * @return refresh token
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Sets refresh token.
     *
     * @param refreshToken refresh token
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    /**
     * Returns access-token expiry epoch seconds.
     *
     * @return access-token expiry epoch seconds
     */
    public Long getAccessExpiresAtEpochSeconds() {
        return accessExpiresAtEpochSeconds;
    }

    /**
     * Sets access-token expiry epoch seconds.
     *
     * @param accessExpiresAtEpochSeconds access-token expiry epoch seconds
     */
    public void setAccessExpiresAtEpochSeconds(Long accessExpiresAtEpochSeconds) {
        this.accessExpiresAtEpochSeconds = accessExpiresAtEpochSeconds;
    }

    /**
     * Returns refresh-token expiry epoch seconds.
     *
     * @return refresh-token expiry epoch seconds
     */
    public Long getRefreshExpiresAtEpochSeconds() {
        return refreshExpiresAtEpochSeconds;
    }

    /**
     * Sets refresh-token expiry epoch seconds.
     *
     * @param refreshExpiresAtEpochSeconds refresh-token expiry epoch seconds
     */
    public void setRefreshExpiresAtEpochSeconds(Long refreshExpiresAtEpochSeconds) {
        this.refreshExpiresAtEpochSeconds = refreshExpiresAtEpochSeconds;
    }

    /**
     * Returns error text for failed refresh responses.
     *
     * @return error text
     */
    public String getError() {
        return error;
    }

    /**
     * Sets error text for failed refresh responses.
     *
     * @param error error text
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Returns whether response is successful.
     *
     * @return true when error is absent
     */
    public boolean isSuccess() {
        return error == null || error.isBlank();
    }

    /**
     * Creates success response.
     *
     * @param accessToken access JWT
     * @param refreshToken refresh token
     * @param accessExpiresAtEpochSeconds access-token expiry epoch seconds
     * @param refreshExpiresAtEpochSeconds refresh-token expiry epoch seconds
     * @return success response
     */
    public static RefreshTokenResponse success(
            String accessToken,
            String refreshToken,
            long accessExpiresAtEpochSeconds,
            long refreshExpiresAtEpochSeconds) {
        RefreshTokenResponse response = new RefreshTokenResponse();
        response.setSessionId(accessToken);
        response.setRefreshToken(refreshToken);
        response.setAccessExpiresAtEpochSeconds(accessExpiresAtEpochSeconds);
        response.setRefreshExpiresAtEpochSeconds(refreshExpiresAtEpochSeconds);
        return response;
    }

    /**
     * Creates failure response.
     *
     * @param error error message
     * @return failure response
     */
    public static RefreshTokenResponse error(String error) {
        RefreshTokenResponse response = new RefreshTokenResponse();
        response.setError(error);
        return response;
    }
}
