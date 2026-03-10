package com.haf.shared.crypto;

import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.MessageValidator;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.EccKeyIO;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class MessageFlowTest {

    @Test
    void test_happy_flow_encrypt_validate_bind_decrypt_ok() throws Exception {
        KeyPair kp = EccKeyIO.generate();

        String localRecipientId = "userB";
        String senderId = "userA";
        byte[] payload = "Μήνυμα ελέγχου".getBytes(StandardCharsets.UTF_8);

        ClockProvider clock = new FixedClockProvider(1000000L);
        MessageEncryptor enc = new MessageEncryptor(kp.getPublic(), senderId, localRecipientId, clock);
        EncryptedMessage m = enc.encrypt(payload, "text/plain", 3600);

        assertDoesNotThrow(() -> MessageValidator.validate(m), "Validator should accept message");
        assertDoesNotThrow(() -> MessageValidator.validateRecipientOrThrow(localRecipientId, m));

        MessageDecryptor dec = new MessageDecryptor(kp.getPrivate(), clock);
        byte[] pt = dec.decryptMessage(m);
        assertArrayEquals(payload, pt);
        assertEquals("Μήνυμα ελέγχου", new String(pt, StandardCharsets.UTF_8));
    }

    @Test
    void test_negative_wrong_aad_decrypt_fails() throws Exception {
        KeyPair kp = EccKeyIO.generate();

        String localRecipientId = "userB";
        String senderId = "userA";
        byte[] payload = "Μήνυμα ελέγχου".getBytes(StandardCharsets.UTF_8);

        ClockProvider clock = new FixedClockProvider(1000000L);
        MessageEncryptor enc = new MessageEncryptor(kp.getPublic(), senderId, localRecipientId, clock);
        EncryptedMessage m = enc.encrypt(payload, "text/plain", 3600);

        MessageValidator.validate(m);
        MessageValidator.validateRecipientOrThrow(localRecipientId, m);

        // Tamper AAD field used in canonical AAD
        m.setTtlSeconds(m.getTtlSeconds() + 1); // This changes the AAD, so decrypt should fail

        MessageDecryptor dec = new MessageDecryptor(kp.getPrivate(), clock);
        assertThrows(Exception.class, () -> dec.decryptMessage(m), "Decrypt must fail with tampered AAD field");
    }

}
