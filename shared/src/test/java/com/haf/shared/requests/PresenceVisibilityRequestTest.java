package com.haf.shared.requests;

import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceVisibilityRequestTest {

    @Test
    void constructor_and_setter_getter_work() {
        PresenceVisibilityRequest request = new PresenceVisibilityRequest(true);
        assertTrue(request.isHidePresenceIndicators());

        request.setHidePresenceIndicators(false);
        assertFalse(request.isHidePresenceIndicators());
    }

    @Test
    void json_roundtrip_preserves_hide_presence_flag() {
        PresenceVisibilityRequest request = new PresenceVisibilityRequest(true);

        String json = JsonCodec.toJson(request);
        PresenceVisibilityRequest decoded = JsonCodec.fromJson(json, PresenceVisibilityRequest.class);

        assertTrue(decoded.isHidePresenceIndicators());
    }
}
