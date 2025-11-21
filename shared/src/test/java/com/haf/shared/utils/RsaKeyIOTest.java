package com.haf.shared.utils;

import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import java.security.PublicKey;
import static org.junit.jupiter.api.Assertions.*;

class RsaKeyIOTest {

    @Test
    void generate2048_export_import_roundtrip_ok() {
        KeyPair kp = RsaKeyIO.generate(2048);

        String pubPem = RsaKeyIO.publicPem(kp.getPublic());
        String prvPem = RsaKeyIO.privatePem(kp.getPrivate());

        PublicKey pub2 = RsaKeyIO.publicFromPem(pubPem);
        assertArrayEquals(kp.getPublic().getEncoded(), pub2.getEncoded(),
                "Public key DER must be identical after PEM round-trip");

        assertNotNull(RsaKeyIO.privateFromPem(prvPem),
                "Private key must import from PEM (PKCS#8)");
    }

    @Test
    void generate3072_fingerprint_stable() {
        KeyPair kp = RsaKeyIO.generate(3072);
        byte[] der = RsaKeyIO.publicDer(kp.getPublic());

        String f1 = FingerprintUtil.sha256Hex(der);
        String f2 = FingerprintUtil.sha256Hex(der.clone());

        assertEquals(f1, f2, "Fingerprint must be stable for identical DER input");
        assertFalse(f1.isEmpty());
        assertEquals(64, f1.length(), "SHA-256 HEX length must be 64");
    }

    @Test
    void invalid_public_pem_throws() {
        String bad = "-----BEGIN PUBLIC KEY-----\nAAAA\n-----END PUBLIC KEY-----\n";
        assertThrows(IllegalArgumentException.class, () -> RsaKeyIO.publicFromPem(bad));
    }

    @Test
    void invalid_private_pem_throws() {
        String bad = "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n";
        assertThrows(IllegalArgumentException.class, () -> RsaKeyIO.privateFromPem(bad));
    }

}
