package com.haf.integration_test;

import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.client.network.*;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.EccKeyIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class MessageSendReceiveIT {

    static class InMemoryWebSocketAdapter extends WebSocketAdapter {
        private java.util.function.Consumer<String> messageConsumer;
        private java.util.function.Consumer<Throwable> errorConsumer;
        private boolean connected = false;
        private InMemoryWebSocketAdapter peer;

        InMemoryWebSocketAdapter() {
            super(java.net.URI.create("ws://localhost:8080"), "test-session-id");
        }

        void setPeer(InMemoryWebSocketAdapter peer) {
            this.peer = peer;
        }

        @Override
        public void connect(java.util.function.Consumer<String> onMessage,
                java.util.function.Consumer<Throwable> onError) throws java.io.IOException {
            this.messageConsumer = onMessage;
            this.errorConsumer = onError;
            connected = true;
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> postAuthenticated(String path, String jsonBody) {
            if ("/api/v1/messages".equals(path)) {
                try {
                    sendText(jsonBody);
                    return java.util.concurrent.CompletableFuture.completedFuture("{\"success\":true}");
                } catch (java.io.IOException e) {
                    return java.util.concurrent.CompletableFuture.failedFuture(e);
                }
            }
            return super.postAuthenticated(path, jsonBody);
        }

        @Override
        public void sendText(String message) throws java.io.IOException {
            if (!connected) {
                throw new java.io.IOException("Not connected");
            }
            if (peer != null && peer.messageConsumer != null) {
                String wrappedJson = "{\"type\":\"message\",\"payload\":" + message + "}";
                // Simulate network delay
                new Thread(() -> {
                    try {
                        peer.messageConsumer.accept(wrappedJson);
                    } catch (Exception e) {
                        if (peer.errorConsumer != null) {
                            peer.errorConsumer.accept(e);
                        }
                    }
                }).start();
            }
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void close() {
            connected = false;
        }
    }

    Path tmpRoot1;
    Path tmpRoot2;
    char[] passphrase = "test-pass".toCharArray();
    KeyPair senderKp;
    KeyPair recipientKp;
    String senderKeyId;
    String recipientKeyId;
    UserKeystoreKeyProvider senderKeyProvider;
    UserKeystoreKeyProvider recipientKeyProvider;
    ClockProvider clock;
    InMemoryWebSocketAdapter senderWebSocket;
    InMemoryWebSocketAdapter recipientWebSocket;
    MessageSender messageSender;
    MessageReceiver messageReceiver;
    byte[] receivedPayload;
    String receivedSenderId;
    String receivedContentType;
    long receivedTimestamp;
    CountDownLatch messageReceivedLatch;

    @BeforeEach
    void setup() throws Exception {
        tmpRoot1 = Files.createTempDirectory("haf-test-sender");
        tmpRoot2 = Files.createTempDirectory("haf-test-recipient");
        FilePerms.ensureDir700(tmpRoot1);
        FilePerms.ensureDir700(tmpRoot2);

        // Create sender and recipient keys
        UserKeystore senderKeyStore = new UserKeystore(tmpRoot1);
        UserKeystore recipientKeyStore = new UserKeystore(tmpRoot2);

        senderKeyId = "key-sender-001";
        recipientKeyId = "key-recipient-001";

        senderKp = EccKeyIO.generate();
        recipientKp = EccKeyIO.generate();

        senderKeyStore.saveKeypair(senderKeyId, senderKp, passphrase);
        recipientKeyStore.saveKeypair(recipientKeyId, recipientKp, passphrase);

        // Also save recipient key in sender's keystore for lookup (Phase 4 placeholder)
        senderKeyStore.saveKeypair(recipientKeyId, recipientKp, passphrase);

        senderKeyProvider = new UserKeystoreKeyProvider(tmpRoot1, senderKeyId, passphrase);
        recipientKeyProvider = new UserKeystoreKeyProvider(tmpRoot2, recipientKeyId, passphrase);

        clock = new FixedClockProvider(1000000L);

        // Create in-memory WebSocket adapters
        senderWebSocket = new InMemoryWebSocketAdapter();
        recipientWebSocket = new InMemoryWebSocketAdapter();
        senderWebSocket.setPeer(recipientWebSocket);
        recipientWebSocket.setPeer(senderWebSocket);

        messageSender = new DefaultMessageSender(senderKeyProvider, clock, senderWebSocket);
        messageReceiver = new DefaultMessageReceiver(recipientKeyProvider, clock, recipientWebSocket, recipientKeyId);

        messageReceivedLatch = new CountDownLatch(1);
        messageReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs, String envelopeId) {
                receivedPayload = plaintext;
                receivedSenderId = senderId;
                receivedContentType = contentType;
                receivedTimestamp = timestampEpochMs;
                messageReceivedLatch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                fail("Unexpected error: " + error.getMessage(), error);
            }
        });
    }

    @AfterEach
    void cleanup() throws Exception {
        if (tmpRoot1 != null) {
            try (var w = Files.walk(tmpRoot1)) {
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // Ignore failure
                    }
                });
            }
        }
        if (tmpRoot2 != null) {
            try (var w = Files.walk(tmpRoot2)) {
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // Ignore failure
                    }
                });
            }
        }
    }

    @Test
    void send_receive_decrypt_roundtrip() throws Exception {
        // Start receiver
        messageReceiver.start();

        // Connect sender WebSocket
        senderWebSocket.connect(msg -> {
        }, err -> {
        });

        // Send message
        byte[] payload = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";
        long ttlSeconds = 3600;

        messageSender.sendMessage(payload, recipientKeyId, contentType, ttlSeconds);

        // Wait for message to be received and decrypted
        assertTrue(messageReceivedLatch.await(5, TimeUnit.SECONDS),
                "Message should be received within 5 seconds");

        // Verify received message
        assertNotNull(receivedPayload);
        assertArrayEquals(payload, receivedPayload);
        assertEquals(senderKeyId, receivedSenderId);
        assertEquals(contentType, receivedContentType);
        assertEquals(1000000L, receivedTimestamp);
    }

    @Test
    void send_receive_with_deterministic_clock() throws Exception {
        // Use deterministic clock for testing
        ClockProvider testClock = new FixedClockProvider(2000000L);

        // Recreate sender and receiver with test clock
        MessageSender testSender = new DefaultMessageSender(senderKeyProvider, testClock, senderWebSocket);
        MessageReceiver testReceiver = new DefaultMessageReceiver(recipientKeyProvider, testClock, recipientWebSocket,
                recipientKeyId);

        CountDownLatch latch = new CountDownLatch(1);
        testReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs, String envelopeId) {
                assertEquals(2000000L, timestampEpochMs);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                fail("Unexpected error: " + error.getMessage(), error);
            }
        });

        testReceiver.start();
        senderWebSocket.connect(msg -> {
        }, err -> {
        });

        byte[] payload = "Test message".getBytes(StandardCharsets.UTF_8);
        testSender.sendMessage(payload, recipientKeyId, "text/plain", 3600);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be received");
    }

    @Test
    void send_receive_with_directory_service_fetch_roundtrip() throws Exception {
        // Create new keys and providers to isolate this test
        Path tmpSenderDir = Files.createTempDirectory("haf-test-ds-sender");
        Path tmpRecipientDir = Files.createTempDirectory("haf-test-ds-recipient");

        try {
            UserKeystore newSenderKeyStore = new UserKeystore(tmpSenderDir);
            UserKeystore newRecipientKeyStore = new UserKeystore(tmpRecipientDir);

            String newSenderKeyId = "sender-ds-001";
            String newRecipientKeyId = "recipient-ds-001";

            KeyPair newSenderKp = EccKeyIO.generate();
            KeyPair newRecipientKp = EccKeyIO.generate();

            newSenderKeyStore.saveKeypair(newSenderKeyId, newSenderKp, passphrase);
            newRecipientKeyStore.saveKeypair(newRecipientKeyId, newRecipientKp, passphrase);

            // CRITICAL: DO NOT save the recipient key in the sender's keystore!
            // The sender should not know the recipient's key yet.

            UserKeystoreKeyProvider newSenderKeyProvider = new UserKeystoreKeyProvider(tmpSenderDir, newSenderKeyId,
                    passphrase);
            UserKeystoreKeyProvider newRecipientKeyProvider = new UserKeystoreKeyProvider(tmpRecipientDir,
                    newRecipientKeyId, passphrase);

            // Configure directory service callback on the sender
            final String recipientPem = EccKeyIO.publicPem(newRecipientKp.getPublic());
            newSenderKeyProvider.setDirectoryServiceFetcher(recipientId -> {
                if (newRecipientKeyId.equals(recipientId)) {
                    return recipientPem;
                }
                return null;
            });

            InMemoryWebSocketAdapter newSenderWs = new InMemoryWebSocketAdapter();
            InMemoryWebSocketAdapter newRecipientWs = new InMemoryWebSocketAdapter();
            newSenderWs.setPeer(newRecipientWs);
            newRecipientWs.setPeer(newSenderWs);

            MessageSender dsSender = new DefaultMessageSender(newSenderKeyProvider, clock, newSenderWs);
            MessageReceiver dsReceiver = new DefaultMessageReceiver(newRecipientKeyProvider, clock, newRecipientWs,
                    newRecipientKeyId);

            CountDownLatch latch = new CountDownLatch(1);
            dsReceiver.setMessageListener(new MessageReceiver.MessageListener() {
                @Override
                public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs, String envelopeId) {
                    receivedPayload = plaintext;
                    receivedSenderId = senderId;
                    receivedContentType = contentType;
                    receivedTimestamp = timestampEpochMs;
                    latch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    fail("Unexpected error: " + error.getMessage(), error);
                }
            });

            dsReceiver.start();
            newSenderWs.connect(msg -> {
            }, err -> {
            });

            // Send message. This should trigger the fetcher callback.
            byte[] payload = "Hello, Directory Service!".getBytes(StandardCharsets.UTF_8);
            String contentType = "text/plain";
            dsSender.sendMessage(payload, newRecipientKeyId, contentType, 3600);

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be received");

            assertNotNull(receivedPayload);
            assertArrayEquals(payload, receivedPayload);
            assertEquals(newSenderKeyId, receivedSenderId);
            assertEquals(contentType, receivedContentType);

        } finally {
            // Cleanup local temp directories
            try (var w = Files.walk(tmpSenderDir)) {
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // Ignore failure
                    }
                });
            }
            try (var w = Files.walk(tmpRecipientDir)) {
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // Ignore failure
                    }
                });
            }
        }
    }
}
