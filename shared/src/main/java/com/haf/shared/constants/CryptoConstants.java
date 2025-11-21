package com.haf.shared.constants;

public final class CryptoConstants {
    // AES
    public static final String AES = "AES";
    public static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    public static final int AES_KEY_BITS = 256;
    public static final int GCM_TAG_BITS = 128;
    public static final int GCM_IV_BYTES = 12;
    public static final int SALT_LEN = 16;

    // RSA-OAEP (SHA-256)
    public static final String RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    public static final String OAEP_MGF_ALGO = "MGF1";
    public static final String OAEP_HASH = "SHA-256";
    public static final int RSA_MIN_BITS = 2048;
    public static final int RSA_MAX_BITS = 4096;
    public static final String RSA_ALGORITHM = "RSA".concat(Integer.toString(RSA_MIN_BITS));

    private CryptoConstants() {}
}
