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
    void ecc_constants_are_correct() {
        assertEquals("XDH", CryptoConstants.KEY_AGREEMENT_ALGO, "KEY_AGREEMENT_ALGO mismatch");
        assertEquals("X25519", CryptoConstants.X25519_CURVE, "X25519_CURVE mismatch");
        assertEquals("SHA-256", CryptoConstants.KDF_HASH_ALGO, "KDF_HASH_ALGO should be 'SHA-256'");
    }

}
