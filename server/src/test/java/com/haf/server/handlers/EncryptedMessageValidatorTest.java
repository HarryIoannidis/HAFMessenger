package com.haf.server.handlers;

import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedMessageValidatorTest {

    private final EncryptedMessageValidator validator = new EncryptedMessageValidator();

    @Test
    void validate_accepts_valid_message() {
        EncryptedMessage message = createValidMessage();
        EncryptedMessageValidator.ValidationResult result = validator.validate(message);

        assertTrue(result.valid());
        assertNull(result.reason());
        assertTrue(result.expiresAtMillis() > System.currentTimeMillis());
    }

    @Test
    void validate_rejects_invalid_base64() {
        EncryptedMessage message = createValidMessage();

        // Create clearly invalid base64 by taking the valid string and appending an
        // illegal character
        message.setCiphertextB64(
                Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8))
                        + "###"); // invalid characters

        EncryptedMessageValidator.ValidationResult result = validator.validate(message);

        assertFalse(result.valid());
        // Invalid base64 fails structural validation, so we get STRUCTURAL_INVALID
        assertEquals("STRUCTURAL_INVALID", result.reason());
    }

    @Test
    void validate_rejects_expired_ttl() {
        EncryptedMessage message = createValidMessage();
        // Set timestamp to 4 minutes ago (within clock skew tolerance of 5 minutes)
        // but TTL is only 2 minutes, so it's expired
        long fourMinutesAgo = System.currentTimeMillis() - Duration.ofMinutes(4).toMillis();
        message.setTimestampEpochMs(fourMinutesAgo);
        message.setTtlSeconds((int) Duration.ofMinutes(2).toSeconds()); // Expired 2 minutes ago

        EncryptedMessageValidator.ValidationResult result = validator.validate(message);

        assertFalse(result.valid());
        assertEquals("TTL_EXPIRED", result.reason());
    }

    @Test
    void validate_accepts_max_ttl() {
        EncryptedMessage message = createValidMessage();
        // Test that maximum allowed TTL (24 hours) passes validation
        // TTL_TOO_LONG check (expiresAt > 7 days) is not reachable since TTL max is 24
        // hours
        message.setTimestampEpochMs(System.currentTimeMillis());
        message.setTtlSeconds((int) MessageHeader.MAX_TTL_SECONDS); // 24 hours - max allowed

        EncryptedMessageValidator.ValidationResult result = validator.validate(message);

        // This should pass since 24 hours < 7 days server limit
        assertTrue(result.valid());
    }

    @Test
    void validate_rejects_clock_skew_too_large_future() {
        EncryptedMessage message = createValidMessage();
        message.setTimestampEpochMs(System.currentTimeMillis() + Duration.ofMinutes(6).toMillis());

        EncryptedMessageValidator.ValidationResult result = validator.validate(message);

        assertFalse(result.valid());
        assertEquals("CLOCK_SKEW", result.reason());
    }

    @Test
    void validate_rejects_clock_skew_too_large_past() {
        EncryptedMessage message = createValidMessage();
        message.setTimestampEpochMs(System.currentTimeMillis() - Duration.ofMinutes(6).toMillis());

        EncryptedMessageValidator.ValidationResult result = validator.validate(message);

        assertFalse(result.valid());
        assertEquals("CLOCK_SKEW", result.reason());
    }

    @Test
    void validate_accepts_clock_skew_within_tolerance() {
        EncryptedMessage message = createValidMessage();
        message.setTimestampEpochMs(System.currentTimeMillis() - Duration.ofMinutes(4).toMillis());

        EncryptedMessageValidator.ValidationResult result = validator.validate(message);

        assertTrue(result.valid());
    }

    @Test
    void validate_rejects_message_too_large_content_length() {
        EncryptedMessage message = createValidMessage();
        // Test that contentLength > 8MB is rejected
        message.setContentLength(9 * 1024 * 1024); // > 8MB

        EncryptedMessageValidator.ValidationResult result = validator.validate(message);

        assertFalse(result.valid());
        assertEquals("MESSAGE_TOO_LARGE", result.reason());
    }

    @Test
    void validate_rejects_structural_validation_failures() {
        EncryptedMessage message = new EncryptedMessage();
        // Missing required fields

        EncryptedMessageValidator.ValidationResult result = validator.validate(message);

        assertFalse(result.valid());
        assertEquals("STRUCTURAL_INVALID", result.reason());
    }

    private EncryptedMessage createValidMessage() {
        EncryptedMessage message = new EncryptedMessage();
        message.setVersion(MessageHeader.VERSION);
        message.setAlgorithm(MessageHeader.ALGO_AEAD);
        message.setSenderId("sender-123");
        message.setRecipientId("recipient-456");
        message.setTimestampEpochMs(System.currentTimeMillis());
        message.setTtlSeconds((int) Duration.ofDays(1).toSeconds());
        message.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
        message.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[256]));
        message.setCiphertextB64(Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8)));
        message.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
        message.setContentType("text/plain");
        message.setContentLength(4);
        message.setAadB64(Base64.getEncoder().encodeToString("aad".getBytes(StandardCharsets.UTF_8)));
        message.setE2e(true);
        return message;
    }
}
