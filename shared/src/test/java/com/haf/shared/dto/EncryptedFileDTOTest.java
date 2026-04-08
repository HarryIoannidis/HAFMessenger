package com.haf.shared.dto;

import com.haf.shared.requests.RegisterRequest;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EncryptedFileDTOTest {

    @Test
    void fields_survive_json_round_trip() {
        EncryptedFile original = new EncryptedFile();
        original.setCiphertextB64("abc123CipherText==");
        original.setIvB64("ivBase64==");
        original.setTagB64("tagBase64==");
        original.setEphemeralPublicB64("ephemeralPubKey==");
        original.setContentType("image/jpeg");
        original.setOriginalSize(512000L);

        String json = JsonCodec.toJson(original);
        EncryptedFile parsed = JsonCodec.fromJson(json, EncryptedFile.class);

        assertEquals(original.getCiphertextB64(), parsed.getCiphertextB64());
        assertEquals(original.getIvB64(), parsed.getIvB64());
        assertEquals(original.getTagB64(), parsed.getTagB64());
        assertEquals(original.getEphemeralPublicB64(), parsed.getEphemeralPublicB64());
        assertEquals(original.getContentType(), parsed.getContentType());
        assertEquals(original.getOriginalSize(), parsed.getOriginalSize());
    }

    @Test
    void null_fields_serialize_to_null_in_json() {
        EncryptedFile dto = new EncryptedFile();
        // Leave all fields null

        String json = JsonCodec.toJson(dto);

        // Deserialized back, all fields should still be null
        EncryptedFile parsed = JsonCodec.fromJson(json, EncryptedFile.class);
        assertNull(parsed.getCiphertextB64());
        assertNull(parsed.getIvB64());
        assertNull(parsed.getTagB64());
        assertNull(parsed.getEphemeralPublicB64());
        assertNull(parsed.getContentType());
        assertEquals(0L, parsed.getOriginalSize());
    }

    @Test
    void register_request_embeds_id_and_selfie_photos() {
        RegisterRequest req = new RegisterRequest();
        EncryptedFile idPhoto = new EncryptedFile();
        idPhoto.setContentType("image/jpeg");
        idPhoto.setCiphertextB64("ct1==");
        req.setIdPhoto(idPhoto);

        EncryptedFile selfiePhoto = new EncryptedFile();
        selfiePhoto.setContentType("image/png");
        selfiePhoto.setCiphertextB64("ct2==");
        req.setSelfiePhoto(selfiePhoto);

        // Round-trip through JSON (simulates client → server transport)
        String json = JsonCodec.toJson(req);
        RegisterRequest parsed = JsonCodec.fromJson(json, RegisterRequest.class);

        assertNotNull(parsed.getIdPhoto());
        assertEquals("ct1==", parsed.getIdPhoto().getCiphertextB64());
        assertEquals("image/jpeg", parsed.getIdPhoto().getContentType());

        assertNotNull(parsed.getSelfiePhoto());
        assertEquals("ct2==", parsed.getSelfiePhoto().getCiphertextB64());
        assertEquals("image/png", parsed.getSelfiePhoto().getContentType());
    }
}
