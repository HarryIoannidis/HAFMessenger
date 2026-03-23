package com.haf.shared.utils;

import com.haf.shared.exceptions.JsonCodecException;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.dto.KeyMetadata;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JsonCodecTest {

  @Test
  void dto_roundtrip_ok() {
    EncryptedMessage m = new EncryptedMessage();
    m.setVersion("1");
    m.setSenderId("A10");
    m.setRecipientId("B10");
    m.setTimestampEpochMs(System.currentTimeMillis());
    m.setTtlSeconds(86400);
    m.setAlgorithm("AES-256-GCM+X25519");
    m.setIvB64(Base64.getEncoder().encodeToString(new byte[12]));
    m.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[32]));
    m.setCiphertextB64(Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8)));
    m.setTagB64(Base64.getEncoder().encodeToString(new byte[16]));
    m.setContentType("text/plain");
    m.setContentLength(1);
    m.setAadB64(Base64.getEncoder().encodeToString("header-metadata".getBytes(StandardCharsets.UTF_8))); // AAD should
                                                                                                         // not
    // be serialized
    m.setE2e(true);

    String json = JsonCodec.toJson(m);
    assertFalse(json.contains("\"aadB64\""), "AAD must NOT be serialized");

    EncryptedMessage back = JsonCodec.fromJson(json, EncryptedMessage.class);

    assertEquals(m.getVersion(), back.getVersion());
    assertEquals(m.getSenderId(), back.getSenderId());
    assertEquals(m.getRecipientId(), back.getRecipientId());
    assertEquals(m.getAlgorithm(), back.getAlgorithm());
    assertEquals(m.getIvB64(), back.getIvB64());
    assertEquals(m.getEphemeralPublicB64(), back.getEphemeralPublicB64());
    assertEquals(m.getCiphertextB64(), back.getCiphertextB64());
    assertEquals(m.getTagB64(), back.getTagB64());
    assertEquals(m.getContentType(), back.getContentType());
    assertEquals(m.getContentLength(), back.getContentLength());
    assertEquals(m.isE2e(), back.isE2e());

    assertEquals(12, Base64.getDecoder().decode(back.getIvB64()).length);
    assertEquals(16, Base64.getDecoder().decode(back.getTagB64()).length);
  }

  @Test
  void dto_roundtrip_then_validate_ok() {
    EncryptedMessage m = new EncryptedMessage();
    m.setVersion(MessageHeader.VERSION);
    m.setSenderId("A12");
    m.setRecipientId("B34");
    m.setTimestampEpochMs(System.currentTimeMillis());
    m.setTtlSeconds(600);
    m.setAlgorithm(MessageHeader.ALGO_AEAD);
    m.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
    m.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[32]));
    m.setCiphertextB64(Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8)));
    m.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
    m.setContentType("application/pdf");
    m.setContentLength(1);
    m.setAadB64(Base64.getEncoder().encodeToString("header-metadata".getBytes(StandardCharsets.UTF_8))); // AAD should
                                                                                                         // not
    // be serialized
    m.setE2e(true);

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
            "algo":"AES-256-GCM+X25519",
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
        Base64.getEncoder().encodeToString(new byte[32]),
        Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8)));

    JsonCodecException ex = assertThrows(JsonCodecException.class,
        () -> JsonCodec.fromJson(json, EncryptedMessage.class),
        "JSON: " + json);
    assertNotNull(ex.getMessage(), "Exception has no message; JSON: " + json);
    assertFalse(ex.getMessage().isBlank(), "Empty exception message; JSON: " + json);
  }

  @Test
  void key_metadata_roundtrip_ok() {
    var meta = new KeyMetadata(
        "key-2025Q4",
        "X25519",
        "ABCD1234EF...FA", // 64-hex in practice
        "Primary-key-2025Q4",
        1_730_000_000L,
        "CURRENT");

    String json = JsonCodec.toJson(meta);
    var back = JsonCodec.fromJson(json, KeyMetadata.class);

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
            "algorithm":"X25519",
            "fingerprint":"%s",
            "label":"Primary",
            "createdAtEpochSec":1730000000,
            "status":"CURRENT",
            "unknown": 42
          }
        """.formatted("A".repeat(64));

    assertThrows(JsonCodecException.class,
        () -> JsonCodec.fromJson(json, KeyMetadata.class));
  }

}
