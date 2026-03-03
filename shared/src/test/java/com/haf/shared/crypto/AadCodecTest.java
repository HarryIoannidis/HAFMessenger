package com.haf.shared.crypto;

import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.constants.MessageHeader;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

public class AadCodecTest {

    private EncryptedMessage base() {
        EncryptedMessage m = new EncryptedMessage();
        m.version = MessageHeader.VERSION;
        m.algorithm = MessageHeader.ALGO_AEAD;
        m.senderId = "userA";
        m.recipientId = "userB";
        m.timestampEpochMs = 1730544000000L; // fixed
        m.ttlSeconds = 3600;
        m.contentType = "text/plain";
        m.contentLength = 11L;
        m.ivB64 = Base64.getEncoder().encodeToString("0123456789AB".getBytes(StandardCharsets.UTF_8)); // 12B dummy
        m.ephemeralPublicB64 = Base64.getEncoder().encodeToString("k".getBytes(StandardCharsets.UTF_8));
        return m;
    }

    @Test
    public void test_deterministic_aad() {
        EncryptedMessage m1 = base();
        EncryptedMessage m2 = base();

        byte[] a1 = AadCodec.buildAAD(m1);
        byte[] a2 = AadCodec.buildAAD(m2);

        assertArrayEquals(a1, a2, "AAD must be deterministic for identical DTOs");
    }

    @Test
    public void test_different_contentType_changes_aad() {
        EncryptedMessage m1 = base();
        EncryptedMessage m2 = base();
        m2.contentType = "application/octet-stream";

        byte[] a1 = AadCodec.buildAAD(m1);
        byte[] a2 = AadCodec.buildAAD(m2);

        assertFalse(Arrays.equals(a1, a2), "AAD must change when contentType changes");
    }

    @Test
    public void test_different_contentLength_changes_aad() {
        EncryptedMessage m1 = base();
        EncryptedMessage m2 = base();
        m2.contentLength = 12L;

        byte[] a1 = AadCodec.buildAAD(m1);
        byte[] a2 = AadCodec.buildAAD(m2);

        assertFalse(Arrays.equals(a1, a2), "AAD must change when contentLength changes");
    }

    @Test
    public void test_different_recipient_id_changes_aad() {
        EncryptedMessage m1 = base();
        EncryptedMessage m2 = base();
        m2.recipientId = "userC";

        byte[] a1 = AadCodec.buildAAD(m1);
        byte[] a2 = AadCodec.buildAAD(m2);

        assertFalse(Arrays.equals(a1, a2), "AAD must change when recipientId changes");
    }

}
