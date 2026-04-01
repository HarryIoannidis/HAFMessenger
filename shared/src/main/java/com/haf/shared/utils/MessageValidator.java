package com.haf.shared.utils;

import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.exceptions.MessageValidationException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class MessageValidator {

    /**
     * Prevents instantiation of utility class.
     */
    private MessageValidator() {
    }

    public enum ErrorCode {
        NULL_DTO, // DTO is null
        BAD_VERSION, // Wrong protocol version
        BAD_ALGO, // Wrong algorithm declaration
        BAD_SENDER, // senderId missing or too short
        BAD_RECIPIENT, // recipientId missing or too short
        BAD_TTL, // ttlSeconds out of bounds
        BAD_IV, // ivB64 invalid or wrong length (12B)
        BAD_CIPHERTEXT, // ciphertextB64 invalid, empty, or too large
        BAD_TAG, // tagB64 invalid or wrong length (16B)
        BAD_WRAPPED_KEY, // ephemeralPublicB64 invalid or too short for X25519 ECDHKeyAgreement
        BAD_TIMESTAMP, // timestampEpochMs <= 0
        BAD_CONTENT_LENGTH, // contentLength < 0
        BAD_CONTENT_TYPE, // contentType null/empty/not allowed
        BAD_AAD // aadB64 is null or empty
    }

    /**
     * Validates EncryptedMessage and throws exception if it fails.
     *
     * @param m encryptedMessage to be validated
     * @throws MessageValidationException if the message does not pass the checks
     */
    public static void validate(EncryptedMessage m) throws MessageValidationException {
        List<ErrorCode> errs = validateOrCollectErrors(m);
        if (!errs.isEmpty()) {
            throw new MessageValidationException("validation failed", errs);
        }
    }

    /**
     * Checks if the message is to the specific recipient.
     *
     * @param localRecipientId the ID of the local recipient
     * @param m                encryptedMessage to check
     * @throws IllegalArgumentException if recipientId does not match
     */
    public static void validateRecipientOrThrow(String localRecipientId, EncryptedMessage m) {
        if (localRecipientId == null || localRecipientId.length() < MessageHeader.MIN_RECIPIENT_LEN) {
            throw new IllegalArgumentException("Local recipient identity invalid");
        }
        if (m.getRecipientId() == null || m.getRecipientId().length() < MessageHeader.MIN_RECIPIENT_LEN) {
            throw new IllegalArgumentException("Message recipientId invalid");
        }
        if (!m.getRecipientId().equals(localRecipientId)) {
            throw new IllegalArgumentException("Recipient mismatch");
        }
    }

    /**
     * Collects all validation errors without throwing exceptions.
     *
     * @param m encryptedMessage to check
     * @return list of ErrorCode of each error
     */
    public static List<ErrorCode> validateOrCollectErrors(EncryptedMessage m) {
        List<ErrorCode> errors = new ArrayList<>();

        // Basic check: DTO must not be null
        if (m == null) {
            errors.add(ErrorCode.NULL_DTO);
            return errors;
        }

        validateHeader(m, errors);
        validateIdentities(m, errors);
        validateMetadata(m, errors);
        validatePayload(m, errors);

        return errors;
    }

    /**
     * Validates protocol-level header fields.
     *
     * @param m      encrypted message to inspect
     * @param errors mutable list receiving validation errors
     */
    private static void validateHeader(EncryptedMessage m, List<ErrorCode> errors) {
        // Protocol version
        if (!MessageHeader.VERSION.equals(m.getVersion())) {
            errors.add(ErrorCode.BAD_VERSION);
        }

        // Algorithm declaration (AEAD profile)
        if (!MessageHeader.ALGO_AEAD.equals(m.getAlgorithm())) {
            errors.add(ErrorCode.BAD_ALGO);
        }
    }

    /**
     * Validates sender and recipient identity fields.
     *
     * @param m      encrypted message to inspect
     * @param errors mutable list receiving validation errors
     */
    private static void validateIdentities(EncryptedMessage m, List<ErrorCode> errors) {
        // Sender identity (minimum length)
        if (m.getSenderId() == null || m.getSenderId().length() < MessageHeader.MIN_SENDER_LEN) {
            errors.add(ErrorCode.BAD_SENDER);
        }

        // Recipient identity (minimum length)
        if (m.getRecipientId() == null || m.getRecipientId().length() < MessageHeader.MIN_RECIPIENT_LEN) {
            errors.add(ErrorCode.BAD_RECIPIENT);
        }
    }

    /**
     * Validates metadata fields such as TTL, timestamp, content metadata, and AAD.
     *
     * @param m      encrypted message to inspect
     * @param errors mutable list receiving validation errors
     */
    private static void validateMetadata(EncryptedMessage m, List<ErrorCode> errors) {
        // Time-to-live policy (TTL)
        if (m.getTtlSeconds() < MessageHeader.MIN_TTL_SECONDS || m.getTtlSeconds() > MessageHeader.MAX_TTL_SECONDS) {
            errors.add(ErrorCode.BAD_TTL);
        }

        // Timestamp – positive value
        if (m.getTimestampEpochMs() <= 0) {
            errors.add(ErrorCode.BAD_TIMESTAMP);
        }

        // Content length – non-negative
        if (m.getContentLength() < 0) {
            errors.add(ErrorCode.BAD_CONTENT_LENGTH);
        }

        // Additional Authenticated Data (AAD) – if present, must have positive length
        if (m.getAadB64() != null && m.getAadB64().isEmpty()) {
            errors.add(ErrorCode.BAD_AAD);
        }

        // Content type – accepted
        String baseCt = normalizeContentType(m.getContentType());
        if (baseCt == null || !MessageHeader.ALLOWED_CONTENT_TYPES.contains(baseCt)) {
            errors.add(ErrorCode.BAD_CONTENT_TYPE);
        }
    }

    /**
     * Validates payload cryptographic components.
     *
     * @param m      encrypted message to inspect
     * @param errors mutable list receiving validation errors
     */
    private static void validatePayload(EncryptedMessage m, List<ErrorCode> errors) {
        validateIv(m, errors);
        validateCiphertext(m, errors);
        validateTag(m, errors);
        validateEphemeralKey(m, errors);
    }

    /**
     * Validates the IV field presence and expected byte length.
     *
     * @param m      encrypted message to inspect
     * @param errors mutable list receiving validation errors
     */
    private static void validateIv(EncryptedMessage m, List<ErrorCode> errors) {
        // IV (GCM nonce) – existence and length 12 bytes
        if (m.getIvB64() == null) {
            errors.add(ErrorCode.BAD_IV);
        } else {
            byte[] iv = safeB64(m.getIvB64());
            if (iv == null || iv.length != MessageHeader.IV_BYTES) {
                errors.add(ErrorCode.BAD_IV);
            }
        }
    }

    /**
     * Validates ciphertext presence, Base64 format, and size constraints.
     *
     * @param m      encrypted message to inspect
     * @param errors mutable list receiving validation errors
     */
    private static void validateCiphertext(EncryptedMessage m, List<ErrorCode> errors) {
        // Ciphertext – existence, decoding, non-empty, below maximum threshold
        if (m.getCiphertextB64() == null) {
            errors.add(ErrorCode.BAD_CIPHERTEXT);
        } else {
            byte[] ct = safeB64(m.getCiphertextB64());
            if (ct == null || ct.length == 0 || m.getCiphertextB64().length() > MessageHeader.MAX_CIPHERTEXT_BASE64) {
                errors.add(ErrorCode.BAD_CIPHERTEXT);
            }
        }
    }

    /**
     * Validates the authentication tag presence and expected byte length.
     *
     * @param m      encrypted message to inspect
     * @param errors mutable list receiving validation errors
     */
    private static void validateTag(EncryptedMessage m, List<ErrorCode> errors) {
        // GCM tag – existence and length 16 bytes
        if (m.getTagB64() == null) {
            errors.add(ErrorCode.BAD_TAG);
        } else {
            byte[] tag = safeB64(m.getTagB64());
            if (tag == null || tag.length != MessageHeader.GCM_TAG_BYTES) {
                errors.add(ErrorCode.BAD_TAG);
            }
        }
    }

    /**
     * Validates wrapped ephemeral key presence and minimum decoded size.
     *
     * @param m      encrypted message to inspect
     * @param errors mutable list receiving validation errors
     */
    private static void validateEphemeralKey(EncryptedMessage m, List<ErrorCode> errors) {
        // Ephemeral key (X25519) – existence and minimum length (32 bytes)
        if (m.getEphemeralPublicB64() == null) {
            errors.add(ErrorCode.BAD_WRAPPED_KEY);
        } else {
            byte[] wk = safeB64(m.getEphemeralPublicB64());
            if (wk == null || wk.length < 32) { // X25519 min
                errors.add(ErrorCode.BAD_WRAPPED_KEY);
            }
        }
    }

    /**
     * Normalizes the content type string by removing parameters and converting to
     * lowercase.
     *
     * @param ct the content type string to normalize
     * @return the normalized content type string or null if the input is null or
     *         empty
     */
    private static String normalizeContentType(String ct) {
        if (ct == null) {
            return null;
        }
        int semi = ct.indexOf(';');
        String base = (semi >= 0 ? ct.substring(0, semi) : ct).trim().toLowerCase();

        return base.isEmpty() ? null : base;
    }

    /**
     * Safely decodes a Base64 string into a byte array.
     *
     * @param s the Base64 encoded string.
     * @return the decoded byte array, or an empty array if decoding fails.
     */
    private static byte[] safeB64(String s) {
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException _) {
            return new byte[0];
        }
    }

    /**
     * Checks if the EncryptedMessage is valid.
     *
     * @param m encryptedMessage to check
     * @return true if no errors, false otherwise
     */
    public static boolean isValid(EncryptedMessage m) {
        return validateOrCollectErrors(m).isEmpty();
    }

}
