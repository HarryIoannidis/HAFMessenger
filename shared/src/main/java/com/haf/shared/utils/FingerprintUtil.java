package com.haf.shared.utils;

import java.security.MessageDigest;
import java.util.HexFormat;

public final class FingerprintUtil {
    private FingerprintUtil() {}

  /**
     * Calculates the SHA-256 fingerprint of a DER-encoded public key and returns it as an uppercase HEX string.
     *
     * @param derPublicKey the DER-encoded public key in byte array
     * @return sHA-256 hash of the key in uppercase HEX format
     * @throws IllegalStateException if the SHA-256 algorithm is unavailable
     */
    public static String sha256Hex(byte[] derPublicKey) {
        try {
            byte[] d = MessageDigest.getInstance(com.haf.shared.constants.CryptoConstants.OAEP_HASH).digest(derPublicKey);
            return HexFormat.of().withUpperCase().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(com.haf.shared.constants.CryptoConstants.OAEP_HASH + " not available", e);
        }
    }
}
