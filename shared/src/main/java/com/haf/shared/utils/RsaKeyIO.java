package com.haf.shared.utils;

import java.security.*;
import java.security.spec.*;

public final class RsaKeyIO {
    private RsaKeyIO() {}

    /**
     * Generates new RSA KeyPair with bits size.
     *
     * @param bits key size (2048 or 3072)
     * @return new RSA key pair
     * @throws IllegalStateException if creation fails
     */
    public static KeyPair generate(int bits) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(bits, new SecureRandom());
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
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
     * @return publicKey RSA
     * @throws IllegalArgumentException if PEM/DER is invalid
     */
    public static PublicKey publicFromPem(String pem) {
        try {
            byte[] der = PemCodec.fromPem(pem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid RSA public PEM", e);
        }
    }

    /**
     * Private key import from PEM (PKCS#8 DER).
     *
     * @param pem pEM of private key
     * @return privateKey RSA
     * @throws IllegalArgumentException if PEM/DER is invalid
     */
    public static PrivateKey privateFromPem(String pem) {
        try {
            byte[] der = PemCodec.fromPem(pem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);

            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid RSA private PEM", e);
        }
    }
}
