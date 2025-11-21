package com.haf.shared.crypto;

import com.haf.shared.dto.EncryptedMessage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class AadCodec {
    private AadCodec() {}

    /**
     * Κατασκευάζει τα AAD bytes από metadata EncryptedMessage για AES-GCM.
     * @param m το EncryptedMessage προς επεξεργασία
     * @return τα canonical AAD bytes
     */
    public static byte[] buildAAD(EncryptedMessage m) {
        // Encode fields with length-prefix for strings
        byte[] version   = utf8(m.version);
        byte[] algo      = utf8(m.algorithm);
        byte[] sender    = utf8(m.senderId);
        byte[] recipient = utf8(m.recipientId);
        byte[] ctype     = utf8(m.contentType);

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
        bb.putLong(m.timestampEpochMs);
        bb.putLong(m.ttlSeconds);
        put(bb, ctype);
        bb.putLong(m.contentLength);
        return bb.array();
    }
    
    /**
     * Μετατρέπει String σε UTF-8 bytes.
     * @param s το String προς μετατροπή
     * @return uTF-8 bytes ή κενό array αν null
     */
    private static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Γράφει byte array στο ByteBuffer με length prefix.
     * @param bb το ByteBuffer προς εγγραφή
     * @param s το byte array προς εγγραφή
     */
    private static void put(ByteBuffer bb, byte[] s) {
        bb.putInt(s.length);
        if (s.length > 0) {
            bb.put(s);
        }
    }
}
