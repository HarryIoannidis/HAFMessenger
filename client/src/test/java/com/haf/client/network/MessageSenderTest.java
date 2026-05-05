package com.haf.client.network;

import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.requests.AttachmentBindRequest;
import com.haf.shared.requests.AttachmentChunkRequest;
import com.haf.shared.requests.AttachmentCompleteRequest;
import com.haf.shared.requests.AttachmentInitRequest;
import com.haf.shared.responses.AttachmentBindResponse;
import com.haf.shared.responses.AttachmentChunkResponse;
import com.haf.shared.responses.AttachmentCompleteResponse;
import com.haf.shared.responses.AttachmentDownloadResponse;
import com.haf.shared.responses.AttachmentInitResponse;
import com.haf.shared.responses.MessagingPolicyResponse;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.JsonCodec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageSenderTest {

    static class MockKeyProvider implements KeyProvider {
        private final String senderId;
        private final PublicKey publicKey;

        MockKeyProvider(String senderId, PublicKey publicKey) {
            this.senderId = senderId;
            this.publicKey = publicKey;
        }

        @Override
        public PublicKey getRecipientPublicKey(String recipientId) throws KeyNotFoundException {
            if ("recipient-123".equals(recipientId)) {
                return publicKey;
            }
            throw new KeyNotFoundException("Recipient not found: " + recipientId);
        }

        @Override
        public String getSenderId() {
            return senderId;
        }
    }

    static class RecordingAuthHttpClient extends AuthHttpClient {
        private String lastPostPath;
        private String lastPostBody;

        RecordingAuthHttpClient() {
            super(URI.create("https://localhost:8443"), "test-session-id");
        }

        @Override
        public CompletableFuture<String> postAuthenticated(String path, String jsonBody, Map<String, String> extraHeaders) {
            lastPostPath = path;
            lastPostBody = jsonBody;
            return CompletableFuture.completedFuture("{\"envelopeId\":\"env-record\",\"expiresAt\":1111}");
        }
    }

    static class ApiStubAuthHttpClient extends AuthHttpClient {
        private final Map<String, String> postResponses = new HashMap<>();
        private final Map<String, String> getResponses = new HashMap<>();

        ApiStubAuthHttpClient() {
            super(URI.create("https://localhost:8443"), "api-test-session");
        }

        void whenPost(String path, String responseBody) {
            postResponses.put(path, responseBody);
        }

        void whenGet(String path, String responseBody) {
            getResponses.put(path, responseBody);
        }

        @Override
        public CompletableFuture<String> postAuthenticated(String path, String jsonBody, Map<String, String> extraHeaders) {
            if (postResponses.containsKey(path)) {
                return CompletableFuture.completedFuture(postResponses.get(path));
            }
            return CompletableFuture.failedFuture(new IOException("No POST stub for " + path));
        }

        @Override
        public CompletableFuture<String> getAuthenticated(String path) {
            if (getResponses.containsKey(path)) {
                return CompletableFuture.completedFuture(getResponses.get(path));
            }
            return CompletableFuture.failedFuture(new IOException("No GET stub for " + path));
        }
    }

    private KeyProvider keyProvider;
    private ClockProvider clockProvider;
    private RecordingAuthHttpClient authHttpClient;
    private MessageSender messageSender;

    @BeforeEach
    void setup() throws Exception {
        KeyPair kp = EccKeyIO.generate();
        keyProvider = new MockKeyProvider("sender-123", kp.getPublic());
        clockProvider = new FixedClockProvider(1_000_000L);
        authHttpClient = new RecordingAuthHttpClient();
        messageSender = new DefaultMessageSender(keyProvider, clockProvider, authHttpClient);
    }

    @Test
    void send_message_encrypts_and_posts_json() throws Exception {
        byte[] payload = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        messageSender.sendMessage(payload, "recipient-123", "text/plain", 3600);

        assertEquals("/api/v1/messages", authHttpClient.lastPostPath);
        assertNotNull(authHttpClient.lastPostBody);
        JsonCodec.fromJson(authHttpClient.lastPostBody, EncryptedMessage.class);
    }

    @Test
    void send_message_throws_on_unknown_recipient() {
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);
        assertThrows(KeyNotFoundException.class, () ->
                messageSender.sendMessage(payload, "unknown-recipient", "text/plain", 3600));
    }

    @Test
    void send_message_validates_arguments() {
        assertThrows(IllegalArgumentException.class, () ->
                messageSender.sendMessage(null, "recipient-123", "text/plain", 3600));
        assertDoesNotThrow(() ->
                messageSender.sendMessage("Hello".getBytes(StandardCharsets.UTF_8), "recipient-123", "text/plain", 3600));
    }

    @Test
    void send_encrypted_message_parses_envelope_metadata() throws Exception {
        ApiStubAuthHttpClient apiAdapter = new ApiStubAuthHttpClient();
        apiAdapter.whenPost("/api/v1/messages", "{\"envelopeId\":\"env-1\",\"expiresAt\":123456}");
        MessageSender sender = new DefaultMessageSender(keyProvider, clockProvider, apiAdapter);

        MessageSender.SendResult result = sender.sendEncryptedMessage(new EncryptedMessage());

        assertEquals("env-1", result.envelopeId());
        assertEquals(123456L, result.expiresAtEpochMs());
    }

    @Test
    void send_encrypted_message_defaults_expires_at_when_not_numeric() throws Exception {
        ApiStubAuthHttpClient apiAdapter = new ApiStubAuthHttpClient();
        apiAdapter.whenPost("/api/v1/messages", "{\"envelopeId\":\"env-2\",\"expiresAt\":\"oops\"}");
        MessageSender sender = new DefaultMessageSender(keyProvider, clockProvider, apiAdapter);

        MessageSender.SendResult result = sender.sendEncryptedMessage(new EncryptedMessage());

        assertEquals("env-2", result.envelopeId());
        assertEquals(0L, result.expiresAtEpochMs());
    }

    @Test
    void send_message_retries_once_when_recipient_key_is_stale() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ApiStubAuthHttpClient apiAdapter = new ApiStubAuthHttpClient() {
            @Override
            public CompletableFuture<String> postAuthenticated(String path, String jsonBody, Map<String, String> extraHeaders) {
                if (!"/api/v1/messages".equals(path)) {
                    return super.postAuthenticated(path, jsonBody, extraHeaders);
                }
                if (attempts.getAndIncrement() == 0) {
                    return CompletableFuture.failedFuture(
                            new HttpCommunicationException(
                                    "stale key",
                                    409,
                                    "{\"error\":\"recipient key is stale\",\"code\":\"stale_recipient_key\"}"));
                }
                return CompletableFuture.completedFuture("{\"envelopeId\":\"env-retry\",\"expiresAt\":9999}");
            }
        };
        MessageSender sender = new DefaultMessageSender(keyProvider, clockProvider, apiAdapter);

        MessageSender.SendResult result = sender.sendMessageWithResult(
                "hello".getBytes(StandardCharsets.UTF_8),
                "recipient-123",
                "text/plain",
                60);

        assertEquals("env-retry", result.envelopeId());
        assertEquals(2, attempts.get());
    }

    @Test
    void attachment_and_policy_apis_decode_payloads() throws Exception {
        ApiStubAuthHttpClient apiAdapter = new ApiStubAuthHttpClient();
        MessageSender sender = new DefaultMessageSender(keyProvider, clockProvider, apiAdapter);

        MessagingPolicyResponse policy = MessagingPolicyResponse.success(1000, 100, 32, java.util.List.of("application/pdf"), 60);
        apiAdapter.whenGet("/api/v1/config/messaging", JsonCodec.toJson(policy));

        AttachmentInitResponse init = AttachmentInitResponse.success("att-1", 32, 1000);
        apiAdapter.whenPost("/api/v1/attachments/init", JsonCodec.toJson(init));

        AttachmentChunkResponse chunk = AttachmentChunkResponse.success("att-1", 0, true);
        apiAdapter.whenPost("/api/v1/attachments/att-1/chunk", JsonCodec.toJson(chunk));

        AttachmentCompleteResponse complete = AttachmentCompleteResponse.success("att-1", 1, 12, "COMPLETE");
        apiAdapter.whenPost("/api/v1/attachments/att-1/complete", JsonCodec.toJson(complete));

        AttachmentBindResponse bind = AttachmentBindResponse.success("att-1", "env-1", 5555);
        apiAdapter.whenPost("/api/v1/attachments/att-1/bind", JsonCodec.toJson(bind));

        AttachmentDownloadResponse download = AttachmentDownloadResponse.success(
                "att-1", "sender", "recipient", "application/pdf", 12, 1, "AQID");
        apiAdapter.whenGet("/api/v1/attachments/att-1", JsonCodec.toJson(download));

        MessagingPolicyResponse decodedPolicy = sender.fetchMessagingPolicy();
        assertEquals(1000L, decodedPolicy.getAttachmentMaxBytes());
        assertEquals(32, decodedPolicy.getAttachmentChunkBytes());

        AttachmentInitRequest initReq = new AttachmentInitRequest();
        initReq.setRecipientId("recipient-123");
        initReq.setContentType("application/vnd.haf.encrypted-message+json");
        initReq.setPlaintextSizeBytes(2000);
        initReq.setEncryptedSizeBytes(3000);
        initReq.setExpectedChunks(2);
        assertEquals("att-1", sender.initAttachmentUpload(initReq).getAttachmentId());

        AttachmentChunkRequest chunkReq = new AttachmentChunkRequest();
        chunkReq.setChunkIndex(0);
        chunkReq.setChunkDataB64("AQID");
        assertTrue(sender.uploadAttachmentChunk("att-1", chunkReq).isStored());

        AttachmentCompleteRequest completeReq = new AttachmentCompleteRequest();
        completeReq.setExpectedChunks(1);
        completeReq.setEncryptedSizeBytes(12);
        assertEquals("COMPLETE", sender.completeAttachmentUpload("att-1", completeReq).getStatus());

        AttachmentBindRequest bindReq = new AttachmentBindRequest();
        bindReq.setEnvelopeId("env-1");
        assertEquals("env-1", sender.bindAttachmentUpload("att-1", bindReq).getEnvelopeId());

        assertEquals("att-1", sender.downloadAttachment("att-1").getAttachmentId());
    }
}
