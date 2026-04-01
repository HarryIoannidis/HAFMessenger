package com.haf.shared.constants;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageHeaderTest {

    @Test
    void version_and_algo_are_expected() {
        assertEquals("1", MessageHeader.VERSION, "VERSION mismatch");
        assertEquals("AES-256-GCM+X25519", MessageHeader.ALGO_AEAD, "ALGO_AEAD mismatch");
    }

    @Test
    void gcm_sizes_and_limits() {
        assertEquals(12, MessageHeader.IV_BYTES, "IV_BYTES must be 12");
        assertEquals(16, MessageHeader.GCM_TAG_BYTES, "GCM_TAG_BYTES must be 16");
        assertTrue(MessageHeader.MAX_CIPHERTEXT_BASE64 >= 1024 * 1024, "MAX_CIPHERTEXT_BASE64 too small");
    }

    @Test
    void id_min_lengths_and_ttl_bounds() {
        assertTrue(MessageHeader.MIN_SENDER_LEN >= 3, "MIN_SENDER_LEN too small");
        assertTrue(MessageHeader.MIN_RECIPIENT_LEN >= 3, "MIN_RECIPIENT_LEN too small");
        assertTrue(MessageHeader.MIN_TTL_SECONDS >= 60, "MIN_TTL_SECONDS too small");
        assertTrue(MessageHeader.MAX_TTL_SECONDS <= 24 * 3600, "MAX_TTL_SECONDS too large");
    }

    @Test
    void allowlist_contains_core_types() {
        assertTrue(MessageHeader.ALLOWED_CONTENT_TYPES.contains("text/plain"), "text/plain missing");
        assertTrue(MessageHeader.ALLOWED_CONTENT_TYPES.contains("application/pdf"), "application/pdf missing");
        assertTrue(MessageHeader.ALLOWED_CONTENT_TYPES.contains("image/png"), "image/png missing");
        assertTrue(MessageHeader.ALLOWED_CONTENT_TYPES.contains("application/vnd.haf.attachment-inline+json"),
                "inline attachment content-type missing");
        assertTrue(MessageHeader.ALLOWED_CONTENT_TYPES.contains("application/vnd.haf.attachment-ref+json"),
                "reference attachment content-type missing");
        assertTrue(MessageHeader.ALLOWED_CONTENT_TYPES.contains("application/octet-stream"), "octet-stream missing");
    }

}
