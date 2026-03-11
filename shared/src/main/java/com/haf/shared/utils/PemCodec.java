package com.haf.shared.utils;

import java.util.Base64;

public final class PemCodec {
    private PemCodec() {
    }

    /**
     * Converts a DER-encoded byte array to a PEM-encoded string.
     *
     * @param type the type of the PEM block, e.g. "PUBLIC KEY"
     * @param der  the DER-encoded byte array
     * @return the PEM-encoded string
     */
    public static String toPem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" +
                Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der) +
                "\n-----END " + type + "-----\n";
    }

    /**
     * Converts a PEM-encoded string to a DER-encoded byte array.
     *
     * @param pem the PEM-encoded string
     * @return the DER-encoded byte array
     */
    public static byte[] fromPem(String pem) {
        String b64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(b64);
    }
}
