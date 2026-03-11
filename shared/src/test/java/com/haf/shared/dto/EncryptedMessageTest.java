package com.haf.shared.dto;

import com.haf.shared.utils.JsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedMessageTest {

    private static ObjectMapper strict() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    @Test
    void fields_exist_and_aad_not_in_wire() throws Exception {
        EncryptedMessage m = new EncryptedMessage();
        m.setVersion("1");
        m.setSenderId("A10");
        m.setRecipientId("B10");
        m.setAlgorithm("AES-256-GCM+X25519");
        m.setIvB64(Base64.getEncoder().encodeToString(new byte[12])); // 12B IV
        m.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[32])); // X25519 public key (32 bytes)
        m.setCiphertextB64(Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8)));
        m.setTagB64(Base64.getEncoder().encodeToString(new byte[16])); // 16B tag
        m.setTimestampEpochMs(1);
        m.setTtlSeconds(60);
        m.setContentType("text/plain");
        m.setContentLength(1);
        m.setAadB64("SHOULD_NOT_APPEAR");
        m.setE2e(true);

        String json = JsonCodec.toJson(m);

        for (String k : new String[] { "version", "senderId", "recipientId", "algorithm", "ivB64",
                "ephemeralPublicB64", "ciphertextB64", "tagB64", "timestampEpochMs",
                "ttlSeconds", "contentType", "contentLength", "e2e" }) {
            assertTrue(json.contains("\"" + k + "\""), "Missing field: " + k + " in JSON: " + json);
        }

        assertFalse(json.contains("\"aadB64\""), "AAD must NOT be serialized per policy");

        EncryptedMessage back = strict().readValue(json, EncryptedMessage.class);
        assertEquals("1", back.getVersion());
        assertEquals("A10", back.getSenderId());
        assertEquals("B10", back.getRecipientId());
        assertEquals("text/plain", back.getContentType());
        assertEquals(1, back.getContentLength());
        assertEquals(12, Base64.getDecoder().decode(back.getIvB64()).length);
        assertEquals(16, Base64.getDecoder().decode(back.getTagB64()).length);
    }

}
