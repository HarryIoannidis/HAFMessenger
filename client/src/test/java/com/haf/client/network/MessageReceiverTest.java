package com.haf.client.network;

import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.shared.crypto.MessageEncryptor;
import com.haf.shared.crypto.MessageSignatureService;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.SigningKeyIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MessageReceiverTest {

    static class MockAuthHttpClient extends AuthHttpClient {
        private final ArrayDeque<String> messageResponses = new ArrayDeque<>();
        private final ArrayDeque<String> contactsResponses = new ArrayDeque<>();
        private RuntimeException getFailure;
        private final List<String> getPaths = new ArrayList<>();
        private final List<String> postPaths = new ArrayList<>();
        private final List<String> postBodies = new ArrayList<>();
        private int closeCalls;

        MockAuthHttpClient() {
            super(java.net.URI.create("https://localhost:8443"), "test-session-id");
        }

        @Override
        public CompletableFuture<String> getAuthenticated(String path) {
            getPaths.add(path);
            if (getFailure != null) {
                return CompletableFuture.failedFuture(getFailure);
            }
            if (path.startsWith("/api/v1/messages")) {
                String next = messageResponses.isEmpty() ? "{\"messages\":[]}" : messageResponses.removeFirst();
                return CompletableFuture.completedFuture(next);
            }
            if (path.startsWith("/api/v1/contacts")) {
                String next = contactsResponses.isEmpty() ? "{\"contacts\":[]}" : contactsResponses.removeFirst();
                return CompletableFuture.completedFuture(next);
            }
            return CompletableFuture.completedFuture("{}");
        }

        @Override
        public CompletableFuture<String> postAuthenticated(String path, String body) {
            postPaths.add(path);
            postBodies.add(body);
            return CompletableFuture.completedFuture("{\"acknowledged\":true}");
        }

        @Override
        public void close() {
            closeCalls++;
        }

        void enqueueMessagePoll(String responseJson) {
            messageResponses.addLast(responseJson);
        }

        void enqueueContactsPoll(String responseJson) {
            contactsResponses.addLast(responseJson);
        }

        void failGetWith(RuntimeException error) {
            getFailure = error;
        }
    }

    Path tmpRoot;
    char[] passphrase = "test-pass".toCharArray();
    KeyPair senderKp;
    KeyPair recipientKp;
    String senderKeyId;
    String recipientKeyId;
    UserKeystore keyStore;
    UserKeystoreKeyProvider keyProvider;
    ClockProvider clockProvider;
    MockAuthHttpClient authHttpClient;
    MessageReceiver messageReceiver;
    List<byte[]> receivedMessages;
    List<Throwable> receivedErrors;
    List<String> presenceUpdates;

    @BeforeEach
    void setup() throws Exception {
        tmpRoot = Files.createTempDirectory("haf-test-receiver");
        FilePerms.ensureDir700(tmpRoot);

        keyStore = new UserKeystore(tmpRoot);
        senderKeyId = "key-sender-001";
        recipientKeyId = "key-recipient-001";
        senderKp = EccKeyIO.generate();
        recipientKp = EccKeyIO.generate();
        keyStore.saveKeypair(senderKeyId, senderKp, passphrase);
        keyStore.saveKeypair(recipientKeyId, recipientKp, passphrase);

        keyProvider = new UserKeystoreKeyProvider(tmpRoot, recipientKeyId, passphrase);
        clockProvider = new FixedClockProvider(1_000_000L);
        authHttpClient = new MockAuthHttpClient();

        messageReceiver = new DefaultMessageReceiver(keyProvider, clockProvider, authHttpClient, recipientKeyId);
        receivedMessages = new ArrayList<>();
        receivedErrors = new ArrayList<>();
        presenceUpdates = new ArrayList<>();

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
        if (tmpRoot == null) {
            return;
        }
        try (var w = Files.walk(tmpRoot)) {
            w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Ignore cleanup failures in tests.
                }
            });
        }
    }

    @Test
    void polling_ingests_mailbox_and_acknowledges_over_http() throws Exception {
        byte[] payload = "hello polling".getBytes(StandardCharsets.UTF_8);
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(),
                senderKeyId,
                recipientKeyId,
                clockProvider);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", 3600);
        String senderSigningFingerprint = FingerprintUtil.sha256Hex(
                SigningKeyIO.publicDer(keyStore.loadSigningPublicKeyByKeyId(senderKeyId)));
        MessageSignatureService.sign(
                encrypted,
                keyStore.loadSigningPrivate(senderKeyId, passphrase),
                senderSigningFingerprint);
        String envelope = "{\"type\":\"message\",\"envelopeId\":\"env-1\",\"payload\":" + JsonCodec.toJson(encrypted) + "}";
        authHttpClient.enqueueMessagePoll("{\"messages\":[" + envelope + "]}");

        messageReceiver.start();
        waitForCondition(() -> receivedMessages.size() == 1, 2500L);

        assertArrayEquals(payload, receivedMessages.getFirst());
        messageReceiver.acknowledgeEnvelopes(senderKeyId);

        assertEquals(List.of("/api/v1/messages/ack"), authHttpClient.postPaths);
        assertEquals(List.of("{\"envelopeIds\":[\"env-1\"]}"), authHttpClient.postBodies);
    }

    @Test
    void polling_emits_presence_updates_only_on_state_change() throws Exception {
        authHttpClient.enqueueContactsPoll("{\"contacts\":[{\"userId\":\"u-1\",\"fullName\":\"User\",\"active\":true}]}");
        authHttpClient.enqueueContactsPoll("{\"contacts\":[{\"userId\":\"u-1\",\"fullName\":\"User\",\"active\":true}]}");
        authHttpClient.enqueueContactsPoll("{\"contacts\":[{\"userId\":\"u-1\",\"fullName\":\"User\",\"active\":false}]}");

        messageReceiver.start();
        waitForCondition(() -> presenceUpdates.size() >= 2, 6500L);

        assertEquals(List.of("u-1:true", "u-1:false"), presenceUpdates);
    }

    @Test
    void polling_stops_and_surfaces_auth_failure_once() throws Exception {
        authHttpClient.failGetWith(new HttpCommunicationException("Unauthorized", 401, "{\"error\":\"invalid session\"}"));

        messageReceiver.start();
        waitForCondition(() -> !receivedErrors.isEmpty(), 2500L);

        assertEquals(1, receivedErrors.size());
        assertTrue(isAuthenticationFailure(receivedErrors.getFirst()));
        assertTrue(authHttpClient.getPaths.size() <= 2);
    }

    @Test
    void stop_closes_adapter() throws Exception {
        messageReceiver.start();
        messageReceiver.stop();
        assertEquals(1, authHttpClient.closeCalls);
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
        long interval = TimeUnit.MILLISECONDS.toNanos(20L);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            LockSupport.parkNanos(interval);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while waiting for test condition");
            }
        }
        fail("Condition not met within timeout");
    }
}
