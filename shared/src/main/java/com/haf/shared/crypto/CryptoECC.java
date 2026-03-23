package com.haf.shared.crypto;

import com.haf.shared.constants.CryptoConstants;
import com.haf.shared.exceptions.CryptoOperationException;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Utility class for Elliptic Curve Cryptography (ECC) operations.
 * Handles X25519 Key Agreement (ECDH) and Key Derivation for AES.
 */
public final class CryptoECC {
    /**
     * Prevents instantiation of this utility class.
     */
    private CryptoECC() {
    }

    /**
     * Performs Elliptic Curve Diffie-Hellman (ECDH) key agreement
     * to generate a shared secret between two parties.
     *
     * @param myPrivate   my private X25519 key
     * @param theirPublic their public X25519 key
     * @return the raw shared secret bytes
     * @throws CryptoOperationException if the agreement fails
     */
    public static byte[] generateSharedSecret(PrivateKey myPrivate, PublicKey theirPublic) {
        try {
            KeyAgreement keyAgreement = KeyAgreement.getInstance(CryptoConstants.KEY_AGREEMENT_ALGO);
            keyAgreement.init(myPrivate);
            keyAgreement.doPhase(theirPublic, true);
            return keyAgreement.generateSecret();
        } catch (Exception e) {
            throw new CryptoOperationException("Failed to generate ECDH shared secret", e);
        }
    }

    /**
     * Derives a 256-bit AES SecretKey from the raw ECDH shared secret
     * using a SHA-256 Key Derivation Function (KDF).
     *
     * @param sharedSecret the raw bytes from ECDH
     * @return a 256-bit AES SecretKey
     * @throws CryptoOperationException if derivation fails
     */
    public static SecretKeySpec deriveAesKey(byte[] sharedSecret) {
        try {
            MessageDigest hash = MessageDigest.getInstance(CryptoConstants.KDF_HASH_ALGO);
            byte[] derivedKey = hash.digest(sharedSecret);
            return new SecretKeySpec(derivedKey, CryptoConstants.AES);
        } catch (Exception e) {
            throw new CryptoOperationException("Failed to derive AES key from shared secret", e);
        }
    }

    /**
     * Convenience method to perform ECDH and immediately derive the AES session
     * key.
     *
     * @param myPrivate   my private X25519 key
     * @param theirPublic their public X25519 key
     * @return derived AES session key
     * @throws CryptoOperationException if key agreement or derivation fails
     */
    public static SecretKeySpec generateAndDeriveAesKey(PrivateKey myPrivate, PublicKey theirPublic) {
        byte[] sharedSecret = generateSharedSecret(myPrivate, theirPublic);
        return deriveAesKey(sharedSecret);
    }
}
