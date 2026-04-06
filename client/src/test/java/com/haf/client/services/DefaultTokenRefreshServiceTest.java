package com.haf.client.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTokenRefreshServiceTest {

    @Test
    void invalid_session_status_detection_handles_expected_http_codes() {
        assertTrue(DefaultTokenRefreshService.isInvalidSessionStatus(401));
        assertTrue(DefaultTokenRefreshService.isInvalidSessionStatus(403));
        assertFalse(DefaultTokenRefreshService.isInvalidSessionStatus(400));
        assertFalse(DefaultTokenRefreshService.isInvalidSessionStatus(500));
    }

    @Test
    void invalid_session_message_detection_handles_common_server_variants() {
        assertTrue(DefaultTokenRefreshService.isInvalidSessionMessage("invalid session"));
        assertTrue(DefaultTokenRefreshService.isInvalidSessionMessage("Session expired due to inactivity"));
        assertTrue(DefaultTokenRefreshService.isInvalidSessionMessage("refresh token expired"));
        assertTrue(DefaultTokenRefreshService.isInvalidSessionMessage("refresh token revoked"));
        assertTrue(DefaultTokenRefreshService.isInvalidSessionMessage("Unauthorized"));
        assertTrue(DefaultTokenRefreshService.isInvalidSessionMessage("Forbidden"));
    }

    @Test
    void invalid_session_message_detection_rejects_non_auth_failures() {
        assertFalse(DefaultTokenRefreshService.isInvalidSessionMessage(null));
        assertFalse(DefaultTokenRefreshService.isInvalidSessionMessage(""));
        assertFalse(DefaultTokenRefreshService.isInvalidSessionMessage("internal server error"));
        assertFalse(DefaultTokenRefreshService.isInvalidSessionMessage("temporary network timeout"));
    }

    @Test
    void access_token_resolution_supports_legacy_access_token_field_name() {
        String rawBody = """
                {"accessToken":"legacy-access","refreshToken":"refresh-value","accessExpiresAtEpochSeconds":123,"refreshExpiresAtEpochSeconds":456}
                """;

        assertEquals("legacy-access", DefaultTokenRefreshService.resolveAccessToken(null, rawBody));
        assertEquals("refresh-value", DefaultTokenRefreshService.resolveRefreshToken(null, rawBody));
        assertEquals(123L, DefaultTokenRefreshService.resolveAccessExpiry(null, rawBody));
        assertEquals(456L, DefaultTokenRefreshService.resolveRefreshExpiry(null, rawBody));
    }

    @Test
    void refresh_failure_message_uses_status_fallback_when_no_error_payload_exists() {
        assertEquals(
                "token refresh failed (HTTP 500)",
                DefaultTokenRefreshService.resolveRefreshFailureMessage(null, 500, "{}"));
        assertEquals(
                "invalid session",
                DefaultTokenRefreshService.resolveRefreshFailureMessage(null, 401, "{\"error\":\"invalid session\"}"));
    }

    @Test
    void explicit_error_detection_reads_typed_and_raw_payloads() {
        assertTrue(DefaultTokenRefreshService.hasExplicitError(null, "{\"error\":\"raw error\"}"));
        assertFalse(DefaultTokenRefreshService.hasExplicitError(null, "{\"status\":\"ok\"}"));
        assertNull(DefaultTokenRefreshService.resolveAccessToken(null, "{\"status\":\"ok\"}"));
    }
}
