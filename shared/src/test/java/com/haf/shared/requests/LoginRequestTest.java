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

        assertEquals("pilot@haf.gr", request.getEmail());
        assertEquals("secret", request.getPassword());

        String json = JsonCodec.toJson(request);
        LoginRequest decoded = JsonCodec.fromJson(json, LoginRequest.class);

        assertEquals("pilot@haf.gr", decoded.getEmail());
        assertEquals("secret", decoded.getPassword());
    }
}
