package com.haf.integration_test;

import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.client.network.AuthHttpClient;
import com.haf.client.network.DefaultMessageReceiver;
import com.haf.client.network.DefaultMessageSender;
import com.haf.client.network.MessageReceiver;
import com.haf.client.network.MessageSender;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.JsonCodec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MessageSendReceiveIT {

    static class InMemoryAuthHttpClient extends AuthHttpClient {
        private static final Map<String, ArrayDeque<String>> MAILBOX_BY_USER = new ConcurrentHashMap<>();
        private static int envelopeCounter = 0;

        private final String userId;

        InMemoryAuthHttpClient(String userId) {
            super(java.net.URI.create("https://localhost:8443"), "test-session-id");
            this.userId = userId;
            MAILBOX_BY_USER.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        }

        @Override
        public CompletableFuture<String> postAuthenticated(String path, String body, java.util.Map<String, String> extraHeaders) {
            if ("/api/v1/messages".equals(path)) {
                EncryptedMessage payload = JsonCodec.fromJson(body, EncryptedMessage.class);
                String recipientId = payload.getRecipientId();
                String envelopeId = nextEnvelopeId();
                String wrapped = "{\"type\":\"message\",\"envelopeId\":\"" + envelopeId + "\",\"payload\":" + body + "}";
                MAILBOX_BY_USER.computeIfAbsent(recipientId, ignored -> new ArrayDeque<>()).addLast(wrapped);
                return CompletableFuture.completedFuture("{\"envelopeId\":\"" + envelopeId + "\",\"expiresAt\":9999999}");
            }
            if ("/api/v1/messages/ack".equals(path)) {
                return CompletableFuture.completedFuture("{\"acknowledged\":true}");
            }
            return CompletableFuture.completedFuture("{}");
        }

        @Override
        public CompletableFuture<String> getAuthenticated(String path) {
            if (path.startsWith("/api/v1/messages")) {
                ArrayDeque<String> mailbox = MAILBOX_BY_USER.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
                if (mailbox.isEmpty()) {
                    return CompletableFuture.completedFuture("{\"messages\":[]}");
                }
                String message = mailbox.removeFirst();
                return CompletableFuture.completedFuture("{\"messages\":[" + message + "]}");
            }
            if (path.startsWith("/api/v1/contacts")) {
                return CompletableFuture.completedFuture("{\"contacts\":[]}");
            }
            return CompletableFuture.completedFuture("{}");
        }

        private static synchronized String nextEnvelopeId() {
            envelopeCounter++;
            return "env-" + envelopeCounter;
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
    InMemoryAuthHttpClient senderHttpClient;
    InMemoryAuthHttpClient recipientHttpClient;
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

        UserKeystore senderKeyStore = new UserKeystore(tmpRoot1);
        UserKeystore recipientKeyStore = new UserKeystore(tmpRoot2);

        senderKeyId = "key-sender-001";
        recipientKeyId = "key-recipient-001";
        senderKp = EccKeyIO.generate();
        recipientKp = EccKeyIO.generate();
        senderKeyStore.saveKeypair(senderKeyId, senderKp, passphrase);
        recipientKeyStore.saveKeypair(recipientKeyId, recipientKp, passphrase);

        // Sender knows recipient public key for encryption.
        senderKeyStore.saveKeypair(recipientKeyId, recipientKp, passphrase);

        senderKeyProvider = new UserKeystoreKeyProvider(tmpRoot1, senderKeyId, passphrase);
        recipientKeyProvider = new UserKeystoreKeyProvider(tmpRoot2, recipientKeyId, passphrase);
        clock = new FixedClockProvider(1_000_000L);

        senderHttpClient = new InMemoryAuthHttpClient(senderKeyId);
        recipientHttpClient = new InMemoryAuthHttpClient(recipientKeyId);

        messageSender = new DefaultMessageSender(senderKeyProvider, clock, senderHttpClient);
        messageReceiver = new DefaultMessageReceiver(recipientKeyProvider, clock, recipientHttpClient, recipientKeyId);

        messageReceivedLatch = new CountDownLatch(1);
        messageReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs,
                    String envelopeId) {
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
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // Ignore cleanup failures in tests.
                    }
                });
            }
        }
        if (tmpRoot2 != null) {
            try (var w = Files.walk(tmpRoot2)) {
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // Ignore cleanup failures in tests.
                    }
                });
            }
        }
    }

    @Test
    void send_receive_decrypt_roundtrip() throws Exception {
        messageReceiver.start();

        byte[] payload = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String contentType = "text/plain";
        long ttlSeconds = 3600;

        messageSender.sendMessage(payload, recipientKeyId, contentType, ttlSeconds);

        assertTrue(messageReceivedLatch.await(5, TimeUnit.SECONDS), "Message should be received within 5 seconds");
        assertNotNull(receivedPayload);
        assertArrayEquals(payload, receivedPayload);
        assertEquals(senderKeyId, receivedSenderId);
        assertEquals(contentType, receivedContentType);
        assertEquals(1_000_000L, receivedTimestamp);
    }

    @Test
    void send_receive_with_deterministic_clock() throws Exception {
        ClockProvider testClock = new FixedClockProvider(2_000_000L);
        MessageSender testSender = new DefaultMessageSender(senderKeyProvider, testClock, senderHttpClient);
        MessageReceiver testReceiver = new DefaultMessageReceiver(recipientKeyProvider, testClock, recipientHttpClient,
                recipientKeyId);

        CountDownLatch latch = new CountDownLatch(1);
        testReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs,
                    String envelopeId) {
                assertEquals(2_000_000L, timestampEpochMs);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                fail("Unexpected error: " + error.getMessage(), error);
            }
        });

        testReceiver.start();
        testSender.sendMessage("Test message".getBytes(StandardCharsets.UTF_8), recipientKeyId, "text/plain", 3600);

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be received");
    }

    @Test
    void send_receive_with_directory_service_fetch_roundtrip() throws Exception {
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

            UserKeystoreKeyProvider newSenderKeyProvider = new UserKeystoreKeyProvider(tmpSenderDir, newSenderKeyId,
                    passphrase);
            UserKeystoreKeyProvider newRecipientKeyProvider = new UserKeystoreKeyProvider(tmpRecipientDir,
                    newRecipientKeyId, passphrase);

            final String recipientPem = EccKeyIO.publicPem(newRecipientKp.getPublic());
            newSenderKeyProvider.setDirectoryServiceFetcher(recipientId -> {
                if (newRecipientKeyId.equals(recipientId)) {
                    return recipientPem;
                }
                return null;
            });

            InMemoryAuthHttpClient newSenderClient = new InMemoryAuthHttpClient(newSenderKeyId);
            InMemoryAuthHttpClient newRecipientClient = new InMemoryAuthHttpClient(newRecipientKeyId);
            MessageSender dsSender = new DefaultMessageSender(newSenderKeyProvider, clock, newSenderClient);
            MessageReceiver dsReceiver = new DefaultMessageReceiver(newRecipientKeyProvider, clock, newRecipientClient,
                    newRecipientKeyId);

            CountDownLatch latch = new CountDownLatch(1);
            dsReceiver.setMessageListener(new MessageReceiver.MessageListener() {
                @Override
                public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs,
                        String envelopeId) {
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
            byte[] payload = "Hello, Directory Service!".getBytes(StandardCharsets.UTF_8);
            dsSender.sendMessage(payload, newRecipientKeyId, "text/plain", 3600);

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Message should be received");
            assertNotNull(receivedPayload);
            assertArrayEquals(payload, receivedPayload);
            assertEquals(newSenderKeyId, receivedSenderId);
            assertEquals("text/plain", receivedContentType);
        } finally {
            try (var w = Files.walk(tmpSenderDir)) {
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // Ignore cleanup failures in tests.
                    }
                });
            }
            try (var w = Files.walk(tmpRecipientDir)) {
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                        // Ignore cleanup failures in tests.
                    }
                });
            }
        }
    }
}
