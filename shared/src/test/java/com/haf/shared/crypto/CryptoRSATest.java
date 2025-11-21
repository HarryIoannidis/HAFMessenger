package com.haf.shared.crypto;

import com.haf.shared.constants.CryptoConstants;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import static org.junit.jupiter.api.Assertions.*;

class CryptoRSATest {

    @Test
    void test_wrap_unwrap_session_key() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(CryptoConstants.RSA_MIN_BITS);
        KeyPair keyPair = kpg.generateKeyPair();

        SecretKey sessionKey = CryptoService.generateAesKey();

        byte[] wrapped = CryptoRSA.wrapKey(sessionKey, keyPair.getPublic());
        assertNotNull(wrapped);
        assertTrue(wrapped.length > 0);

        SecretKey unwrappedKey = CryptoRSA.unwrapKey(wrapped, keyPair.getPrivate());
        assertNotNull(unwrappedKey);
        assertArrayEquals(sessionKey.getEncoded(), unwrappedKey.getEncoded(), "Τα κλειδιά πρέπει να είναι ίδια");
    }

}