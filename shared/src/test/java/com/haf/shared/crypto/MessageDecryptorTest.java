package com.haf.shared.crypto;

import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.EccKeyIO;
import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

public class MessageDecryptorTest {

    @Test
    public void test_decrypt_message() throws Exception {
        KeyPair keyPair = EccKeyIO.generate();

        String senderId = "userA";
        String recipientId = "userB";
        byte[] payload = "Το μυστικό μήνυμα".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";
        long ttlSeconds = 3600;

        ClockProvider clock = new FixedClockProvider(1000000L);
        MessageEncryptor encryptor = new MessageEncryptor(keyPair.getPublic(), senderId, recipientId, clock);
        EncryptedMessage encryptedMessage = encryptor.encrypt(payload, contentType, ttlSeconds);

        MessageDecryptor decryptor = new MessageDecryptor(keyPair.getPrivate(), clock);
        byte[] decryptedBytes = decryptor.decryptMessage(encryptedMessage);
        String decryptedMessage = new String(decryptedBytes, StandardCharsets.UTF_8);

        assertNotNull(decryptedBytes, "Η αποκρυπτογράφηση δεν πρέπει να επιστρέφει null");
        assertArrayEquals(payload, decryptedBytes, "Το αποκρυπτογραφημένο μήνυμα πρέπει να ταιριάζει με το αρχικό");
        assertEquals("Το μυστικό μήνυμα", decryptedMessage,
                "Το αποκρυπτογραφημένο μήνυμα πρέπει να ταιριάζει με το αρχικό");
    }

}
