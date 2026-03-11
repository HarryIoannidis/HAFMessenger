package com.haf.shared.constants;

public final class CryptoConstants {
    // AES
    public static final String AES = "AES";
    public static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    public static final int AES_KEY_BITS = 256;
    public static final int GCM_TAG_BITS = 128;
    public static final int GCM_IV_BYTES = 12;
    public static final int SALT_LEN = 16;

    // X25519 Key Agreement
    public static final String XDH_ALGORITHM = "XDH";
    public static final String X25519_CURVE = "X25519";
    public static final String KEY_AGREEMENT_ALGO = "XDH";

    // HKDF (Key Derivation)
    public static final String KDF_ALGORITHM = "HKDF";
    public static final String KDF_HASH_ALGO = "SHA-256";

    // Password Hashing (Argon2id)
    public static final int ARGON2_MEMORY_KB = 65536; // 64 MB
    public static final int ARGON2_ITERATIONS = 3;
    public static final int ARGON2_PARALLELISM = 4;
    public static final int ARGON2_OUTPUT_LENGTH = 32;

    private CryptoConstants() {
    }
}
