package com.haf.client.services;

import com.haf.shared.utils.AuthErrorCode;

/**
 * Refreshes access tokens using the server refresh-token endpoint.
 */
public interface TokenRefreshService {

    /**
     * Token-refresh result model.
     *
     * @param success                      whether refresh succeeded
     * @param invalidSession               whether server rejected refresh as
     *                                     invalid/expired
     * @param accessToken                  new access token when success
     * @param refreshToken                 rotated refresh token when success
     * @param accessExpiresAtEpochSeconds  access-token expiry epoch seconds
     * @param refreshExpiresAtEpochSeconds refresh-token expiry epoch seconds
     * @param errorCode                    typed auth/api error code for failures
     * @param errorMessage                 failure message when refresh fails
     */
    record TokenRefreshResult(
            boolean success,
            boolean invalidSession,
            String accessToken,
            String refreshToken,
            Long accessExpiresAtEpochSeconds,
            Long refreshExpiresAtEpochSeconds,
            AuthErrorCode errorCode,
            String errorMessage) {

        /**
         * Creates successful refresh result.
         *
         * @param accessToken                  new access token
         * @param refreshToken                 new refresh token
         * @param accessExpiresAtEpochSeconds  access-token expiry epoch seconds
         * @param refreshExpiresAtEpochSeconds refresh-token expiry epoch seconds
         * @return successful refresh result
         */
        public static TokenRefreshResult success(
                String accessToken,
                String refreshToken,
                Long accessExpiresAtEpochSeconds,
                Long refreshExpiresAtEpochSeconds) {
            return new TokenRefreshResult(
                    true,
                    false,
                    accessToken,
                    refreshToken,
                    accessExpiresAtEpochSeconds,
                    refreshExpiresAtEpochSeconds,
                    AuthErrorCode.UNKNOWN,
                    null);
        }

        /**
         * Creates failed refresh result.
         *
         * @param invalidSession whether failure is an invalid/expired-session case
         * @param errorCode      typed auth/api error code
         * @param errorMessage   failure message
         * @return failed refresh result
         */
        public static TokenRefreshResult failure(boolean invalidSession, AuthErrorCode errorCode, String errorMessage) {
            return new TokenRefreshResult(
                    false,
                    invalidSession,
                    null,
                    null,
                    null,
                    null,
                    errorCode == null ? AuthErrorCode.UNKNOWN : errorCode,
                    errorMessage);
        }
    }

    /**
     * Performs token refresh.
     *
     * @param refreshToken current refresh token
     * @return refresh result
     */
    TokenRefreshResult refresh(String refreshToken);
}
