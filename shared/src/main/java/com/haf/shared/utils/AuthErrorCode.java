package com.haf.shared.utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Canonical auth/API error codes exchanged over HTTPS response payloads.
 */
public enum AuthErrorCode {
    METHOD_NOT_ALLOWED("method_not_allowed"),
    INVALID_REQUEST("invalid_request"),
    INVALID_CREDENTIALS("invalid_credentials"),
    ACCOUNT_NOT_APPROVED("account_not_approved"),
    DUPLICATE_SESSION("duplicate_session"),
    RATE_LIMIT("rate_limit"),
    UNAUTHORIZED("unauthorized"),
    FORBIDDEN("forbidden"),
    INVALID_SESSION("invalid_session"),
    SESSION_REVOKED_BY_TAKEOVER("session_revoked_by_takeover"),
    CONFLICT("conflict"),
    INTERNAL_SERVER_ERROR("internal_server_error"),
    UNKNOWN("unknown");

    private static final Map<String, AuthErrorCode> BY_WIRE_VALUE = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(AuthErrorCode::wireValue, value -> value));

    private final String wireValue;

    AuthErrorCode(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the wire value persisted in JSON payloads.
     *
     * @return normalized wire code
     */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Parses wire value into enum representation.
     *
     * @param code wire code from JSON payload
     * @return matching enum value, or {@link #UNKNOWN} when absent/unrecognized
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AuthErrorCode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        return BY_WIRE_VALUE.getOrDefault(code.trim().toLowerCase(Locale.ROOT), UNKNOWN);
    }
}
