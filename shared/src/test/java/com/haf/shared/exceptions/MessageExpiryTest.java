package com.haf.shared.exceptions;

import com.haf.shared.crypto.MessageDecryptor;
import com.haf.shared.crypto.MessageEncryptor;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.EccKeyIO;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import static org.junit.jupiter.api.Assertions.*;

public class MessageExpiryTest {

    @Test
    public void test_expired_message_rejected() throws Exception {
        KeyPair kp = EccKeyIO.generate();

        long pastTime = 500000L;
        long currentTime = 1000000L;
        long ttlSeconds = 60; // 60 seconds TTL

        ClockProvider pastClock = new FixedClockProvider(pastTime);
        ClockProvider currentClock = new FixedClockProvider(currentTime);

        MessageEncryptor encryptor = new MessageEncryptor(kp.getPublic(), "userA", "userB", pastClock);
        byte[] payload = "secret".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage m = encryptor.encrypt(payload, "text/plain", ttlSeconds);

        assertEquals(pastTime, m.timestampEpochMs);

        MessageDecryptor decryptor = new MessageDecryptor(kp.getPrivate(), currentClock);

        MessageExpiredException ex = assertThrows(MessageExpiredException.class, () -> {
            decryptor.decryptMessage(m);
        }, "Expected expired message to be rejected");

        assertTrue(ex.getMessage().toLowerCase().contains("expired"), "Should mention expired");
    }

    @Test
    public void test_valid_message_not_expired() throws Exception {
        KeyPair kp = EccKeyIO.generate();

        long messageTime = 1000000L;
        long currentTime = 1000000L + 30000L; // 30 seconds later
        long ttlSeconds = 60; // 60 seconds TTL (message still valid)

        ClockProvider messageClock = new FixedClockProvider(messageTime);
        ClockProvider currentClock = new FixedClockProvider(currentTime);

        MessageEncryptor encryptor = new MessageEncryptor(kp.getPublic(), "userA", "userB", messageClock);
        byte[] payload = "secret".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage m = encryptor.encrypt(payload, "text/plain", ttlSeconds);

        MessageDecryptor decryptor = new MessageDecryptor(kp.getPrivate(), currentClock);

        byte[] decrypted = decryptor.decryptMessage(m);
        assertArrayEquals(payload, decrypted);
    }

    @Test
    public void test_message_at_exact_ttl_boundary() throws Exception {
        KeyPair kp = EccKeyIO.generate();

        long messageTime = 1000000L; // Epoch milliseconds when message is created
        long ttlSeconds = 60; // 60 seconds TTL
        long expiryTime = messageTime + (ttlSeconds * 1000L); // Expires at T=1060000

        ClockProvider messageClock = new FixedClockProvider(messageTime);
        MessageEncryptor encryptor = new MessageEncryptor(kp.getPublic(), "userA", "userB", messageClock);
        byte[] payload = "boundary test".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", ttlSeconds);

        ClockProvider boundaryClock = new FixedClockProvider(expiryTime);
        MessageDecryptor decryptor = new MessageDecryptor(kp.getPrivate(), boundaryClock);

        byte[] decrypted = decryptor.decryptMessage(encrypted);
        assertArrayEquals(payload, decrypted, "Message at exact TTL boundary should still be valid");

        ClockProvider expiredClock = new FixedClockProvider(expiryTime + 1L);
        MessageDecryptor expiredDecryptor = new MessageDecryptor(kp.getPrivate(), expiredClock);

        MessageExpiredException ex = assertThrows(
                MessageExpiredException.class,
                () -> expiredDecryptor.decryptMessage(encrypted),
                "Message must expire 1ms after TTL boundary");

        String msg = ex.getMessage().toLowerCase();
        assertTrue(msg.contains("expired"), "Exception message should contain 'expired'");
    }
}
