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
        m.version = "1";
        m.senderId = "A10";
        m.recipientId = "B10";
        m.algorithm = "AES-256-GCM+RSA-OAEP";
        m.ivB64 = Base64.getEncoder().encodeToString(new byte[12]);          // 12B IV
        m.ephemeralPublicB64 = Base64.getEncoder().encodeToString(new byte[256]); // simulate RSA-2048 wrapped key
        m.ciphertextB64 = Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));
        m.tagB64 = Base64.getEncoder().encodeToString(new byte[16]);         // 16B tag
        m.timestampEpochMs = 1;
        m.ttlSeconds = 60;
        m.contentType = "text/plain";
        m.contentLength = 1;
        m.aadB64 = "SHOULD_NOT_APPEAR";
        m.e2e = true;

        String json = JsonCodec.toJson(m);

        for (String k : new String[]{"version","senderId","recipientId","algorithm","ivB64",
                "ephemeralPublicB64","ciphertextB64","tagB64","timestampEpochMs",
                "ttlSeconds","contentType","contentLength","e2e"}) {
            assertTrue(json.contains("\"" + k + "\""), "Missing field: " + k + " in JSON: " + json);
        }

        assertFalse(json.contains("\"aadB64\""), "AAD must NOT be serialized per policy");

        EncryptedMessage back = strict().readValue(json, EncryptedMessage.class);
        assertEquals("1", back.version);
        assertEquals("A10", back.senderId);
        assertEquals("B10", back.recipientId);
        assertEquals("text/plain", back.contentType);
        assertEquals(1, back.contentLength);
        assertEquals(12, Base64.getDecoder().decode(back.ivB64).length);
        assertEquals(16, Base64.getDecoder().decode(back.tagB64).length);
    }

}
