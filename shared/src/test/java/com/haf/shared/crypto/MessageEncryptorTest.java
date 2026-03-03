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

public class MessageEncryptorTest {

    @Test
    public void test_encrypt_message() throws Exception {
        KeyPair keyPair = EccKeyIO.generate();

        String senderId = "userA";
        String recipientId = "userB";
        byte[] payload = "Το μυστικό μήνυμα".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";
        long ttlSeconds = 3600;

        ClockProvider clock = new FixedClockProvider(1000000L);
        MessageEncryptor encryptor = new MessageEncryptor(keyPair.getPublic(), senderId, recipientId, clock);

        EncryptedMessage encryptedMessage = encryptor.encrypt(payload, contentType, ttlSeconds);

        assertNotNull(encryptedMessage, "EncryptedMessage δεν πρέπει να είναι null");
        assertEquals(MessageHeader.VERSION, encryptedMessage.version, "Η έκδοση πρέπει να ταιριάζει");
        assertEquals(senderId, encryptedMessage.senderId, "Το senderId πρέπει να ταιριάζει");
        assertEquals(recipientId, encryptedMessage.recipientId, "Το recipientId πρέπει να ταιριάζει");
        assertEquals(1000000L, encryptedMessage.timestampEpochMs, "Το timestamp πρέπει να ταιριάζει με το clock");
        assertEquals(ttlSeconds, encryptedMessage.ttlSeconds, "Το TTL πρέπει να ταιριάζει");
        assertEquals(contentType, encryptedMessage.contentType, "Το contentType πρέπει να ταιριάζει");
        assertEquals(MessageHeader.ALGO_AEAD, encryptedMessage.algorithm, "Το αλγόριθμο πρέπει να ταιριάζει");
        assertNotNull(encryptedMessage.ivB64, "Το ivB64 δεν πρέπει να είναι null");
        assertNotNull(encryptedMessage.ephemeralPublicB64, "Το ephemeralPublicB64 δεν πρέπει να είναι null");
        assertNotNull(encryptedMessage.ciphertextB64, "Το ciphertextB64 δεν πρέπει να είναι null");
        assertEquals(payload.length, encryptedMessage.contentLength,
                "Το contentLength πρέπει να ταιριάζει με το μήκος του αρχικού μηνύματος");
        assertTrue(encryptedMessage.e2e, "Το e2e flag πρέπει να είναι true");
    }

    @Test
    public void test_encrypt_rejects_null_payload() {
        KeyPair keyPair = EccKeyIO.generate();
        ClockProvider clock = new FixedClockProvider(1000000L);
        MessageEncryptor encryptor = new MessageEncryptor(keyPair.getPublic(), "sender", "recipient", clock);

        assertThrows(IllegalArgumentException.class, () -> {
            encryptor.encrypt(null, "text/plain", 3600);
        });
    }

    @Test
    public void test_encrypt_rejects_invalid_ttl() {
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
