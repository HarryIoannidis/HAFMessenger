package com.haf.server.handlers;

import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.exceptions.MessageValidationException;
import com.haf.shared.utils.MessageValidator;
import java.time.Duration;
import java.util.Base64;

public final class EncryptedMessageValidator {

    private static final long MAX_MESSAGE_BYTES = 8L * 1024L * 1024L; // 8 MB
    private static final long MAX_CLOCK_SKEW_MS = Duration.ofMinutes(5).toMillis();
    private static final long MAX_TTL_FUTURE_MS = Duration.ofDays(7).toMillis();

    private final Base64.Decoder decoder = Base64.getDecoder();

    /**
     * Validates an EncryptedMessage.
     *
     * @param message the EncryptedMessage to validate
     * @return the ValidationResult
     */
    public ValidationResult validate(EncryptedMessage message) {
        try {
            MessageValidator.validate(message);
        } catch (MessageValidationException ex) {
            return ValidationResult.invalid("STRUCTURAL_INVALID");
        }

        long now = System.currentTimeMillis();
        long skew = Math.abs(now - message.getTimestampEpochMs());
        if (skew > MAX_CLOCK_SKEW_MS) {
            return ValidationResult.invalid("CLOCK_SKEW");
        }

        long expiresAt = message.getTimestampEpochMs() + (message.getTtlSeconds() * 1000L);
        if (expiresAt <= now) {
            return ValidationResult.invalid("TTL_EXPIRED");
        }

        if (expiresAt - now > MAX_TTL_FUTURE_MS) {
            return ValidationResult.invalid("TTL_TOO_LONG");
        }

        long ciphertextBytes = estimateBytes(message.getCiphertextB64());
        if (ciphertextBytes > MAX_MESSAGE_BYTES || message.getContentLength() > MAX_MESSAGE_BYTES) {
            return ValidationResult.invalid("MESSAGE_TOO_LARGE");
        }

        // Ensure Base64 decoding does not throw later.
        try {
            decoder.decode(message.getCiphertextB64());
        } catch (IllegalArgumentException ex) {
            return ValidationResult.invalid("INVALID_BASE64");
        }

        return ValidationResult.valid(expiresAt);
    }

    /**
     * Estimates the number of bytes in a Base64-encoded string.
     *
     * @param base64 the Base64-encoded string
     * @return the estimated number of bytes
     */
    private long estimateBytes(String base64) {
        if (base64 == null) {
            return 0;
        }
        return (base64.length() * 3L) / 4L;
    }

    /**
     * The result of validating an EncryptedMessage.
     */
    public record ValidationResult(boolean valid, String reason, long expiresAtMillis) {

        /**
         * Creates a valid ValidationResult.
         *
         * @param expiresAtMillis the expiration time of the message
         * @return the ValidationResult
         */
        public static ValidationResult valid(long expiresAtMillis) {
            return new ValidationResult(true, null, expiresAtMillis);
        }

        /**
         * Creates an invalid ValidationResult.
         *
         * @param reason the reason for the invalidation
         * @return the ValidationResult
         */
        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason, 0);
        }
    }
}
