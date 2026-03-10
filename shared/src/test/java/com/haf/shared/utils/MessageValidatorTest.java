package com.haf.shared.utils;

import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MessageValidatorTest {

    private EncryptedMessage valid() {
        EncryptedMessage m = new EncryptedMessage();
        m.setVersion(MessageHeader.VERSION);
        m.setSenderId("A10");
        m.setRecipientId("B10");
        m.setTimestampEpochMs(System.currentTimeMillis());
        m.setTtlSeconds(Math.min(MessageHeader.MAX_TTL_SECONDS, 86400));
        m.setAlgorithm(MessageHeader.ALGO_AEAD);
        m.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
        m.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[256]));
        m.setCiphertextB64(Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8)));
        m.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
        m.setContentType("text/plain");
        m.setContentLength(1);
        m.setAadB64(Base64.getEncoder().encodeToString("authenticated metadata".getBytes(StandardCharsets.UTF_8)));
        m.setE2e(true);
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
        m.setVersion("x");
        m.setAlgorithm("X");
        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(m);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_VERSION), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_ALGO), "Errors: " + errs);
    }

    @Test
    void bad_ids_and_ttl_reported() {
        EncryptedMessage m = valid();
        m.setSenderId("A");
        m.setRecipientId("B");
        m.setTtlSeconds(MessageHeader.MAX_TTL_SECONDS + 1);
        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(m);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_SENDER), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_RECIPIENT), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_TTL), "Errors: " + errs);
    }

    @Test
    void bad_b64_fields_reported() {
        EncryptedMessage m = valid();
        m.setIvB64("!!");
        m.setCiphertextB64("");
        m.setTagB64("AAA=");
        m.setEphemeralPublicB64("AAA=");
        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(m);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_IV), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_CIPHERTEXT), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_TAG), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_WRAPPED_KEY), "Errors: " + errs);
    }

    @Test
    void bad_timestamp_and_length_reported() {
        EncryptedMessage m = valid();
        m.setTimestampEpochMs(0);
        m.setContentLength(-1);
        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(m);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_TIMESTAMP), "Errors: " + errs);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_CONTENT_LENGTH), "Errors: " + errs);
    }

    @Test
    void contentType_allowed_passes_validation() {
        EncryptedMessage m = valid();
        m.setContentType("image/png");
        assertTrue(MessageValidator.isValid(m), errsMsg(m));
        assertTrue(MessageValidator.validateOrCollectErrors(m).isEmpty(), errsMsg(m));
    }

    @Test
    void contentType_unknown_is_rejected() {
        EncryptedMessage m = valid();
        m.setContentType("application/x-shockwave-flash");
        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(m);
        assertTrue(errs.contains(MessageValidator.ErrorCode.BAD_CONTENT_TYPE), "Errors: " + errs);
    }

    @Test
    void aad_null_is_ok() {
        EncryptedMessage m = valid();
        m.setAadB64(null);
        assertTrue(MessageValidator.isValid(m), errsMsg(m));
        assertTrue(MessageValidator.validateOrCollectErrors(m).isEmpty(), errsMsg(m));
    }

    @Test
    void content_type_roundtrip_json_ok() {
        EncryptedMessage m = new EncryptedMessage();
        m.setVersion(MessageHeader.VERSION);
        m.setSenderId("A12");
        m.setRecipientId("B34");
        m.setTimestampEpochMs(System.currentTimeMillis());
        m.setTtlSeconds(600);
        m.setAlgorithm(MessageHeader.ALGO_AEAD);
        m.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
        m.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[256]));
        m.setCiphertextB64(Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8)));
        m.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
        m.setContentType("application/pdf");
        m.setContentLength(1);
        m.setAadB64("SHOULD_NOT_APPEAR");
        m.setE2e(true);

        String json = JsonCodec.toJson(m);
        assertFalse(json.contains("\"aadB64\""), "AAD must NOT be serialized");

        EncryptedMessage back = JsonCodec.fromJson(json, EncryptedMessage.class);

        assertEquals("application/pdf", back.getContentType(), "JSON: " + json);
        assertEquals(m.getVersion(), back.getVersion(), "JSON: " + json);
        assertEquals(m.getSenderId(), back.getSenderId(), "JSON: " + json);
        assertEquals(m.getRecipientId(), back.getRecipientId(), "JSON: " + json);
        assertEquals(m.getTimestampEpochMs(), back.getTimestampEpochMs(), "JSON: " + json);
        assertEquals(m.getAlgorithm(), back.getAlgorithm(), "JSON: " + json);

        assertEquals(MessageHeader.IV_BYTES, Base64.getDecoder().decode(back.getIvB64()).length);
        assertEquals(MessageHeader.GCM_TAG_BYTES, Base64.getDecoder().decode(back.getTagB64()).length);
    }

    @Test
    void test_validate_recipient_ok() {
        EncryptedMessage m = new EncryptedMessage();
        m.setRecipientId("userB");
        assertDoesNotThrow(() -> MessageValidator.validateRecipientOrThrow("userB", m));
    }

    @Test
    void test_validate_recipient_mismatch() {
        EncryptedMessage m = new EncryptedMessage();
        m.setRecipientId("userB");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MessageValidator.validateRecipientOrThrow("userC", m));
        assertTrue(ex.getMessage().toLowerCase().contains("mismatch"));
    }

    @Test
    void test_validate_recipient_invalid() {
        EncryptedMessage m = new EncryptedMessage();
        m.setRecipientId("ab"); // below minimum
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> MessageValidator.validateRecipientOrThrow("userB", m));
        assertTrue(ex.getMessage().toLowerCase().contains("invalid"));
    }

}
