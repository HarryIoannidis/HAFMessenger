package com.haf.shared.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeyMetadataTest {

    private static ObjectMapper strict() {
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    @Test
    void jsonRoundTrip_strict_ok() throws Exception {
        KeyMetadata meta = new KeyMetadata(
                "c9b1d7a2-5d7d-4f61-8a4e-0a2b1f9e1c33",
                "RSA-3072",
                "4F8C1E3A2B...AB79",
                "Primary-2025Q4",
                1762108800L,
                "CURRENT"
        );

        String json = strict().writeValueAsString(meta);
        KeyMetadata back = strict().readValue(json, KeyMetadata.class);

        assertEquals(meta.keyId(), back.keyId());
        assertEquals(meta.algorithm(), back.algorithm());
        assertEquals(meta.fingerprint(), back.fingerprint());
        assertEquals(meta.label(), back.label());
        assertEquals(meta.createdAtEpochSec(), back.createdAtEpochSec());
        assertEquals(meta.status(), back.status());
    }

    @Test
    void rejectsUnknownFields() {
        String jsonWithUnknown = """
        {
          "keyId": "id-1",
          "algorithm": "RSA-2048",
          "fingerprint": "ABCDEF",
          "label": "L1",
          "createdAtEpochSec": 1700000000,
          "status": "CURRENT",
          "unknown": "X"
        }
        """;
        assertThrows(Exception.class,
                () -> strict().readValue(jsonWithUnknown, KeyMetadata.class));
    }

    @Test
    void validatesStatusValues() {
        for (String s : new String[]{"CURRENT","PREVIOUS","REVOKED"}) {
            KeyMetadata m = new KeyMetadata("id","RSA-2048","FF","L",1700L,s);
            assertNotNull(m);
        }
    }

}
