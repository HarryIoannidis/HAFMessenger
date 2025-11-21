package com.haf.shared.crypto;

import com.haf.shared.constants.CryptoConstants;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    @Test
    void generateAesKey_produces_valid_key_and_randomness() throws GeneralSecurityException {
        Set<String> generatedKeys = new HashSet<>();

        for (int i = 0; i < 5; i++) {
            SecretKey key = CryptoService.generateAesKey();

            assertNotNull(key, "Generated key must not be null");
            assertEquals(CryptoConstants.AES, key.getAlgorithm(), "Algorithm must be AES");
            assertEquals(CryptoConstants.AES_KEY_BITS / 8, key.getEncoded().length, "Key length must be 32 bytes for AES-256");

            String encoded = java.util.Base64.getEncoder().encodeToString(key.getEncoded());
            boolean isNew = generatedKeys.add(encoded); // true αν προστέθηκε, false αν υπάρχει ήδη

            assertTrue(isNew, "Generated key must be unique (random)");
        }
    }

    @Test
    void generateIv_produces_12_bytes_and_randomness() {
        Set<String> ivs = new HashSet<>();

        for (int i = 0; i < 5; i++) {
            byte[] iv = CryptoService.generateIv();

            assertNotNull(iv, "IV δεν πρέπει να είναι null");
            assertEquals(CryptoConstants.GCM_IV_BYTES, iv.length, "Μήκος IV πρέπει να είναι 12 bytes");

            String base64 = java.util.Base64.getEncoder().encodeToString(iv);
            boolean isNew = ivs.add(base64); // true αν προστέθηκε, false αν υπάρχει ήδη

            assertTrue(isNew, "Κάθε IV πρέπει να είναι μοναδικό (random)");
        }
    }

    @Test
    void encrypt_decrypt_aes_gcm_roundtrip() throws Exception {
        SecretKey key = CryptoService.generateAesKey();
        byte[] iv = CryptoService.generateIv();
        byte[] aad = "authenticated metadata".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "This is a secret message!".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = CryptoService.encryptAesGcm(plaintext, key, iv, aad);
        assertNotNull(ciphertext);
        assertTrue(ciphertext.length > 0);

        byte[] decrypted = CryptoService.decryptAesGcm(ciphertext, key, iv, aad);
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void aead_rejects_tampered_ciphertext() throws Exception {
        SecretKey key = CryptoService.generateAesKey();
        byte[] iv = CryptoService.generateIv();
        byte[] aad = "authenticated metadata".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "Secret message".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = CryptoService.encryptAesGcm(plaintext, key, iv, aad);

        // Τροποποίησε 1 byte του ciphertext
        ciphertext[0] ^= 0x01;

        assertThrows(GeneralSecurityException.class, () -> {
            CryptoService.decryptAesGcm(ciphertext, key, iv, aad);
        }, "Η αποκρυπτογράφηση πρέπει να αποτύχει λόγω αλλοίωσης ciphertext");
    }

    @Test
    void decrypt_fails_with_wrong_key_or_iv() throws Exception {
        SecretKey key = CryptoService.generateAesKey();
        SecretKey wrongKey = CryptoService.generateAesKey();
        byte[] iv = CryptoService.generateIv();
        byte[] wrongIv = CryptoService.generateIv();
        byte[] aad = "authenticated metadata".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "Secret message".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = CryptoService.encryptAesGcm(plaintext, key, iv, aad);

        // Λάθος κλειδί
        assertThrows(GeneralSecurityException.class, () -> {
            CryptoService.decryptAesGcm(ciphertext, wrongKey, iv, aad);
        }, "Αποκρυπτογράφηση με λάθος κλειδί πρέπει να αποτύχει");

        // Λάθος iv
        assertThrows(GeneralSecurityException.class, () -> {
            CryptoService.decryptAesGcm(ciphertext, key, wrongIv, aad);
        }, "Αποκρυπτογράφηση με λάθος IV πρέπει να αποτύχει");
    }

    @Test
    void decrypt_fails_with_wrong_aad() throws Exception {
        SecretKey key = CryptoService.generateAesKey();

        byte[] iv = CryptoService.generateIv();
        byte[] aad = "authenticated metadata".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "Secret message".getBytes(StandardCharsets.UTF_8);
        byte[] wrongAad = "wrong authenticated metadata".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = CryptoService.encryptAesGcm(plaintext, key, iv, aad);

        assertThrows(GeneralSecurityException.class, () -> {
            CryptoService.decryptAesGcm(ciphertext, key, iv, wrongAad);
        }, "Αποκρυπτογράφηση με λάθος AAD πρέπει να αποτύχει");
    }

}
