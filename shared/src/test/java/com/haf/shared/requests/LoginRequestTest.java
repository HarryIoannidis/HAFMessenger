package com.haf.shared.requests;

import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoginRequestTest {

    @Test
    void setter_getter_and_json_roundtrip_work() {
        LoginRequest request = new LoginRequest();
        request.setEmail("pilot@haf.gr");
        request.setPassword("secret");
        request.setForceTakeover(Boolean.TRUE);
        request.setTakeoverPublicKeyPem("-----BEGIN PUBLIC KEY-----\\nabc\\n-----END PUBLIC KEY-----");
        request.setTakeoverPublicKeyFingerprint("fp-123");

        assertEquals("pilot@haf.gr", request.getEmail());
        assertEquals("secret", request.getPassword());
        assertEquals(Boolean.TRUE, request.getForceTakeover());
        assertEquals("-----BEGIN PUBLIC KEY-----\\nabc\\n-----END PUBLIC KEY-----", request.getTakeoverPublicKeyPem());
        assertEquals("fp-123", request.getTakeoverPublicKeyFingerprint());

        String json = JsonCodec.toJson(request);
        LoginRequest decoded = JsonCodec.fromJson(json, LoginRequest.class);

        assertEquals("pilot@haf.gr", decoded.getEmail());
        assertEquals("secret", decoded.getPassword());
        assertEquals(Boolean.TRUE, decoded.getForceTakeover());
        assertEquals("-----BEGIN PUBLIC KEY-----\\nabc\\n-----END PUBLIC KEY-----", decoded.getTakeoverPublicKeyPem());
        assertEquals("fp-123", decoded.getTakeoverPublicKeyFingerprint());
    }
}
