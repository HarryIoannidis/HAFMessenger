package com.haf.shared.crypto;

import com.haf.shared.constants.CryptoConstants;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;

public class CryptoRSA {
    private CryptoRSA() {}

    /**
     * Encrypts (wraps) the AES key with the recipient's public RSA key.
     *
     * @param sessionKey the AES session key to be wrapped.
     * @param recipientPublicKey the recipient's public RSA key.
     * @return the bytes of the encrypted key.
     * @throws Exception if an error occurs during encryption.
     */
    public static byte[] wrapKey(SecretKey sessionKey, PublicKey recipientPublicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(CryptoConstants.RSA_OAEP_TRANSFORMATION);
        cipher.init(Cipher.WRAP_MODE, recipientPublicKey);

        return cipher.wrap(sessionKey);
    }

    /**
     * Unwinds (decrypts) the wrapped AES session key with the private RSA key.
     *
     * @param wrappedKey the bytes of the wrapped key.
     * @param recipientPrivateKey the recipient's private RSA key.
     * @return the original AES SecretKey.
     * @throws Exception if an error occurs during decryption.
     */
    public static SecretKey unwrapKey(byte[] wrappedKey, PrivateKey recipientPrivateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(CryptoConstants.RSA_OAEP_TRANSFORMATION);
        cipher.init(Cipher.UNWRAP_MODE, recipientPrivateKey);

        return (SecretKey) cipher.unwrap(wrappedKey, CryptoConstants.AES, Cipher.SECRET_KEY);
    }
}
