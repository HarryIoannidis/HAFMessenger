package com.haf.shared.crypto;

import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.EccKeyIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import static org.junit.jupiter.api.Assertions.*;

public class AadBindingTest {
    static KeyPair kp;
    static ClockProvider clock;

    @BeforeAll
    static void keys() throws Exception {
        kp = EccKeyIO.generate();
        clock = new FixedClockProvider(1000000L);
    }

    private EncryptedMessage fresh(String msg, String sender, String recipient) throws Exception {
        MessageEncryptor enc = new MessageEncryptor(kp.getPublic(), sender, recipient, clock);
        byte[] payload = msg.getBytes(StandardCharsets.UTF_8);
        return enc.encrypt(payload, "text/plain", 3600);
    }

    private byte[] tryDecrypt(EncryptedMessage m) throws Exception {
        MessageDecryptor dec = new MessageDecryptor(kp.getPrivate(), clock);
        return dec.decryptMessage(m);
    }

    @Test
    public void test_aad_binding_version() throws Exception {
        EncryptedMessage m = fresh("hello", "userA", "userB");
        assertNotNull(tryDecrypt(m));
        m.version = "X";
        assertThrows(Exception.class, () -> tryDecrypt(m));
    }

    @Test
    public void test_aad_binding_algo() throws Exception {
        EncryptedMessage m = fresh("hello", "userA", "userB");
        m.algorithm = "OTHER";
        assertThrows(Exception.class, () -> tryDecrypt(m));
    }

    @Test
    public void test_aad_binding_sender() throws Exception {
        EncryptedMessage m = fresh("hello", "userA", "userB");
        m.senderId = "userX";
        assertThrows(Exception.class, () -> tryDecrypt(m));
    }

    @Test
    public void test_aad_binding_recipient() throws Exception {
        EncryptedMessage m = fresh("hello", "userA", "userB");
        m.recipientId = "userC";
        assertThrows(Exception.class, () -> tryDecrypt(m));
    }

    @Test
    public void test_aad_binding_timestamp() throws Exception {
        EncryptedMessage m = fresh("hello", "userA", "userB");
        m.timestampEpochMs += 1;
        assertThrows(Exception.class, () -> tryDecrypt(m));
    }

    @Test
    public void test_aad_binding_ttl() throws Exception {
        EncryptedMessage m = fresh("hello", "userA", "userB");
        m.ttlSeconds += 1;
        assertThrows(Exception.class, () -> tryDecrypt(m));
    }

    @Test
    public void test_aad_binding_content_type() throws Exception {
        EncryptedMessage m = fresh("hello", "userA", "userB");
        m.contentType = "application/octet-stream";
        assertThrows(Exception.class, () -> tryDecrypt(m));
    }

    @Test
    public void test_aad_binding_content_length() throws Exception {
        EncryptedMessage m = fresh("hello", "userA", "userB");
        m.contentLength += 1;
        assertThrows(Exception.class, () -> tryDecrypt(m));
    }

}
