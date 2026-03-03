package com.haf.shared.dto;

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
        original.ciphertextB64 = "abc123CipherText==";
        original.ivB64 = "ivBase64==";
        original.tagB64 = "tagBase64==";
        original.ephemeralPublicB64 = "ephemeralPubKey==";
        original.contentType = "image/jpeg";
        original.originalSize = 512000L;

        String json = JsonCodec.toJson(original);
        EncryptedFileDTO parsed = JsonCodec.fromJson(json, EncryptedFileDTO.class);

        assertEquals(original.ciphertextB64, parsed.ciphertextB64);
        assertEquals(original.ivB64, parsed.ivB64);
        assertEquals(original.tagB64, parsed.tagB64);
        assertEquals(original.ephemeralPublicB64, parsed.ephemeralPublicB64);
        assertEquals(original.contentType, parsed.contentType);
        assertEquals(original.originalSize, parsed.originalSize);
    }

    @Test
    void null_fields_serialize_to_null_in_json() {
        EncryptedFileDTO dto = new EncryptedFileDTO();
        // Leave all fields null

        String json = JsonCodec.toJson(dto);

        // Deserialized back, all fields should still be null
        EncryptedFileDTO parsed = JsonCodec.fromJson(json, EncryptedFileDTO.class);
        assertNull(parsed.ciphertextB64);
        assertNull(parsed.ivB64);
        assertNull(parsed.tagB64);
        assertNull(parsed.ephemeralPublicB64);
        assertNull(parsed.contentType);
        assertEquals(0L, parsed.originalSize);
    }

    @Test
    void register_request_embeds_id_and_selfie_photos() {
        RegisterRequest req = new RegisterRequest();
        req.idPhoto = new EncryptedFileDTO();
        req.idPhoto.contentType = "image/jpeg";
        req.idPhoto.ciphertextB64 = "ct1==";

        req.selfiePhoto = new EncryptedFileDTO();
        req.selfiePhoto.contentType = "image/png";
        req.selfiePhoto.ciphertextB64 = "ct2==";

        // Round-trip through JSON (simulates client → server transport)
        String json = JsonCodec.toJson(req);
        RegisterRequest parsed = JsonCodec.fromJson(json, RegisterRequest.class);

        assertNotNull(parsed.idPhoto);
        assertEquals("ct1==", parsed.idPhoto.ciphertextB64);
        assertEquals("image/jpeg", parsed.idPhoto.contentType);

        assertNotNull(parsed.selfiePhoto);
        assertEquals("ct2==", parsed.selfiePhoto.ciphertextB64);
        assertEquals("image/png", parsed.selfiePhoto.contentType);
    }
}
