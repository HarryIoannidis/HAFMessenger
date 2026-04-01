package com.haf.shared.crypto;

import com.haf.shared.dto.EncryptedMessage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Builds canonical Additional Authenticated Data (AAD) bytes for encrypted messages.
 */
public final class AadCodec {
    /**
     * Prevents instantiation of this utility class.
     */
    private AadCodec() {
    }

    /**
     * Constructs the AAD bytes from EncryptedMessage metadata for AES-GCM.
     *
     * @param m the EncryptedMessage to process
     * @return the canonical AAD bytes
     */
    public static byte[] buildAAD(EncryptedMessage m) {
        // Encode fields with length-prefix for strings
        byte[] version = utf8(m.getVersion());
        byte[] algo = utf8(m.getAlgorithm());
        byte[] sender = utf8(m.getSenderId());
        byte[] recipient = utf8(m.getRecipientId());
        byte[] ctype = utf8(m.getContentType());

        int total = 4 + version.length
                + 4 + algo.length
                + 4 + sender.length
                + 4 + recipient.length
                + 8 // timestampEpochMs
                + 8 // ttlSeconds
                + 4 + ctype.length
                + 8; // contentLength

        ByteBuffer bb = ByteBuffer.allocate(total);
        put(bb, version);
        put(bb, algo);
        put(bb, sender);
        put(bb, recipient);
        bb.putLong(m.getTimestampEpochMs());
        bb.putLong(m.getTtlSeconds());
        put(bb, ctype);
        bb.putLong(m.getContentLength());
        return bb.array();
    }

    /**
     * Converts String to UTF-8 bytes.
     *
     * @param s the String to convert
     * @return UTF-8 bytes or empty array if null
     */
    private static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Writes a byte array to the ByteBuffer with a length prefix.
     *
     * @param bb the ByteBuffer to write to
     * @param s  the byte array to write
     */
    private static void put(ByteBuffer bb, byte[] s) {
        bb.putInt(s.length);
        if (s.length > 0) {
            bb.put(s);
        }
    }
}
