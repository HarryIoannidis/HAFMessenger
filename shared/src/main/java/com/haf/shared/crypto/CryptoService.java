package com.haf.shared.crypto;

import com.haf.shared.constants.CryptoConstants;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import javax.crypto.KeyGenerator;
import java.security.SecureRandom;

public final class CryptoService {
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Prevents instantiation of this utility class.
     */
    private CryptoService() {} //

    /**
     * Generates new random AES-256 key to use in a message
     *
     * @return secretKey AES 256bit
     * @throws GeneralSecurityException in case of a creation error
     */
    public static SecretKey generateAesKey() throws GeneralSecurityException {
        KeyGenerator keyGen = KeyGenerator.getInstance(CryptoConstants.AES);
        keyGen.init(CryptoConstants.AES_KEY_BITS, SecureRandom.getInstanceStrong());

        return keyGen.generateKey();
    }

    /**
     * Generates random IV 12 bytes for AES-GCM
     *
     * @return byte[] length 12 bytes
     */
    public static byte[] generateIv() {
        byte[] iv = new byte[CryptoConstants.GCM_IV_BYTES];
        secureRandom.nextBytes(iv);

        return iv;
    }

    /**
     * Encrypts plaintext with AES-256-GCM
     *
     * @param plaintext bytes to encrypt
     * @param key aES 256bit key
     * @param iv 12 bytes IV
     * @param aad authenticated additional data
     * @return encrypted bytes (ciphertext+tag)
     * @throws GeneralSecurityException if AES-GCM initialization or encryption fails
     * @throws IllegalArgumentException if {@code iv} is null or not 12 bytes
     */
    public static byte[] encryptAesGcm(byte[] plaintext, SecretKey key, byte[] iv, byte[] aad) throws GeneralSecurityException {
        if (iv == null || iv.length != CryptoConstants.GCM_IV_BYTES) {
            throw new IllegalArgumentException("IV must be " + CryptoConstants.GCM_IV_BYTES + " bytes");
        }

        Cipher cipher = Cipher.getInstance(CryptoConstants.AES_GCM_TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        if (aad != null) {
            cipher.updateAAD(aad);
        }

        return cipher.doFinal(plaintext);
    }

    /**
     * Decrypts ciphertext with AES-256-GCM
     *
     * @param ciphertext encrypted bytes (ciphertext+tag)
     * @param key aES 256bit key
     * @param iv 12 bytes IV
     * @param aad authenticated additional data (optional)
     * @return plaintext bytes
     * @throws GeneralSecurityException if AES-GCM initialization or decryption fails
     * @throws IllegalArgumentException if {@code iv} is null or not 12 bytes
     */
    public static byte[] decryptAesGcm(byte[] ciphertext, SecretKey key, byte[] iv, byte[] aad) throws GeneralSecurityException {
        if (iv == null || iv.length != CryptoConstants.GCM_IV_BYTES) {
            throw new IllegalArgumentException("IV must be " + CryptoConstants.GCM_IV_BYTES + " bytes");
        }

        Cipher cipher = Cipher.getInstance(CryptoConstants.AES_GCM_TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        if (aad != null) {
            cipher.updateAAD(aad);
        }

        return cipher.doFinal(ciphertext);
    }
}
