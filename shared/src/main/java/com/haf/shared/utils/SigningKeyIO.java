package com.haf.shared.utils;

import com.haf.shared.constants.CryptoConstants;
import com.haf.shared.exceptions.CryptoOperationException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Provides Ed25519 signing key generation and encoding helpers.
 */
public final class SigningKeyIO {

    /**
     * Prevents instantiation of utility class.
     */
    private SigningKeyIO() {
    }

    /**
     * Generates a new Ed25519 key pair.
     *
     * @return generated Ed25519 key pair
     */
    public static KeyPair generate() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(CryptoConstants.ED25519_ALGORITHM);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new CryptoOperationException("Failed to generate Ed25519 keypair", e);
        }
    }

    /**
     * Returns DER (X.509 SPKI) bytes of public key.
     *
     * @param pub public key
     * @return DER bytes
     */
    public static byte[] publicDer(PublicKey pub) {
        return pub.getEncoded();
    }

    /**
     * Returns DER (PKCS#8) bytes of private key.
     *
     * @param prv private key
     * @return DER bytes
     */
    public static byte[] privateDer(PrivateKey prv) {
        return prv.getEncoded();
    }

    /**
     * Exports Ed25519 public key in PEM format.
     *
     * @param pub public key
     * @return PEM text
     */
    public static String publicPem(PublicKey pub) {
        return PemCodec.toPem("PUBLIC KEY", publicDer(pub));
    }

    /**
     * Exports Ed25519 private key in PEM format.
     *
     * @param prv private key
     * @return PEM text
     */
    public static String privatePem(PrivateKey prv) {
        return PemCodec.toPem("PRIVATE KEY", privateDer(prv));
    }

    /**
     * Imports Ed25519 public key from PEM.
     *
     * @param pem PEM text
     * @return public key
     */
    public static PublicKey publicFromPem(String pem) {
        try {
            byte[] der = PemCodec.fromPem(pem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance(CryptoConstants.ED25519_ALGORITHM).generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Ed25519 public PEM", e);
        }
    }

    /**
     * Imports Ed25519 private key from PEM.
     *
     * @param pem PEM text
     * @return private key
     */
    public static PrivateKey privateFromPem(String pem) {
        try {
            byte[] der = PemCodec.fromPem(pem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance(CryptoConstants.ED25519_ALGORITHM).generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Ed25519 private PEM", e);
        }
    }
}

