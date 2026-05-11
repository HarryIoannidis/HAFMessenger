package com.haf.shared.responses;

import java.io.Serializable;

import com.haf.shared.utils.AuthErrorCode;

/**
 * Generic API error payload for HTTPS endpoints.
 */
public class ApiErrorResponse implements Serializable {
    private AuthErrorCode code;
    private String error;
    private Long retryAfterSeconds;

    /**
     * Creates an empty payload for JSON deserialization.
     */
    public ApiErrorResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns typed error code.
     *
     * @return typed error code
     */
    public AuthErrorCode getCode() {
        return code == null ? AuthErrorCode.UNKNOWN : code;
    }

    /**
     * Sets typed error code.
     *
     * @param code typed error code
     */
    public void setCode(AuthErrorCode code) {
        this.code = code == null ? AuthErrorCode.UNKNOWN : code;
    }

    /**
     * Returns human-readable error reason.
     *
     * @return error message text
     */
    public String getError() {
        return error;
    }

    /**
     * Sets human-readable error reason.
     *
     * @param error error message text
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Returns optional retry-after value in seconds.
     *
     * @return retry-after seconds, or {@code null}
     */
    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * Sets optional retry-after value in seconds.
     *
     * @param retryAfterSeconds retry-after seconds
     */
    public void setRetryAfterSeconds(Long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Creates error payload.
     *
     * @param code  typed code
     * @param error error message
     * @return error payload
     */
    public static ApiErrorResponse error(AuthErrorCode code, String error) {
        ApiErrorResponse response = new ApiErrorResponse();
        response.setCode(code);
        response.setError(error);
        return response;
    }

    /**
     * Creates rate-limit payload.
     *
     * @param retryAfterSeconds retry-after seconds
     * @return rate-limit payload
     */
    public static ApiErrorResponse rateLimit(long retryAfterSeconds) {
        ApiErrorResponse response = error(AuthErrorCode.RATE_LIMIT, "rate limit");
        response.setRetryAfterSeconds(Math.max(1L, retryAfterSeconds));
        return response;
    }
}
