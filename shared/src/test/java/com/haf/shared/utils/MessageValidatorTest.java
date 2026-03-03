package com.haf.shared.utils;

import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class MessageValidatorTest {

    private EncryptedMessage valid() {
        EncryptedMessage m = new EncryptedMessage();
        m.version = MessageHeader.VERSION;
        m.senderId = "A10";
        m.recipientId = "B10";
        m.timestampEpochMs = System.currentTimeMillis();
        m.ttlSeconds = Math.min(MessageHeader.MAX_TTL_SECONDS, 86400);
        m.algorithm = MessageHeader.ALGO_AEAD;
        m.ivB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]);
        m.ephemeralPublicB64 = Base64.getEncoder().encodeToString(new byte[256]);
        m.ciphertextB64 = Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));
        m.tagB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]);
        m.contentType = "text/plain";
        m.contentLength = 1;
        m.aadB64 = Base64.getEncoder().encodeToString("authenticated metadata".getBytes(StandardCharsets.UTF_8));
        m.e2e = true;
        return m;
    }

    private static String errsMsg(EncryptedMessage m) {
        return "Errors: " + MessageValidator.validateOrCollectErrors(m);
    }

    @Test
    void isValid_true_for_well_formed() {
        EncryptedMessage m = valid();
        assertTrue(MessageValidator.isValid(m), errsMsg(m));
        assertTrue(MessageValidator.validateOrCollectErrors(m).isEmpty(), errsMsg(m));
    }

    @Test
    void null_dto_yields_NULL_DTO() {
        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(null);
        assertEquals(1, errs.size(), "Errors: " + errs);
        assertEquals(MessageValidator.ErrorCode.NULL_DTO, errs.get(0), "Errors: " + errs);
    }

    @Test
    void bad_version_and_algo_reported() {
        EncryptedMessage m = valid();
        m.version = "x";
        m.algorithm = "X";
        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(m);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_VERSION), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_ALGO), "Errors: " + errs);
    }

    @Test
    void bad_ids_and_ttl_reported() {
        EncryptedMessage m = valid();
        m.senderId = "A";
        m.recipientId = "B";
        m.ttlSeconds = MessageHeader.MAX_TTL_SECONDS + 1;
        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(m);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_SENDER), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_RECIPIENT), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_TTL), "Errors: " + errs);
    }

    @Test
    void bad_b64_fields_reported() {
        EncryptedMessage m = valid();
        m.ivB64 = "!!";
        m.ciphertextB64 = "";
        m.tagB64 = "AAA=";
        m.ephemeralPublicB64 = "AAA=";
        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(m);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_IV), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_CIPHERTEXT), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_TAG), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_WRAPPED_KEY), "Errors: " + errs);
    }

    @Test
    void bad_timestamp_and_length_reported() {
        EncryptedMessage m = valid();
        m.timestampEpochMs = 0;
        m.contentLength = -1;
        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(m);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_TIMESTAMP), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_CONTENT_LENGTH), "Errors: " + errs);
    }

    @Test
    void contentType_allowed_passes_validation() {
        EncryptedMessage m = valid();
        m.contentType = "image/png";
        assertTrue(MessageValidator.isValid(m), errsMsg(m));
        assertTrue(MessageValidator.validateOrCollectErrors(m).isEmpty(), errsMsg(m));
    }

    @Test
    void contentType_unknown_is_rejected() {
        EncryptedMessage m = valid();
        m.contentType = "application/x-shockwave-flash";
        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(m);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_CONTENT_TYPE), "Errors: " + errs);
    }

    @Test
    void aad_null_is_ok() {
        EncryptedMessage m = valid();
        m.aadB64 = null;
        assertTrue(MessageValidator.isValid(m), errsMsg(m));
        assertTrue(MessageValidator.validateOrCollectErrors(m).isEmpty(), errsMsg(m));
    }

    @Test
    void content_type_roundtrip_json_ok() {
        EncryptedMessage m = new EncryptedMessage();
        m.version = MessageHeader.VERSION;
        m.senderId = "A12";
        m.recipientId = "B34";
        m.timestampEpochMs = System.currentTimeMillis();
        m.ttlSeconds = 600;
        m.algorithm = MessageHeader.ALGO_AEAD;
        m.ivB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]);
        m.ephemeralPublicB64 = Base64.getEncoder().encodeToString(new byte[256]);
        m.ciphertextB64 = Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));
        m.tagB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]);
        m.contentType = "application/pdf";
        m.contentLength = 1;
        m.aadB64 = "SHOULD_NOT_APPEAR";
        m.e2e = true;

        String json = JsonCodec.toJson(m);
        assertFalse(json.contains("\"aadB64\""), "AAD must NOT be serialized");

        EncryptedMessage back = JsonCodec.fromJson(json, EncryptedMessage.class);

        assertEquals("application/pdf", back.contentType, "JSON: " + json);
        assertEquals(m.version, back.version, "JSON: " + json);
        assertEquals(m.senderId, back.senderId, "JSON: " + json);
        assertEquals(m.recipientId, back.recipientId, "JSON: " + json);
        assertEquals(m.timestampEpochMs, back.timestampEpochMs, "JSON: " + json);
        assertEquals(m.algorithm, back.algorithm, "JSON: " + json);

        assertEquals(MessageHeader.IV_BYTES, Base64.getDecoder().decode(back.ivB64).length);
        assertEquals(MessageHeader.GCM_TAG_BYTES, Base64.getDecoder().decode(back.tagB64).length);
    }

    @Test
    public void test_validate_recipient_ok() {
        EncryptedMessage m = new EncryptedMessage();
        m.recipientId = "userB";
        assertDoesNotThrow(() -> MessageValidator.validateRecipientOrThrow("userB", m));
    }

    @Test
    public void test_validate_recipient_mismatch() {
        EncryptedMessage m = new EncryptedMessage();
        m.recipientId = "userB";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MessageValidator.validateRecipientOrThrow("userC", m));
        assertTrue(ex.getMessage().toLowerCase().contains("mismatch"));
    }

    @Test
    public void test_validate_recipient_invalid() {
        EncryptedMessage m = new EncryptedMessage();
        m.recipientId = "ab"; // κάτω από ελάχιστο
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MessageValidator.validateRecipientOrThrow("userB", m));
        assertTrue(ex.getMessage().toLowerCase().contains("invalid"));
    }

}
