package com.haf.shared.websocket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Canonical realtime error codes exchanged over the websocket protocol.
 */
public enum RealtimeErrorCode {
    INVALID_PAYLOAD("invalid_payload"),
    INVALID_TYPE("invalid_type"),
    RATE_LIMIT("rate_limit"),
    UNSUPPORTED_EVENT("unsupported_event"),
    UNSUPPORTED_FRAME("unsupported_frame"),
    INVALID_CLIENT_MESSAGE_ID("invalid_client_message_id"),
    INVALID_RECEIPT("invalid_receipt"),
    UNAUTHORIZED("unauthorized"),
    SESSION_REPLACED("session_replaced"),
    INVALID_SESSION("invalid_session"),
    INVALID_EVENT_METADATA("invalid_event_metadata"),
    STALE_EVENT("stale_event"),
    REPLAY_REJECTED("replay_rejected"),
    INVALID_SIGNATURE("invalid_signature"),
    SENDER_MISMATCH("sender_mismatch"),
    STALE_RECIPIENT_KEY("stale_recipient_key"),
    UNKNOWN("unknown");

    private static final Map<String, RealtimeErrorCode> BY_WIRE_VALUE = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(RealtimeErrorCode::wireValue, value -> value));

    private final String wireValue;

    RealtimeErrorCode(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the wire value persisted in realtime event payloads.
     *
     * @return normalized wire code
     */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Parses a realtime wire code into its typed enum representation.
     *
     * @param code wire code from event payload
     * @return matching enum value, or {@link #UNKNOWN} when absent/unrecognized
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RealtimeErrorCode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        return BY_WIRE_VALUE.getOrDefault(code.trim().toLowerCase(Locale.ROOT), UNKNOWN);
    }
}
