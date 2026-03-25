package com.haf.client.network;

import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.shared.crypto.MessageEncryptor;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.exceptions.MessageExpiredException;
import com.haf.shared.exceptions.MessageTamperedException;
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

        private final java.util.List<String> sentMessages = new java.util.ArrayList<>();
        private boolean failNextSend;
        private int closeCalls;

        MockWebSocketAdapter() {
            super(java.net.URI.create("ws://localhost:8080"), "test-session-id");
        }

        @Override
        public void connect(java.util.function.Consumer<String> onMessage,
                java.util.function.Consumer<Throwable> onError) throws java.io.IOException {
            this.messageConsumer = onMessage;
            connected = true;
        }

        @Override
        public void sendText(String text) throws java.io.IOException {
            if (failNextSend) {
                failNextSend = false;
                throw new java.io.IOException("forced send failure");
            }
            sentMessages.add(text);
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void close() {
            connected = false;
            closeCalls++;
        }

        void simulateIncomingMessage(String json, String envelopeId) {
            String wrappedJson = "{\"type\":\"message\",\"envelopeId\":\"" + envelopeId + "\",\"payload\":" + json + "}";
            if (messageConsumer != null) {
                messageConsumer.accept(wrappedJson);
            }
        }

        void simulateIncomingRaw(String json) {
            if (messageConsumer != null) {
                messageConsumer.accept(json);
            }
        }

        java.util.List<String> getSentMessages() {
            return sentMessages;
        }

        void failNextSend() {
            failNextSend = true;
        }

        int getCloseCalls() {
            return closeCalls;
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
    List<String> presenceUpdates = new ArrayList<>();

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

        keyProvider = new UserKeystoreKeyProvider(tmpRoot, recipientKeyId, passphrase);
        clockProvider = new FixedClockProvider(1000000L);
        webSocketAdapter = new MockWebSocketAdapter();
        messageReceiver = new DefaultMessageReceiver(keyProvider, clockProvider, webSocketAdapter, recipientKeyId);

        messageReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs, String envelopeId) {
                receivedMessages.add(plaintext);
            }

            @Override
            public void onError(Throwable error) {
                receivedErrors.add(error);
            }

            @Override
            public void onPresenceUpdate(String userId, boolean active) {
                presenceUpdates.add(userId + ":" + active);
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
                        // ignore
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

        // Send via WebSocket with envelopeId
        String envelopeId = "env-001";
        String json = JsonCodec.toJson(encrypted);
        webSocketAdapter.simulateIncomingMessage(json, envelopeId);

        // Verify message was received and decrypted
        assertEquals(1, receivedMessages.size());
        assertArrayEquals(payload, receivedMessages.get(0));
        assertEquals(0, receivedErrors.size());

        // Verify ACK was NOT sent automatically (deferred until user views the chat)
        List<String> sent = webSocketAdapter.getSentMessages();
        assertEquals(0, sent.size(), "ACK should not be sent automatically on receipt");

        // Now explicitly acknowledge envelopes for the sender
        messageReceiver.acknowledgeEnvelopes(senderKeyId);

        // Verify ACK was sent after explicit acknowledge
        sent = webSocketAdapter.getSentMessages();
        assertEquals(1, sent.size());
        assertEquals("{\"envelopeIds\":[\"env-001\"]}", sent.get(0));
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
        webSocketAdapter.simulateIncomingMessage(json, "env-expired");

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
        webSocketAdapter.simulateIncomingMessage(json, "env-wrong-recipient");

        // Verify error was received (recipient mismatch)
        assertEquals(0, receivedMessages.size());
        assertEquals(1, receivedErrors.size());
        assertTrue(receivedErrors.get(0) instanceof MessageValidationException);
    }

    @Test
    void receives_presence_event_without_decryption_or_ack() throws Exception {
        messageReceiver.start();

        webSocketAdapter.simulateIncomingRaw("{\"type\":\"presence\",\"userId\":\"user-42\",\"active\":true}");

        assertEquals(0, receivedMessages.size());
        assertEquals(0, receivedErrors.size());
        assertEquals(List.of("user-42:true"), presenceUpdates);
        assertTrue(webSocketAdapter.getSentMessages().isEmpty());
    }

    @Test
    void decrypts_with_fallback_key_when_current_key_does_not_match() throws Exception {
        UserKeystore keyStore = new UserKeystore(tmpRoot);
        String rotatedCurrentKeyId = "key-rotated-current";
        KeyPair rotatedCurrentKey = EccKeyIO.generate();
        keyStore.saveKeypair(rotatedCurrentKeyId, rotatedCurrentKey, passphrase);

        messageReceiver.start();

        byte[] payload = "legacy key message".getBytes(StandardCharsets.UTF_8);
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(), senderKeyId, recipientKeyId, clockProvider);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", 3600);

        String envelopeId = "env-fallback";
        webSocketAdapter.simulateIncomingMessage(JsonCodec.toJson(encrypted), envelopeId);

        assertEquals(1, receivedMessages.size());
        assertArrayEquals(payload, receivedMessages.get(0));
        assertEquals(0, receivedErrors.size());
    }

    @Test
    void acknowledges_undecryptable_envelope_to_prevent_retries() throws Exception {
        messageReceiver.start();

        KeyPair unknownRecipientKey = EccKeyIO.generate();
        MessageEncryptor encryptor = new MessageEncryptor(
                unknownRecipientKey.getPublic(), senderKeyId, recipientKeyId, clockProvider);
        EncryptedMessage encrypted = encryptor.encrypt("xx".getBytes(StandardCharsets.UTF_8), "text/plain", 3600);

        String envelopeId = "env-bad-tag";
        webSocketAdapter.simulateIncomingMessage(JsonCodec.toJson(encrypted), envelopeId);

        assertEquals(0, receivedMessages.size());
        assertEquals(1, receivedErrors.size());
        assertTrue(receivedErrors.get(0) instanceof MessageTamperedException);

        List<String> sent = webSocketAdapter.getSentMessages();
        assertEquals(1, sent.size());
        assertEquals("{\"envelopeIds\":[\"env-bad-tag\"]}", sent.get(0));
    }

    @Test
    void decrypt_detached_message_succeeds_for_valid_payload() throws Exception {
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(), senderKeyId, recipientKeyId, clockProvider);
        EncryptedMessage encrypted = encryptor.encrypt("detached".getBytes(StandardCharsets.UTF_8), "text/plain", 3600);

        byte[] plaintext = messageReceiver.decryptDetachedMessage(encrypted);

        assertArrayEquals("detached".getBytes(StandardCharsets.UTF_8), plaintext);
    }

    @Test
    void duplicate_envelope_is_deduplicated() throws Exception {
        messageReceiver.start();

        byte[] payload = "dup".getBytes(StandardCharsets.UTF_8);
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(), senderKeyId, recipientKeyId, clockProvider);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", 3600);
        String json = JsonCodec.toJson(encrypted);

        webSocketAdapter.simulateIncomingMessage(json, "env-dup");
        webSocketAdapter.simulateIncomingMessage(json, "env-dup");

        assertEquals(1, receivedMessages.size(), "Same envelopeId should be processed once");
    }

    @Test
    void acknowledge_requeues_on_send_failure() throws Exception {
        messageReceiver.start();

        byte[] payload = "retry".getBytes(StandardCharsets.UTF_8);
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(), senderKeyId, recipientKeyId, clockProvider);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", 3600);
        webSocketAdapter.simulateIncomingMessage(JsonCodec.toJson(encrypted), "env-retry");

        webSocketAdapter.failNextSend();
        messageReceiver.acknowledgeEnvelopes(senderKeyId);
        assertTrue(webSocketAdapter.getSentMessages().isEmpty(), "First ACK send should fail");

        messageReceiver.acknowledgeEnvelopes(senderKeyId);
        assertEquals(List.of("{\"envelopeIds\":[\"env-retry\"]}"), webSocketAdapter.getSentMessages());
    }

    @Test
    void stop_closes_underlying_adapter() throws Exception {
        messageReceiver.start();

        messageReceiver.stop();

        assertEquals(1, webSocketAdapter.getCloseCalls());
        assertFalse(webSocketAdapter.isConnected());
    }
}
