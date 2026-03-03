package com.haf.shared.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegisterResponseTest {

    @Test
    void success_factory_sets_fields_correctly() {
        String userId = "test-uid-123";
        RegisterResponse response = RegisterResponse.success(userId);

        assertEquals(userId, response.userId);
        assertEquals("PENDING", response.status);
        assertNotNull(response.message);
        assertNull(response.error);
    }

    @Test
    void error_factory_sets_error_field_only() {
        String errMsg = "Email already exists";
        RegisterResponse response = RegisterResponse.error(errMsg);

        assertEquals(errMsg, response.error);
        assertNull(response.userId);
        assertNull(response.status);
        assertNull(response.message);
    }
}
