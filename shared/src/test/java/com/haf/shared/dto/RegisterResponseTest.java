package com.haf.shared.dto;

import com.haf.shared.responses.RegisterResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegisterResponseTest {

    @Test
    void success_factory_sets_fields_correctly() {
        String userId = "test-uid-123";
        RegisterResponse response = RegisterResponse.success(userId);

        assertEquals(userId, response.getUserId());
        assertEquals("PENDING", response.getStatus());
        assertNotNull(response.getMessage());
        assertNull(response.getError());
    }

    @Test
    void error_factory_sets_error_field_only() {
        String errMsg = "Email already exists";
        RegisterResponse response = RegisterResponse.error(errMsg);

        assertEquals(errMsg, response.getError());
        assertNull(response.getUserId());
        assertNull(response.getStatus());
        assertNull(response.getMessage());
    }
}
