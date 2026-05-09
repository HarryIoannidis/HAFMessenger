package com.haf.shared.utils;

import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SigningKeyIOTest {

    @Test
    void generate_and_pem_roundtrip_succeeds() {
        KeyPair keyPair = SigningKeyIO.generate();
        String publicPem = SigningKeyIO.publicPem(keyPair.getPublic());
        String privatePem = SigningKeyIO.privatePem(keyPair.getPrivate());

        var parsedPublic = SigningKeyIO.publicFromPem(publicPem);
        var parsedPrivate = SigningKeyIO.privateFromPem(privatePem);

        assertNotNull(parsedPublic);
        assertNotNull(parsedPrivate);
        assertTrue("Ed25519".equalsIgnoreCase(parsedPublic.getAlgorithm())
                || "EdDSA".equalsIgnoreCase(parsedPublic.getAlgorithm()));
        assertTrue("Ed25519".equalsIgnoreCase(parsedPrivate.getAlgorithm())
                || "EdDSA".equalsIgnoreCase(parsedPrivate.getAlgorithm()));
        assertArrayEquals(keyPair.getPublic().getEncoded(), parsedPublic.getEncoded());
        assertArrayEquals(keyPair.getPrivate().getEncoded(), parsedPrivate.getEncoded());
    }
}
