package com.haf.server.router;

import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class QueuedEnvelopeTest {

    @Test
    void queued_envelope_creates_with_all_fields() {
        EncryptedMessage message = createValidMessage();
        long createdAt = System.currentTimeMillis();
        long expiresAt = createdAt + 3600000;

        QueuedEnvelope envelope = new QueuedEnvelope("envelope-1", message, createdAt, expiresAt);

        assertEquals("envelope-1", envelope.envelopeId());
        assertEquals(message, envelope.payload());
        assertEquals(createdAt, envelope.createdAtEpochMs());
        assertEquals(expiresAt, envelope.expiresAtEpochMs());
    }

    @Test
    void queued_envelope_is_immutable_record() {
        EncryptedMessage message = createValidMessage();
        QueuedEnvelope envelope = new QueuedEnvelope("id-1", message, 1000L, 2000L);

        // Records are immutable - verify all fields are accessible
        assertNotNull(envelope.envelopeId());
        assertNotNull(envelope.payload());
        assertTrue(envelope.createdAtEpochMs() > 0);
        assertTrue(envelope.expiresAtEpochMs() > envelope.createdAtEpochMs());
    }

    @Test
    void queued_envelope_equals_and_hashcode() {
        EncryptedMessage message1 = createValidMessage();
        EncryptedMessage message2 = createValidMessage();
        message2.senderId = message1.senderId; // Make them equal

        QueuedEnvelope envelope1 = new QueuedEnvelope("id-1", message1, 1000L, 2000L);
        QueuedEnvelope envelope2 = new QueuedEnvelope("id-1", message1, 1000L, 2000L);
        QueuedEnvelope envelope3 = new QueuedEnvelope("id-2", message1, 1000L, 2000L);

        assertEquals(envelope1, envelope2);
        assertNotEquals(envelope1, envelope3);
        assertEquals(envelope1.hashCode(), envelope2.hashCode());
    }

    @Test
    void queued_envelope_toString_includes_all_fields() {
        EncryptedMessage message = createValidMessage();
        QueuedEnvelope envelope = new QueuedEnvelope("id-1", message, 1000L, 2000L);

        String toString = envelope.toString();
        assertTrue(toString.contains("id-1"));
        assertTrue(toString.contains("1000"));
        assertTrue(toString.contains("2000"));
    }

    private EncryptedMessage createValidMessage() {
        EncryptedMessage message = new EncryptedMessage();
        message.version = MessageHeader.VERSION;
        message.algorithm = MessageHeader.ALGO_AEAD;
        message.senderId = "sender-123";
        message.recipientId = "recipient-456";
        message.timestampEpochMs = System.currentTimeMillis();
        message.ttlSeconds = (int) Duration.ofDays(1).toSeconds();
        message.ivB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]);
        message.ephemeralPublicB64 = Base64.getEncoder().encodeToString(new byte[256]);
        message.ciphertextB64 = Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8));
        message.tagB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]);
        message.contentType = "text/plain";
        message.contentLength = 4;
        message.aadB64 = Base64.getEncoder().encodeToString("aad".getBytes(StandardCharsets.UTF_8));
        message.e2e = true;
        return message;
    }
}

