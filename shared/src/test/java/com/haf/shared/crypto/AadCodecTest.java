package com.haf.shared.crypto;

import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.constants.MessageHeader;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class AadCodecTest {

    private EncryptedMessage base() {
        EncryptedMessage m = new EncryptedMessage();
        m.setVersion(MessageHeader.VERSION);
        m.setAlgorithm(MessageHeader.ALGO_AEAD);
        m.setSenderId("userA");
        m.setRecipientId("userB");
        m.setTimestampEpochMs(1730544000000L); // fixed
        m.setTtlSeconds(3600);
        m.setContentType("text/plain");
        m.setContentLength(11L);
        m.setIvB64(Base64.getEncoder().encodeToString("0123456789AB".getBytes(StandardCharsets.UTF_8))); // 12B dummy
        m.setEphemeralPublicB64(Base64.getEncoder().encodeToString("k".getBytes(StandardCharsets.UTF_8)));
        return m;
    }

    @Test
    void test_deterministic_aad() {
        EncryptedMessage m1 = base();
        EncryptedMessage m2 = base();

        byte[] a1 = AadCodec.buildAAD(m1);
        byte[] a2 = AadCodec.buildAAD(m2);

        assertArrayEquals(a1, a2, "AAD must be deterministic for identical DTOs");
    }

    @Test
    void test_different_contentType_changes_aad() {
        EncryptedMessage m1 = base();
        EncryptedMessage m2 = base();
        m2.setContentType("application/octet-stream");

        byte[] a1 = AadCodec.buildAAD(m1);
        byte[] a2 = AadCodec.buildAAD(m2);

        assertFalse(Arrays.equals(a1, a2), "AAD must change when contentType changes");
    }

    @Test
    void test_different_contentLength_changes_aad() {
        EncryptedMessage m1 = base();
        EncryptedMessage m2 = base();
        m2.setContentLength(12L);

        byte[] a1 = AadCodec.buildAAD(m1);
        byte[] a2 = AadCodec.buildAAD(m2);

        assertFalse(Arrays.equals(a1, a2), "AAD must change when contentLength changes");
    }

    @Test
    void test_different_recipient_id_changes_aad() {
        EncryptedMessage m1 = base();
        EncryptedMessage m2 = base();
        m2.setRecipientId("userC");

        byte[] a1 = AadCodec.buildAAD(m1);
        byte[] a2 = AadCodec.buildAAD(m2);

        assertFalse(Arrays.equals(a1, a2), "AAD must change when recipientId changes");
    }

}
