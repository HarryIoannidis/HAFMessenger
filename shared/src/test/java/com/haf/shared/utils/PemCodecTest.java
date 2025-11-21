package com.haf.shared.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PemCodecTest {

    @Test
    void der_to_pem_roundtrip_ok() {
        byte[] der = new byte[]{0x30, 0x01, 0x02}; // dummy DER
        String pem = PemCodec.toPem("PUBLIC KEY", der);
        assertTrue(pem.contains("-----BEGIN PUBLIC KEY-----"));
        assertTrue(pem.contains("-----END PUBLIC KEY-----"));

        byte[] back = PemCodec.fromPem(pem);
        assertArrayEquals(der, back);
    }

}
