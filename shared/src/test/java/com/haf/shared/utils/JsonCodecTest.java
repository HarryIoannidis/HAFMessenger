package com.haf.shared.utils;

import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonCodecTest {

    @Test
    void dto_roundtrip_ok() {
        EncryptedMessage m = new EncryptedMessage();
        m.version = "1";
        m.senderId = "A10";
        m.recipientId = "B10";
        m.timestampEpochMs = System.currentTimeMillis();
        m.ttlSeconds = 86400;
        m.algorithm = "AES-256-GCM+RSA-OAEP";
        m.ivB64 = Base64.getEncoder().encodeToString(new byte[12]);
        m.ephemeralPublicB64 = Base64.getEncoder().encodeToString(new byte[256]);
        m.ciphertextB64 = Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));
        m.tagB64 = Base64.getEncoder().encodeToString(new byte[16]);
        m.contentType = "text/plain";
        m.contentLength = 1;
        m.aadB64 = Base64.getEncoder().encodeToString("header-metadata".getBytes(StandardCharsets.UTF_8)); // AAD should not be serialized
        m.e2e = true;

        String json = JsonCodec.toJson(m);
        assertFalse(json.contains("\"aadB64\""), "AAD must NOT be serialized");

        EncryptedMessage back = JsonCodec.fromJson(json, EncryptedMessage.class);

        assertEquals(m.version, back.version);
        assertEquals(m.senderId, back.senderId);
        assertEquals(m.recipientId, back.recipientId);
        assertEquals(m.algorithm, back.algorithm);
        assertEquals(m.ivB64, back.ivB64);
        assertEquals(m.ephemeralPublicB64, back.ephemeralPublicB64);
        assertEquals(m.ciphertextB64, back.ciphertextB64);
        assertEquals(m.tagB64, back.tagB64);
        assertEquals(m.contentType, back.contentType);
        assertEquals(m.contentLength, back.contentLength);
        assertEquals(m.e2e, back.e2e);

        assertEquals(12, Base64.getDecoder().decode(back.ivB64).length);
        assertEquals(16, Base64.getDecoder().decode(back.tagB64).length);
    }

    @Test
    void dto_roundtrip_then_validate_ok() {
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
        m.aadB64 = Base64.getEncoder().encodeToString("header-metadata".getBytes(StandardCharsets.UTF_8)); // AAD should not be serialized
        m.e2e = true;

        String json = JsonCodec.toJson(m);
        EncryptedMessage back = JsonCodec.fromJson(json, EncryptedMessage.class);

        List<MessageValidator.ErrorCode> errs = MessageValidator.validateOrCollectErrors(back);
        assertTrue(errs.isEmpty(), "Errors after roundtrip: " + errs + " ; JSON: " + json);
    }

    @Test
    void deserialize_fails_on_unknown_field() {
        String json = """
          {
            "version":"1",
            "senderId":"A1",
            "recipientId":"B1",
            "timestampEpochMs":1710000000000,
            "ttlSeconds":86400,
            "algo":"AES-256-GCM+RSA-OAEP",
            "ivB64":"AAAAAAAAAAAAAA==",
            "ephemeralPublicB64":"%s",
            "ciphertextB64":"%s",
            "tagB64":"AAAAAAAAAAAAAAAAAAAAAA==",
            "contentType":"text/plain",
            "contentLength":1,
            "e2e":true,
            "aadB64":"header-metadata",
            "unknownField":123
          }
        """.formatted(
                Base64.getEncoder().encodeToString(new byte[256]),
                Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8))
        );

        RuntimeException ex = assertThrows(RuntimeException.class, () -> JsonCodec.fromJson(json, EncryptedMessage.class),
                "JSON: " + json);
        assertNotNull(ex.getMessage(), "Exception has no message; JSON: " + json);
        assertFalse(ex.getMessage().isBlank(), "Empty exception message; JSON: " + json);
    }

    @Test
    void key_metadata_roundtrip_ok() {
        var meta = new com.haf.shared.dto.KeyMetadata(
                "key-2025Q4",
                "RSA-3072",
                "ABCD1234EF...FA",  // 64-hex στην πράξη
                "Primary-key-2025Q4",
                1_730_000_000L,
                "CURRENT"
        );

        String json = JsonCodec.toJson(meta);
        var back = JsonCodec.fromJson(json, com.haf.shared.dto.KeyMetadata.class);

        assertEquals(meta.keyId(), back.keyId());
        assertEquals(meta.algorithm(), back.algorithm());
        assertEquals(meta.fingerprint(), back.fingerprint());
        assertEquals(meta.label(), back.label());
        assertEquals(meta.createdAtEpochSec(), back.createdAtEpochSec());
        assertEquals(meta.status(), back.status());
    }

    @Test
    void key_metadata_fails_on_unknown_field() {
        String json = """
      {
        "keyId":"key-2025Q4",
        "algorithm":"RSA-2048",
        "fingerprint":"%s",
        "label":"Primary",
        "createdAtEpochSec":1730000000,
        "status":"CURRENT",
        "unknown": 42
      }
    """.formatted("A".repeat(64));

        assertThrows(RuntimeException.class,
                () -> JsonCodec.fromJson(json, com.haf.shared.dto.KeyMetadata.class));
    }

}