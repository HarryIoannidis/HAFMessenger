package com.haf.shared.keystore;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KeystoreSealingTest {

    @Test
    void seal_then_open_roundtrip() throws Exception {
        char[] pass = "secret-pass".toCharArray();
        byte[] plain = "TEST-PRIVATE-PEM".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        byte[] env = KeystoreSealing.sealWithPass(pass, plain);
        byte[] out = KeystoreSealing.openWithPass(pass, env);

        assertArrayEquals(plain, out);
    }

    @Test
    void wrong_pass_fails() throws Exception {
        char[] pass1 = "secret-pass".toCharArray();
        char[] pass2 = "wrong-pass".toCharArray();
        byte[] plain = "TEST-PRIVATE-PEM".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        byte[] env = KeystoreSealing.sealWithPass(pass1, plain);
        assertThrows(Exception.class, () -> KeystoreSealing.openWithPass(pass2, env));
    }

    @Test
    void envelope_format_is_v1_tripart() throws Exception {
        char[] pass = "secret-pass".toCharArray();
        byte[] env = KeystoreSealing.sealWithPass(pass, "X".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        String s = new String(env, java.nio.charset.StandardCharsets.US_ASCII);
        String[] parts = s.split("\\.");
        assertEquals(4, parts.length);
        assertEquals("v1", parts[0]);
        assertFalse(parts[1].isBlank());
        assertFalse(parts[2].isBlank());
        assertFalse(parts[3].isBlank());
    }

}
