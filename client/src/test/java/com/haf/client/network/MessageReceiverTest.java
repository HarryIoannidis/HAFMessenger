package com.haf.client.network;

import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.client.utils.ClientRuntimeConfig;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class MessageReceiverTest {

    static class MockWebSocketAdapter extends WebSocketAdapter {
        private java.util.function.Consumer<String> messageConsumer;
        private boolean connected = false;

        private final java.util.List<String> sentMessages = new java.util.ArrayList<>();
        private final java.util.List<String> postedBodies = new java.util.ArrayList<>();
        private final java.util.List<String> postedPaths = new java.util.ArrayList<>();
        private final java.util.List<String> getPaths = new java.util.ArrayList<>();
        private final ArrayDeque<String> messagePollResponses = new ArrayDeque<>();
        private final ArrayDeque<String> contactsPollResponses = new ArrayDeque<>();
        private boolean failNextSend;
        private int closeCalls;
        private java.io.IOException connectFailure;
        private int connectCalls;
        private RuntimeException getFailure;

        MockWebSocketAdapter() {
            super(java.net.URI.create("ws://localhost:8080"), "test-session-id");
        }

        @Override
        public void connect(java.util.function.Consumer<String> onMessage,
                java.util.function.Consumer<Throwable> onError) throws java.io.IOException {
            connectCalls++;
            if (connectFailure != null) {
                throw connectFailure;
            }
            this.messageConsumer = onMessage;
            connected = true;
        }

        @Override
        public CompletableFuture<String> getAuthenticated(String path) {
            getPaths.add(path);
            if (getFailure != null) {
                return CompletableFuture.failedFuture(getFailure);
            }
            String response;
            if (path != null && path.startsWith("/api/v1/messages")) {
                response = messagePollResponses.isEmpty() ? "{\"messages\":[]}" : messagePollResponses.removeFirst();
            } else if (path != null && path.startsWith("/api/v1/contacts")) {
                response = contactsPollResponses.isEmpty() ? "{\"contacts\":[]}" : contactsPollResponses.removeFirst();
            } else {
                response = "{}";
            }
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<String> postAuthenticated(String path, String body) {
            postedPaths.add(path);
            postedBodies.add(body);
            return CompletableFuture.completedFuture("{\"acknowledged\":true}");
        }

        @Override
        public void sendText(String text) throws java.io.IOException {
            if (!connected) {
                throw new java.io.IOException("WebSocket is not connected");
            }
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
            String wrappedJson = "{\"type\":\"message\",\"envelopeId\":\"" + envelopeId + "\",\"payload\":" + json
                    + "}";
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

        void failConnectWith(java.io.IOException error) {
            this.connectFailure = error;
        }

        void enqueuePollResponse(String responseJson) {
            messagePollResponses.addLast(responseJson);
        }

        void enqueueContactsPollResponse(String responseJson) {
            contactsPollResponses.addLast(responseJson);
        }

        java.util.List<String> getPostedBodies() {
            return postedBodies;
        }

        java.util.List<String> getPostedPaths() {
            return postedPaths;
        }

        java.util.List<String> getGetPaths() {
            return getPaths;
        }

        int getConnectCalls() {
            return connectCalls;
        }

        void failGetWith(RuntimeException failure) {
            this.getFailure = failure;
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
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs,
                    String envelopeId) {
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
    void cleanup() throws IOException {
        if (tmpRoot != null) {
            try (var w = Files.walk(tmpRoot)) {
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
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
    void receives_presence_event_with_extra_hidden_field() throws Exception {
        messageReceiver.start();

        webSocketAdapter
                .simulateIncomingRaw("{\"type\":\"presence\",\"userId\":\"user-42\",\"active\":false,\"hidden\":true}");

        assertEquals(0, receivedMessages.size());
        assertEquals(0, receivedErrors.size());
        assertEquals(List.of("user-42:false"), presenceUpdates);
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
    void acknowledge_defers_while_disconnected_then_sends_after_reconnect() throws Exception {
        messageReceiver.start();

        byte[] payload = "offline-ack".getBytes(StandardCharsets.UTF_8);
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(), senderKeyId, recipientKeyId, clockProvider);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", 3600);
        webSocketAdapter.simulateIncomingMessage(JsonCodec.toJson(encrypted), "env-offline-ack");

        webSocketAdapter.close();
        messageReceiver.acknowledgeEnvelopes(senderKeyId);
        assertTrue(webSocketAdapter.getSentMessages().isEmpty(), "ACK should be deferred while disconnected");

        messageReceiver.start();
        messageReceiver.acknowledgeEnvelopes(senderKeyId);
        assertEquals(List.of("{\"envelopeIds\":[\"env-offline-ack\"]}"), webSocketAdapter.getSentMessages());
    }

    @Test
    void start_falls_back_to_http_polling_when_websocket_connect_fails() throws Exception {
        webSocketAdapter.failConnectWith(new java.io.IOException("WebSocket connection timeout"));

        byte[] payload = "fallback-http".getBytes(StandardCharsets.UTF_8);
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(), senderKeyId, recipientKeyId, clockProvider);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", 3600);

        String envelopeJson = "{\"type\":\"message\",\"envelopeId\":\"env-http-1\",\"payload\":"
                + JsonCodec.toJson(encrypted) + "}";
        webSocketAdapter.enqueuePollResponse("{\"messages\":[" + envelopeJson + "]}");

        messageReceiver.start();

        waitForCondition(() -> !receivedMessages.isEmpty(), 1500L);
        assertEquals(1, receivedMessages.size());
        assertArrayEquals(payload, receivedMessages.get(0));
        assertTrue(webSocketAdapter.getGetPaths().stream().anyMatch(path -> path.startsWith("/api/v1/messages")));
    }

    @Test
    void prod_mode_uses_https_polling_without_attempting_websocket_connect() throws Exception {
        MessageReceiver pollingReceiver = new DefaultMessageReceiver(
                keyProvider,
                clockProvider,
                webSocketAdapter,
                recipientKeyId,
                ClientRuntimeConfig.MessagingTransportMode.HTTPS_POLLING);
        pollingReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs,
                    String envelopeId) {
                receivedMessages.add(plaintext);
            }

            @Override
            public void onError(Throwable error) {
                receivedErrors.add(error);
            }
        });

        pollingReceiver.start();
        waitForCondition(() -> !webSocketAdapter.getGetPaths().isEmpty(), 1500L);

        assertEquals(0, webSocketAdapter.getConnectCalls());
        assertTrue(webSocketAdapter.getGetPaths().stream().anyMatch(path -> path.startsWith("/api/v1/messages")));
    }

    @Test
    void acknowledge_uses_http_endpoint_when_fallback_polling_is_active() throws Exception {
        webSocketAdapter.failConnectWith(new java.io.IOException("WebSocket connection timeout"));

        byte[] payload = "fallback-ack".getBytes(StandardCharsets.UTF_8);
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(), senderKeyId, recipientKeyId, clockProvider);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", 3600);

        String envelopeJson = "{\"type\":\"message\",\"envelopeId\":\"env-http-ack\",\"payload\":"
                + JsonCodec.toJson(encrypted) + "}";
        webSocketAdapter.enqueuePollResponse("{\"messages\":[" + envelopeJson + "]}");

        messageReceiver.start();
        waitForCondition(() -> !receivedMessages.isEmpty(), 1500L);

        messageReceiver.acknowledgeEnvelopes(senderKeyId);

        assertEquals(List.of("/api/v1/messages/ack"), webSocketAdapter.getPostedPaths());
        assertEquals(List.of("{\"envelopeIds\":[\"env-http-ack\"]}"), webSocketAdapter.getPostedBodies());
    }

    @Test
    void polling_emits_presence_updates_only_when_state_changes() throws Exception {
        MessageReceiver pollingReceiver = new DefaultMessageReceiver(
                keyProvider,
                clockProvider,
                webSocketAdapter,
                recipientKeyId,
                ClientRuntimeConfig.MessagingTransportMode.HTTPS_POLLING);
        pollingReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs,
                    String envelopeId) {
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

        webSocketAdapter.enqueueContactsPollResponse(
                "{\"contacts\":[{\"userId\":\"u-42\",\"fullName\":\"User\",\"active\":true}]}");
        webSocketAdapter.enqueueContactsPollResponse(
                "{\"contacts\":[{\"userId\":\"u-42\",\"fullName\":\"User\",\"active\":true}]}");
        webSocketAdapter.enqueueContactsPollResponse(
                "{\"contacts\":[{\"userId\":\"u-42\",\"fullName\":\"User\",\"active\":false}]}");

        pollingReceiver.start();
        waitForCondition(() -> presenceUpdates.size() >= 2, 6500L);

        assertEquals(List.of("u-42:true", "u-42:false"), presenceUpdates);
    }

    @Test
    void stop_closes_underlying_adapter() throws Exception {
        messageReceiver.start();

        messageReceiver.stop();

        assertEquals(1, webSocketAdapter.getCloseCalls());
        assertFalse(webSocketAdapter.isConnected());
    }

    @Test
    void polling_stops_and_reports_single_authentication_failure() throws Exception {
        MessageReceiver pollingReceiver = new DefaultMessageReceiver(
                keyProvider,
                clockProvider,
                webSocketAdapter,
                recipientKeyId,
                ClientRuntimeConfig.MessagingTransportMode.HTTPS_POLLING);
        pollingReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs,
                    String envelopeId) {
                receivedMessages.add(plaintext);
            }

            @Override
            public void onError(Throwable error) {
                receivedErrors.add(error);
            }
        });

        webSocketAdapter.failGetWith(new HttpCommunicationException("Unauthorized", 401, "{\"error\":\"invalid session\"}"));

        pollingReceiver.start();
        waitForCondition(() -> !receivedErrors.isEmpty(), 2500L);

        assertEquals(1, receivedErrors.size(), "Auth failures should be surfaced once without repeated spam");
        assertTrue(isAuthenticationFailure(receivedErrors.getFirst()));
        assertTrue(webSocketAdapter.getGetPaths().size() <= 2, "Polling should stop after first failed cycle");
    }

    private static boolean isAuthenticationFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpCommunicationException communicationException) {
                int statusCode = communicationException.getStatusCode();
                return statusCode == 401 || statusCode == 403;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void waitForCondition(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        long pollIntervalNanos = TimeUnit.MILLISECONDS.toNanos(20L);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(pollIntervalNanos);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while waiting for test condition");
            }
        }
        fail("Condition not met within timeout");
    }
}
