package com.haf.shared.crypto;

import com.haf.shared.utils.EccKeyIO;
import org.junit.jupiter.api.Test;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import static org.junit.jupiter.api.Assertions.*;

class CryptoECCTest {

    @Test
    void testGenerateSharedSecret() throws Exception {
        KeyPair alice = EccKeyIO.generate();
        KeyPair bob = EccKeyIO.generate();

        byte[] aliceShared = CryptoECC.generateSharedSecret(alice.getPrivate(), bob.getPublic());
        byte[] bobShared = CryptoECC.generateSharedSecret(bob.getPrivate(), alice.getPublic());

        assertNotNull(aliceShared);
        assertNotNull(bobShared);
        assertArrayEquals(aliceShared, bobShared);
    }

    @Test
    void testDeriveAesKey() throws Exception {
        KeyPair alice = EccKeyIO.generate();
        KeyPair bob = EccKeyIO.generate();

        byte[] aliceShared = CryptoECC.generateSharedSecret(alice.getPrivate(), bob.getPublic());
        byte[] bobShared = CryptoECC.generateSharedSecret(bob.getPrivate(), alice.getPublic());

        SecretKeySpec aliceKey = CryptoECC.deriveAesKey(aliceShared);
        SecretKeySpec bobKey = CryptoECC.deriveAesKey(bobShared);

        assertNotNull(aliceKey);
        assertNotNull(bobKey);
        assertEquals("AES", aliceKey.getAlgorithm());
        assertEquals("AES", bobKey.getAlgorithm());
        assertArrayEquals(aliceKey.getEncoded(), bobKey.getEncoded());
    }

    @Test
    void testGenerateAndDeriveAesKeyCombinesOperations() {
        KeyPair alice = EccKeyIO.generate();
        KeyPair bob = EccKeyIO.generate();

        SecretKeySpec aliceKey = assertDoesNotThrow(
                () -> CryptoECC.generateAndDeriveAesKey(alice.getPrivate(), bob.getPublic()));
        SecretKeySpec bobKey = assertDoesNotThrow(
                () -> CryptoECC.generateAndDeriveAesKey(bob.getPrivate(), alice.getPublic()));

        assertNotNull(aliceKey);
        assertNotNull(bobKey);
        assertEquals("AES", aliceKey.getAlgorithm());
        assertEquals("AES", bobKey.getAlgorithm());
        assertArrayEquals(aliceKey.getEncoded(), bobKey.getEncoded());
    }
}
