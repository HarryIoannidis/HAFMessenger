package com.haf.shared.requests;

import java.io.Serializable;

/**
 * Refresh-token request DTO sent by the client.
 */
public class RefreshTokenRequest implements Serializable {
    private String refreshToken;

    /**
     * Creates an empty refresh-token request for JSON deserialization.
     */
    public RefreshTokenRequest() {
        // Required for JSON deserialization
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
}
