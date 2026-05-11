package com.haf.shared.websocket;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RealtimeErrorCodeTest {

    @Test
    void from_code_resolves_known_values_case_insensitively() {
        assertEquals(RealtimeErrorCode.STALE_RECIPIENT_KEY, RealtimeErrorCode.fromCode("stale_recipient_key"));
        assertEquals(RealtimeErrorCode.SESSION_REPLACED, RealtimeErrorCode.fromCode("SESSION_REPLACED"));
    }

    @Test
    void from_code_returns_unknown_for_null_blank_and_unrecognized() {
        assertEquals(RealtimeErrorCode.UNKNOWN, RealtimeErrorCode.fromCode(null));
        assertEquals(RealtimeErrorCode.UNKNOWN, RealtimeErrorCode.fromCode("   "));
        assertEquals(RealtimeErrorCode.UNKNOWN, RealtimeErrorCode.fromCode("new_code_from_future"));
    }

    @Test
    void wire_value_is_stable() {
        assertEquals("invalid_signature", RealtimeErrorCode.INVALID_SIGNATURE.wireValue());
        assertEquals("sender_mismatch", RealtimeErrorCode.SENDER_MISMATCH.wireValue());
    }
}
