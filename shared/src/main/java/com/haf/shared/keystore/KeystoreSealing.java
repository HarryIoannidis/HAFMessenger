package com.haf.shared.keystore;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import com.haf.shared.constants.CryptoConstants;
import com.haf.shared.exceptions.KeystoreOperationException;

/**
 * Provides password-based sealing and unsealing for keystore material.
 */
public final class KeystoreSealing {
    private static final SecureRandom RNG = new SecureRandom();

    /**
     * Prevents instantiation of this utility class.
     */
    private KeystoreSealing() {
    }

    /**
     * Seals the given plaintext with the given password.
     *
     * @param pass      the password to use for sealing
     * @param plaintext the plaintext to seal
     * @return the sealed ciphertext
     * @throws GeneralSecurityException if key derivation or encryption fails
     */
    public static byte[] sealWithPass(char[] pass, byte[] plaintext) throws GeneralSecurityException {
        byte[] salt = new byte[CryptoConstants.SALT_LEN];
        RNG.nextBytes(salt);

        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(pass, salt, 200_000, CryptoConstants.AES_KEY_BITS);
        SecretKey tmp = skf.generateSecret(spec);
        SecretKey key = new SecretKeySpec(tmp.getEncoded(), CryptoConstants.AES);

        byte[] iv = new byte[CryptoConstants.GCM_IV_BYTES];
        RNG.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(CryptoConstants.AES_GCM_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plaintext);

        return ("v1." + b64(salt) + "." + b64(iv) + "." + b64(ct)).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * Opens the given envelope with the given password.
     *
     * @param pass     the password to use for opening
     * @param envelope the envelope to open
     * @return the plaintext
     * @throws GeneralSecurityException if key derivation or cipher setup fails
     * @throws KeystoreOperationException if envelope format is invalid or decryption/authentication fails
     */
    public static byte[] openWithPass(char[] pass, byte[] envelope) throws GeneralSecurityException, KeystoreOperationException {
        String env = new String(envelope, java.nio.charset.StandardCharsets.US_ASCII);
        String[] parts = env.split("\\.");

        if (parts.length != 4 || !"v1".equals(parts[0])) {
            throw new KeystoreOperationException("bad envelope");
        }

        byte[] salt = b64d(parts[1]);
        byte[] iv = b64d(parts[2]);
        byte[] ct = b64d(parts[3]);

        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(pass, salt, 200_000, CryptoConstants.AES_KEY_BITS);
        SecretKey tmp = skf.generateSecret(spec);
        SecretKey key = new SecretKeySpec(tmp.getEncoded(), CryptoConstants.AES);

        Cipher cipher = Cipher.getInstance(CryptoConstants.AES_GCM_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(CryptoConstants.GCM_TAG_BITS, iv));

        try {
            return cipher.doFinal(ct);
        } catch (AEADBadTagException e) {
            throw new KeystoreOperationException("Tag mismatch: incorrect passphrase or corrupted data", e);
        } catch (GeneralSecurityException e) {
            throw new KeystoreOperationException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Base64 encodes the given byte array.
     *
     * @param b the byte array
     * @return the base64 encoded string
     */
    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    /**
     * Base64 decodes the given string.
     *
     * @param s the base64 encoded string
     * @return the decoded byte array
     * @throws IllegalArgumentException if the input is not valid Base64
     */
    private static byte[] b64d(String s) {
        return Base64.getDecoder().decode(s);
    }
}
