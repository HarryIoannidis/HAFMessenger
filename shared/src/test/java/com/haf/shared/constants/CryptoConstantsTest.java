package com.haf.shared.constants;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CryptoConstantsTest {

    @Test
    void aes_constants_are_correct() {
        assertEquals("AES", CryptoConstants.AES, "CryptoConstants.AES should be 'AES'");
        assertEquals("AES/GCM/NoPadding", CryptoConstants.AES_GCM_TRANSFORMATION, "AES_GCM_TRANSFORMATION mismatch");
        assertEquals(256, CryptoConstants.AES_KEY_BITS, "AES_KEY_BITS should be 256");
        assertEquals(128, CryptoConstants.GCM_TAG_BITS, "GCM_TAG_BITS should be 128");
        assertEquals(12, CryptoConstants.GCM_IV_BYTES, "GCM_IV_BYTES should be 12");
    }

    @Test
    void rsa_oaep_constants_are_correct() {
        assertEquals("RSA/ECB/OAEPWithSHA-256AndMGF1Padding", CryptoConstants.RSA_OAEP_TRANSFORMATION, "RSA_OAEP_TRANSFORMATION mismatch");
        assertEquals("MGF1", CryptoConstants.OAEP_MGF_ALGO, "OAEP_MGF_ALGO should be 'MGF1'");
        assertEquals("SHA-256", CryptoConstants.OAEP_HASH, "OAEP_HASH should be 'SHA-256'");
        assertEquals(2048, CryptoConstants.RSA_MIN_BITS, "RSA_MIN_BITS should be 2048");
        assertEquals(4096, CryptoConstants.RSA_MAX_BITS, "RSA_MAX_BITS should be 4096");
    }

}
