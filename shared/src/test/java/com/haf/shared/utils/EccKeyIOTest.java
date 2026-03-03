package com.haf.shared.utils;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

class EccKeyIOTest {

    @Test
    void testGenerateKey() {
        KeyPair keyPair = assertDoesNotThrow(EccKeyIO::generate);
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPublic());
        assertNotNull(keyPair.getPrivate());
        assertTrue("X25519".equals(keyPair.getPublic().getAlgorithm())
                || "XDH".equals(keyPair.getPublic().getAlgorithm()));
    }

    @Test
    void testPublicPemRoundtrip() {
        KeyPair kp = EccKeyIO.generate();
        PublicKey original = kp.getPublic();

        String pem = assertDoesNotThrow(() -> EccKeyIO.publicPem(original));
        assertNotNull(pem);
        assertTrue(pem.startsWith("-----BEGIN PUBLIC KEY-----"));
        assertTrue(pem.trim().endsWith("-----END PUBLIC KEY-----"));

        PublicKey parsed = assertDoesNotThrow(() -> EccKeyIO.publicFromPem(pem));
        assertNotNull(parsed);
        assertArrayEquals(original.getEncoded(), parsed.getEncoded());
    }

    @Test
    void testPrivatePemRoundtrip() {
        KeyPair kp = EccKeyIO.generate();
        PrivateKey original = kp.getPrivate();

        String pem = assertDoesNotThrow(() -> EccKeyIO.privatePem(original));
        assertNotNull(pem);
        assertTrue(pem.startsWith("-----BEGIN PRIVATE KEY-----"));
        assertTrue(pem.trim().endsWith("-----END PRIVATE KEY-----"));

        PrivateKey parsed = assertDoesNotThrow(() -> EccKeyIO.privateFromPem(pem));
        assertNotNull(parsed);
        assertArrayEquals(original.getEncoded(), parsed.getEncoded());
    }
}
