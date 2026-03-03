package com.haf.client.network;

import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.shared.crypto.MessageEncryptor;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.exceptions.MessageExpiredException;
import com.haf.shared.exceptions.MessageValidationException;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FilePerms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class MessageReceiverTest {

    static class MockWebSocketAdapter extends WebSocketAdapter {
        private java.util.function.Consumer<String> messageConsumer;
        private boolean connected = false;

        MockWebSocketAdapter() {
            super(java.net.URI.create("ws://localhost:8080"));
        }

        @Override
        public void connect(java.util.function.Consumer<String> onMessage,
                java.util.function.Consumer<Throwable> onError) throws java.io.IOException {
            this.messageConsumer = onMessage;
            connected = true;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void close() {
            connected = false;
        }

        void simulateIncomingMessage(String json) {
            if (messageConsumer != null) {
                messageConsumer.accept(json);
            }
        }
    }

    Path tmpRoot;
    char[] passphrase = "test-pass".toCharArray();

    KeyPair senderKp;
    KeyPair recipientKp;

    String senderKeyId;
    String recipientKeyId;

    UserKeystoreKeyProvider keyProvider;

    ClockProvider clockProvider;

    MockWebSocketAdapter webSocketAdapter;
    MessageReceiver messageReceiver;
    List<byte[]> receivedMessages = new ArrayList<>();
    List<Throwable> receivedErrors = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        tmpRoot = Files.createTempDirectory("haf-test-receiver");
        FilePerms.ensureDir700(tmpRoot);

        // Create sender and recipient keys
        UserKeystore keyStore = new UserKeystore(tmpRoot);
        senderKeyId = "key-sender-001";
        recipientKeyId = "key-recipient-001";

        senderKp = EccKeyIO.generate();
        recipientKp = EccKeyIO.generate();

        keyStore.saveKeypair(senderKeyId, senderKp, passphrase);
        keyStore.saveKeypair(recipientKeyId, recipientKp, passphrase);

        keyProvider = new UserKeystoreKeyProvider(tmpRoot, passphrase);
        clockProvider = new FixedClockProvider(1000000L);
        webSocketAdapter = new MockWebSocketAdapter();
        messageReceiver = new DefaultMessageReceiver(keyProvider, clockProvider, webSocketAdapter, recipientKeyId);

        messageReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType) {
                receivedMessages.add(plaintext);
            }

            @Override
            public void onError(Throwable error) {
                receivedErrors.add(error);
            }
        });
    }

    @AfterEach
    void cleanup() throws Exception {
        if (tmpRoot != null) {
            try (var w = Files.walk(tmpRoot)) {
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test
    void receive_and_decrypt_message() throws Exception {
        messageReceiver.start();

        // Create encrypted message
        byte[] payload = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(), senderKeyId, recipientKeyId, clockProvider);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", 3600);

        // Send via WebSocket
        String json = JsonCodec.toJson(encrypted);
        webSocketAdapter.simulateIncomingMessage(json);

        // Wait a bit for processing
        Thread.sleep(100);

        // Verify message was received and decrypted
        assertEquals(1, receivedMessages.size());
        assertArrayEquals(payload, receivedMessages.get(0));
        assertEquals(0, receivedErrors.size());
    }

    @Test
    void reject_expired_message() throws Exception {
        messageReceiver.start();

        // Create message with past timestamp (expired)
        ClockProvider pastClock = new FixedClockProvider(500000L); // Past time
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(), senderKeyId, recipientKeyId, pastClock);
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", 60); // 60 second TTL

        // Current time (1000000) is after message time (500000) + TTL (60s = 60000ms)
        // So message is expired

        String json = JsonCodec.toJson(encrypted);
        webSocketAdapter.simulateIncomingMessage(json);

        Thread.sleep(100);

        // Verify error was received
        assertEquals(0, receivedMessages.size());
        assertEquals(1, receivedErrors.size());
        assertTrue(receivedErrors.get(0) instanceof MessageExpiredException);
    }

    @Test
    void reject_message_for_wrong_recipient() throws Exception {
        messageReceiver.start();

        // Create message for different recipient
        String otherRecipientId = "other-recipient";
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(), senderKeyId, otherRecipientId, clockProvider);
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", 3600);

        String json = JsonCodec.toJson(encrypted);
        webSocketAdapter.simulateIncomingMessage(json);

        Thread.sleep(100);

        // Verify error was received (recipient mismatch)
        assertEquals(0, receivedMessages.size());
        assertEquals(1, receivedErrors.size());
        assertTrue(receivedErrors.get(0) instanceof MessageValidationException);
    }
}
