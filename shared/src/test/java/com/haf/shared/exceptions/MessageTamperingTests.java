package com.haf.shared.exceptions;

import com.haf.shared.crypto.MessageDecryptor;
import com.haf.shared.crypto.MessageEncryptor;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.MessageValidator;
import com.haf.shared.utils.EccKeyIO;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MessageTamperingTests {

    @Test
    public void test_tampered_IV_fails() throws Exception {
        KeyPair kp = EccKeyIO.generate();

        ClockProvider clock = new FixedClockProvider(1000000L);
        MessageEncryptor enc = new MessageEncryptor(kp.getPublic(), "userA", "userB", clock);
        byte[] payload = "secret".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage m = enc.encrypt(payload, "text/plain", 3600);

        char last = m.ivB64.charAt(m.ivB64.length() - 1);
        char flip = (last == 'A') ? 'B' : 'A';
        m.ivB64 = m.ivB64.substring(0, m.ivB64.length() - 1) + flip; // flip last char of IV

        MessageDecryptor dec = new MessageDecryptor(kp.getPrivate(), clock);
        assertThrows(MessageTamperedException.class, () -> dec.decryptMessage(m), "Tampered IV must fail");
    }

    @Test
    public void test_wrong_aad_fails() throws Exception {
        KeyPair kp = EccKeyIO.generate();

        ClockProvider clock = new FixedClockProvider(1000000L);
        MessageEncryptor enc = new MessageEncryptor(kp.getPublic(), "userA", "userB", clock);
        byte[] payload = "secret".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage m = enc.encrypt(payload, "text/plain", 3600);

        MessageDecryptor dec = new MessageDecryptor(kp.getPrivate(), clock);
        assertNotNull(dec.decryptMessage(m));

        m.recipientId = "userC"; // tamper AAD field used in AadCodec
        assertThrows(MessageTamperedException.class, () -> dec.decryptMessage(m), "Tampered AAD must fail decrypt");
    }

    @Test
    public void test_tampered_ciphertext_fails() throws Exception {
        KeyPair kp = EccKeyIO.generate();

        ClockProvider clock = new FixedClockProvider(1000000L);
        MessageEncryptor enc = new MessageEncryptor(kp.getPublic(), "userA", "userB", clock);
        byte[] payload = "secret".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage m = enc.encrypt(payload, "text/plain", 3600);

        char last = m.ciphertextB64.charAt(m.ciphertextB64.length() - 1);
        char flip = (last == 'A') ? 'B' : 'A';
        m.ciphertextB64 = m.ciphertextB64.substring(0, m.ciphertextB64.length() - 1) + flip; // flip last char

        MessageDecryptor dec = new MessageDecryptor(kp.getPrivate(), clock);
        assertThrows(MessageTamperedException.class, () -> dec.decryptMessage(m), "Tampered ciphertext must fail");
    }

    @Test
    public void test_tampered_tag_fails() throws Exception {
        KeyPair kp = EccKeyIO.generate();
        ClockProvider clock = new FixedClockProvider(1000000L);

        MessageEncryptor enc = new MessageEncryptor(kp.getPublic(), "userA", "userB", clock);
        byte[] payload = "secret".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage m = enc.encrypt(payload, "text/plain", 3600);

        MessageDecryptor dec = new MessageDecryptor(kp.getPrivate(), clock);
        byte[] decrypted = dec.decryptMessage(m);
        assertArrayEquals(payload, decrypted, "Original message must decrypt correctly");

        char last = m.tagB64.charAt(m.tagB64.length() - 1);
        char flip = (last == 'A') ? 'B' : 'A';
        m.tagB64 = m.tagB64.substring(0, m.tagB64.length() - 1) + flip;

        MessageValidationException ex = assertThrows(
                MessageValidationException.class,
                () -> dec.decryptMessage(m),
                "Tampered AEAD tag must trigger validation failure");

        List<MessageValidator.ErrorCode> errorCodes = ex.getErrorCodes();
        assertTrue(
                errorCodes.contains(MessageValidator.ErrorCode.BAD_TAG),
                "Error codes should contain BAD_TAG");
    }

}
