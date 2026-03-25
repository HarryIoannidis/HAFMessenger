package com.haf.shared.responses;

import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicKeyResponseTest {

    @Test
    void success_factory_sets_expected_fields() {
        PublicKeyResponse response = PublicKeyResponse.success("u-1", "PEM", "fp-1");

        assertTrue(response.isSuccess());
        assertEquals("u-1", response.getUserId());
        assertEquals("PEM", response.getPublicKeyPem());
        assertEquals("fp-1", response.getFingerprint());
        assertNull(response.getError());
    }

    @Test
    void error_factory_sets_error_and_marks_unsuccessful() {
        PublicKeyResponse response = PublicKeyResponse.error("not found");

        assertFalse(response.isSuccess());
        assertEquals("not found", response.getError());
        assertNull(response.getUserId());
        assertNull(response.getPublicKeyPem());
    }

    @Test
    void json_roundtrip_preserves_success_payload() {
        PublicKeyResponse response = PublicKeyResponse.success("u-1", "PEM", "fp-1");

        String json = JsonCodec.toJson(response);
        PublicKeyResponse decoded = JsonCodec.fromJson(json, PublicKeyResponse.class);

        assertTrue(decoded.isSuccess());
        assertEquals("u-1", decoded.getUserId());
        assertEquals("PEM", decoded.getPublicKeyPem());
        assertEquals("fp-1", decoded.getFingerprint());
    }
}
