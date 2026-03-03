package com.haf.shared.utils;

import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.exceptions.MessageValidationException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class MessageValidator {
    private MessageValidator() {
    }

    public enum ErrorCode {
        NULL_DTO, // DTO null
        BAD_VERSION, // λάθος έκδοση πρωτοκόλλου
        BAD_ALGO, // λάθος δήλωση αλγορίθμων
        BAD_SENDER, // senderId λείπει/πολύ μικρό
        BAD_RECIPIENT, // recipientId λείπει/πολύ μικρό
        BAD_TTL, // ttlSeconds εκτός ορίων
        BAD_IV, // ivB64 άκυρο/λάθος μήκος (12B)
        BAD_CIPHERTEXT, // ciphertextB64 άκυρο/κενό/υπερβολικά μεγάλο
        BAD_TAG, // tagB64 άκυρο/λάθος μήκος (16B)
        BAD_WRAPPED_KEY, // ephemeralPublicB64 άκυρο/μικρό για RSA-2048 OAEP
        BAD_TIMESTAMP, // timestampEpochMs <= 0
        BAD_CONTENT_LENGTH, // contentLength < 0
        BAD_CONTENT_TYPE, // contentType null/empty/not allowed
        BAD_AAD // aadB64 null/empty
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
        if (m.recipientId == null || m.recipientId.length() < MessageHeader.MIN_RECIPIENT_LEN) {
            throw new IllegalArgumentException("Message recipientId invalid");
        }
        if (!m.recipientId.equals(localRecipientId)) {
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

        // Βασικός έλεγχος: DTO δεν πρέπει να είναι null
        if (m == null) {
            errors.add(ErrorCode.NULL_DTO);
            return errors;
        }

        // Έκδοση πρωτοκόλλου
        if (!MessageHeader.VERSION.equals(m.version)) {
            errors.add(ErrorCode.BAD_VERSION);
        }

        // Δήλωση αλγορίθμων (προφίλ AEAD)
        if (!MessageHeader.ALGO_AEAD.equals(m.algorithm)) {
            errors.add(ErrorCode.BAD_ALGO);
        }

        // Ταυτότητα αποστολέα (ελάχιστο μήκος)
        if (m.senderId == null || m.senderId.length() < MessageHeader.MIN_SENDER_LEN) {
            errors.add(ErrorCode.BAD_SENDER);
        }

        // Ταυτότητα παραλήπτη (ελάχιστο μήκος)
        if (m.recipientId == null || m.recipientId.length() < MessageHeader.MIN_RECIPIENT_LEN) {
            errors.add(ErrorCode.BAD_RECIPIENT);
        }

        // Πολιτική χρόνου ζωής (TTL)
        if (m.ttlSeconds < MessageHeader.MIN_TTL_SECONDS || m.ttlSeconds > MessageHeader.MAX_TTL_SECONDS) {
            errors.add(ErrorCode.BAD_TTL);
        }

        // IV (GCM nonce) – ύπαρξη και μήκος 12 bytes
        if (m.ivB64 == null) {
            errors.add(ErrorCode.BAD_IV);
        } else {
            byte[] iv = safeB64(m.ivB64);
            if (iv == null || iv.length != MessageHeader.IV_BYTES) {
                errors.add(ErrorCode.BAD_IV);
            }
        }

        // Ciphertext – ύπαρξη, αποκωδικοποίηση, όχι κενό, κάτω από μέγιστο όριο
        if (m.ciphertextB64 == null) {
            errors.add(ErrorCode.BAD_CIPHERTEXT);
        } else {
            byte[] ct = safeB64(m.ciphertextB64);
            if (ct == null || ct.length == 0 || m.ciphertextB64.length() > MessageHeader.MAX_CIPHERTEXT_BASE64) {
                errors.add(ErrorCode.BAD_CIPHERTEXT);
            }
        }

        // GCM tag – ύπαρξη και μήκος 16 bytes
        if (m.tagB64 == null) {
            errors.add(ErrorCode.BAD_TAG);
        } else {
            byte[] tag = safeB64(m.tagB64);
            if (tag == null || tag.length != MessageHeader.GCM_TAG_BYTES) {
                errors.add(ErrorCode.BAD_TAG);
            }
        }

        // Εφήμερο κλειδί (X25519) – ύπαρξη και ελάχιστο μήκος
        if (m.ephemeralPublicB64 == null) {
            errors.add(ErrorCode.BAD_WRAPPED_KEY);
        } else {
            byte[] wk = safeB64(m.ephemeralPublicB64);
            if (wk == null || wk.length < 32) { // X25519 min
                errors.add(ErrorCode.BAD_WRAPPED_KEY);
            }
        }

        // Χρονοσήμανση – θετική τιμή
        if (m.timestampEpochMs <= 0) {
            errors.add(ErrorCode.BAD_TIMESTAMP);
        }

        // Μήκος αρχικού περιεχομένου – μη αρνητικό
        if (m.contentLength < 0) {
            errors.add(ErrorCode.BAD_CONTENT_LENGTH);
        }

        // Αυθεντικοποιητικά πρόσθετα δεδομένα (AAD) – αν υπάρχουν, πρέπει να είναι
        // θετικά μήκη
        if (m.aadB64 != null && m.aadB64.isEmpty()) {
            errors.add(ErrorCode.BAD_AAD);
        }

        // Τύπος περιεχομένου – αποδεκτός
        String baseCt = normalizeContentType(m.contentType);
        if (baseCt == null || !MessageHeader.ALLOWED_CONTENT_TYPES.contains(baseCt)) {
            errors.add(ErrorCode.BAD_CONTENT_TYPE);
        }

        return errors;
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
     * @return the decoded byte array, or null if decoding fails.
     */
    private static byte[] safeB64(String s) {
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException ex) {
            return null;
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
