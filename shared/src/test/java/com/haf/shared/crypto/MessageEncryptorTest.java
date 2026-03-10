package com.haf.shared.crypto;

import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.EccKeyIO;
import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MessageEncryptorTest {

    @Test
    void test_encrypt_message() throws Exception {
        KeyPair keyPair = EccKeyIO.generate();

        String senderId = "userA";
        String recipientId = "userB";
        byte[] payload = "The secret message".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";
        long ttlSeconds = 3600;

        ClockProvider clock = new FixedClockProvider(1000000L);
        MessageEncryptor encryptor = new MessageEncryptor(keyPair.getPublic(), senderId, recipientId, clock);

        EncryptedMessage encryptedMessage = encryptor.encrypt(payload, contentType, ttlSeconds);

        assertNotNull(encryptedMessage, "EncryptedMessage should not be null");
        assertEquals(MessageHeader.VERSION, encryptedMessage.getVersion(), "Version should match");
        assertEquals(senderId, encryptedMessage.getSenderId(), "senderId should match");
        assertEquals(recipientId, encryptedMessage.getRecipientId(), "recipientId should match");
        assertEquals(1000000L, encryptedMessage.getTimestampEpochMs(), "timestamp should match clock");
        assertEquals(ttlSeconds, encryptedMessage.getTtlSeconds(), "TTL should match");
        assertEquals(contentType, encryptedMessage.getContentType(), "contentType should match");
        assertEquals(MessageHeader.ALGO_AEAD, encryptedMessage.getAlgorithm(), "Algorithm should match");
        assertNotNull(encryptedMessage.getIvB64(), "ivB64 should not be null");
        assertNotNull(encryptedMessage.getEphemeralPublicB64(), "ephemeralPublicB64 should not be null");
        assertNotNull(encryptedMessage.getCiphertextB64(), "ciphertextB64 should not be null");
        assertEquals(payload.length, encryptedMessage.getContentLength(),
                "contentLength should match the original message length");
        assertTrue(encryptedMessage.isE2e(), "e2e flag should be true");
    }

    @Test
    void test_encrypt_rejects_null_payload() {
        KeyPair keyPair = EccKeyIO.generate();
        ClockProvider clock = new FixedClockProvider(1000000L);
        MessageEncryptor encryptor = new MessageEncryptor(keyPair.getPublic(), "sender", "recipient", clock);

        assertThrows(IllegalArgumentException.class, () -> {
            encryptor.encrypt(null, "text/plain", 3600);
        });
    }

    @Test
    void test_encrypt_rejects_invalid_ttl() {
        KeyPair keyPair = EccKeyIO.generate();
        ClockProvider clock = new FixedClockProvider(1000000L);
        MessageEncryptor encryptor = new MessageEncryptor(keyPair.getPublic(), "sender", "recipient", clock);
        byte[] payload = "test".getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> {
            encryptor.encrypt(payload, "text/plain", MessageHeader.MIN_TTL_SECONDS - 1);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            encryptor.encrypt(payload, "text/plain", MessageHeader.MAX_TTL_SECONDS + 1);
        });
    }

}
