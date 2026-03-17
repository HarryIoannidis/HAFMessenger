package com.haf.shared.dto;

import com.haf.shared.requests.RegisterRequest;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EncryptedFileDTO}.
 * Verifies JSON round-trip serialization which is the primary transport use of
 * this DTO.
 */
class EncryptedFileDTOTest {

    @Test
    void fields_survive_json_round_trip() {
        EncryptedFileDTO original = new EncryptedFileDTO();
        original.setCiphertextB64("abc123CipherText==");
        original.setIvB64("ivBase64==");
        original.setTagB64("tagBase64==");
        original.setEphemeralPublicB64("ephemeralPubKey==");
        original.setContentType("image/jpeg");
        original.setOriginalSize(512000L);

        String json = JsonCodec.toJson(original);
        EncryptedFileDTO parsed = JsonCodec.fromJson(json, EncryptedFileDTO.class);

        assertEquals(original.getCiphertextB64(), parsed.getCiphertextB64());
        assertEquals(original.getIvB64(), parsed.getIvB64());
        assertEquals(original.getTagB64(), parsed.getTagB64());
        assertEquals(original.getEphemeralPublicB64(), parsed.getEphemeralPublicB64());
        assertEquals(original.getContentType(), parsed.getContentType());
        assertEquals(original.getOriginalSize(), parsed.getOriginalSize());
    }

    @Test
    void null_fields_serialize_to_null_in_json() {
        EncryptedFileDTO dto = new EncryptedFileDTO();
        // Leave all fields null

        String json = JsonCodec.toJson(dto);

        // Deserialized back, all fields should still be null
        EncryptedFileDTO parsed = JsonCodec.fromJson(json, EncryptedFileDTO.class);
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
        EncryptedFileDTO idPhoto = new EncryptedFileDTO();
        idPhoto.setContentType("image/jpeg");
        idPhoto.setCiphertextB64("ct1==");
        req.setIdPhoto(idPhoto);

        EncryptedFileDTO selfiePhoto = new EncryptedFileDTO();
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
