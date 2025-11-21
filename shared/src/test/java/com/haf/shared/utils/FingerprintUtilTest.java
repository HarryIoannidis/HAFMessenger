package com.haf.shared.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FingerprintUtilTest {

    @Test
    void same_input_same_fingerprint() {
        byte[] der = new byte[]{1,2,3,4,5};
        String f1 = FingerprintUtil.sha256Hex(der);
        String f2 = FingerprintUtil.sha256Hex(der.clone());
        assertEquals(f1, f2, "Fingerprint must be stable for identical DER input");
    }

    @Test
    void different_input_different_fingerprint() {
        String f1 = FingerprintUtil.sha256Hex(new byte[]{1});
        String f2 = FingerprintUtil.sha256Hex(new byte[]{2});
        assertNotEquals(f1, f2, "Fingerprint should differ for different DER input");
    }

}
