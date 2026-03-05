package com.haf.shared.utils;

import com.haf.shared.exceptions.CryptoOperationException;
import java.security.*;
import java.security.spec.*;

public final class EccKeyIO {
    private EccKeyIO() {
    }

    /**
     * Generates new X25519 KeyPair.
     *
     * @return new X25519 key pair
     * @throws CryptoOperationException if creation fails
     */
    public static KeyPair generate() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("XDH");
            kpg.initialize(new NamedParameterSpec("X25519"), new SecureRandom());
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new CryptoOperationException("Failed to generate X25519 keypair", e);
        }
    }

    /**
     * Returns DER (X.509 SPKI) of the public key.
     *
     * @param pub public key
     * @return dER bytes of public key
     */
    public static byte[] publicDer(PublicKey pub) {
        return pub.getEncoded();
    }

    /**
     * Returns DER (PKCS#8) of the private key.
     *
     * @param prv private key
     * @return dER bytes of private key
     */
    public static byte[] privateDer(PrivateKey prv) {
        return prv.getEncoded();
    }

    /**
     * Export public key to PEM (BEGIN/END PUBLIC KEY).
     *
     * @param pub public key
     * @return pEM string
     */
    public static String publicPem(PublicKey pub) {
        return PemCodec.toPem("PUBLIC KEY", publicDer(pub));
    }

    /**
     * Export private key to PEM (BEGIN/END PRIVATE KEY).
     *
     * @param prv private key
     * @return pEM string
     */
    public static String privatePem(PrivateKey prv) {
        return PemCodec.toPem("PRIVATE KEY", privateDer(prv));
    }

    /**
     * Import public key from PEM (X.509 SPKI DER).
     *
     * @param pem pEM of public key
     * @return publicKey X25519
     * @throws IllegalArgumentException if PEM/DER is invalid
     */
    public static PublicKey publicFromPem(String pem) {
        try {
            byte[] der = PemCodec.fromPem(pem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance("XDH").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid X25519 public PEM", e);
        }
    }

    /**
     * Private key import from PEM (PKCS#8 DER).
     *
     * @param pem pEM of private key
     * @return privateKey X25519
     * @throws IllegalArgumentException if PEM/DER is invalid
     */
    public static PrivateKey privateFromPem(String pem) {
        try {
            byte[] der = PemCodec.fromPem(pem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);

            return KeyFactory.getInstance("XDH").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid X25519 private PEM", e);
        }
    }
}
