package com.haf.client.utils;

import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.shared.responses.ApiErrorResponse;
import com.haf.shared.utils.AuthErrorCode;
import com.haf.shared.utils.JsonCodec;

/**
 * Utility methods for classifying typed auth/API errors from HTTP failures.
 */
public final class AuthErrorClassifier {
    public static final String INVALID_SESSION_MESSAGE = "invalid session";
    public static final String SESSION_TAKEOVER_MESSAGE = "session revoked by takeover";

    private AuthErrorClassifier() {
    }

    /**
     * Resolves typed error code from throwable chain.
     *
     * @param error throwable chain to inspect
     * @return resolved code, or {@link AuthErrorCode#UNKNOWN}
     */
    public static AuthErrorCode resolveCode(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpCommunicationException communicationException) {
                AuthErrorCode parsed = parseCode(communicationException.getResponseBody());
                if (parsed != AuthErrorCode.UNKNOWN) {
                    return parsed;
                }
            }
            current = current.getCause();
        }
        return AuthErrorCode.UNKNOWN;
    }

    /**
     * Resolves best-effort human-readable reason from throwable chain.
     *
     * @param error    throwable chain to inspect
     * @param fallback fallback message when reason cannot be extracted
     * @return resolved reason text
     */
    public static String resolveMessage(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpCommunicationException communicationException) {
                String parsed = parseMessage(communicationException.getResponseBody());
                if (parsed != null && !parsed.isBlank()) {
                    return parsed;
                }
            }
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                return message.trim();
            }
            current = current.getCause();
        }
        return fallback;
    }

    /**
     * Returns whether a code indicates invalid session/re-auth required.
     *
     * @param code candidate code
     * @return {@code true} when code marks invalid/revoked session
     */
    public static boolean isInvalidSessionCode(AuthErrorCode code) {
        return code == AuthErrorCode.INVALID_SESSION
                || code == AuthErrorCode.SESSION_REVOKED_BY_TAKEOVER;
    }

    /**
     * Returns whether a code indicates explicit takeover revocation.
     *
     * @param code candidate code
     * @return {@code true} when code marks takeover revocation
     */
    public static boolean isTakeoverCode(AuthErrorCode code) {
        return code == AuthErrorCode.SESSION_REVOKED_BY_TAKEOVER;
    }

    /**
     * Detects invalid-session failures from typed codes or fallback auth statuses.
     *
     * @param error candidate failure
     * @return {@code true} when failure indicates invalid/revoked session
     */
    public static boolean isInvalidSession(Throwable error) {
        AuthErrorCode code = resolveCode(error);
        if (isInvalidSessionCode(code)) {
            return true;
        }

        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpCommunicationException communicationException) {
                int status = communicationException.getStatusCode();
                if (status == 401 || status == 403) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Detects takeover-specific invalid-session failures.
     *
     * @param error candidate failure
     * @return {@code true} when failure indicates takeover revocation
     */
    public static boolean isTakeover(Throwable error) {
        return isTakeoverCode(resolveCode(error));
    }

    /**
     * Parses typed error code from an API error payload.
     *
     * @param responseBody raw response JSON
     * @return parsed code, or {@link AuthErrorCode#UNKNOWN}
     */
    public static AuthErrorCode parseCode(String responseBody) {
        ApiErrorResponse payload = parsePayload(responseBody);
        if (payload == null) {
            return AuthErrorCode.UNKNOWN;
        }
        return payload.getCode();
    }

    /**
     * Parses human-readable error message from an API error payload.
     *
     * @param responseBody raw response JSON
     * @return parsed message, or {@code null}
     */
    public static String parseMessage(String responseBody) {
        ApiErrorResponse payload = parsePayload(responseBody);
        if (payload == null) {
            return null;
        }
        String error = payload.getError();
        if (error == null || error.isBlank()) {
            return null;
        }
        return error.trim();
    }

    private static ApiErrorResponse parsePayload(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            return JsonCodec.fromJson(responseBody, ApiErrorResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }
}
