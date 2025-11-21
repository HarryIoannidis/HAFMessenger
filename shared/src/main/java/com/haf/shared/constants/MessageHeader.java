package com.haf.shared.constants;

import java.util.Set;

public final class MessageHeader {
    // Wire protocol
    public static final String VERSION = "1";
    public static final String ALGO_AEAD = "AES-256-GCM+RSA-OAEP";

    // AEAD sizes (derived from crypto constants to avoid drift)
    public static final int IV_BYTES = CryptoConstants.GCM_IV_BYTES;
    public static final int GCM_TAG_BYTES = CryptoConstants.GCM_TAG_BITS / 8;

    // Identity policy
    public static final int MIN_SENDER_LEN = 3;
    public static final int MIN_RECIPIENT_LEN = 3;

    // TTL policy (seconds)
    public static final long MAX_TTL_SECONDS = 24 * 3600;
    public static final long MIN_TTL_SECONDS = 60;

    // Size limits
    public static final int MAX_CIPHERTEXT_BASE64 = 1024 * 1024 * 8;

    // Content types
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "text/plain",
            "text/markdown",
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "video/mp4",
            "video/webm",
            "video/ogg",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/octet-stream"
    );

    private MessageHeader() {}
}
