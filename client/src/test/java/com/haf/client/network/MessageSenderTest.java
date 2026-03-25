package com.haf.client.network;

import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.dto.EncryptedMessage;
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
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

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

    static class MockWebSocketAdapter extends WebSocketAdapter {
        private String lastSentMessage;
        private boolean connected = false;

        MockWebSocketAdapter() {
            super(URI.create("ws://localhost:8080"), "test-session-id");
        }

        @Override
        public void connect(java.util.function.Consumer<String> onMessage,
                java.util.function.Consumer<Throwable> onError) throws IOException {
            connected = true;
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> postAuthenticated(String path, String jsonBody) {
            if ("/api/v1/messages".equals(path)) {
                try {
                    sendText(jsonBody);
                    return java.util.concurrent.CompletableFuture.completedFuture("{\"success\":true}");
                } catch (IOException e) {
                    return java.util.concurrent.CompletableFuture.failedFuture(e);
                }
            }
            return super.postAuthenticated(path, jsonBody);
        }

        @Override
        public void sendText(String message) throws IOException {
            if (!connected) {
                throw new IOException("Not connected");
            }
            lastSentMessage = message;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void close() {
            connected = false;
        }

        String getLastSentMessage() {
            return lastSentMessage;
        }
    }

    static class ApiStubWebSocketAdapter extends WebSocketAdapter {
        private final Map<String, String> postResponses = new HashMap<>();
        private final Map<String, String> getResponses = new HashMap<>();

        ApiStubWebSocketAdapter() {
            super(URI.create("ws://localhost:8080"), "api-test-session");
        }

        void whenPost(String path, String responseBody) {
            postResponses.put(path, responseBody);
        }

        void whenGet(String path, String responseBody) {
            getResponses.put(path, responseBody);
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> postAuthenticated(String path, String jsonBody) {
            if (postResponses.containsKey(path)) {
                return java.util.concurrent.CompletableFuture.completedFuture(postResponses.get(path));
            }
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IOException("No POST stub for " + path));
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> getAuthenticated(String path) {
            if (getResponses.containsKey(path)) {
                return java.util.concurrent.CompletableFuture.completedFuture(getResponses.get(path));
            }
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new IOException("No GET stub for " + path));
        }
    }

    private KeyProvider keyProvider;
    private ClockProvider clockProvider;
    private MockWebSocketAdapter webSocketAdapter;
    private MessageSender messageSender;

    @BeforeEach
    void setup() throws Exception {
        KeyPair kp = EccKeyIO.generate();
        keyProvider = new MockKeyProvider("sender-123", kp.getPublic());
        clockProvider = new FixedClockProvider(1000000L);
        webSocketAdapter = new MockWebSocketAdapter();
        messageSender = new DefaultMessageSender(keyProvider, clockProvider, webSocketAdapter);

        // Connect WebSocket
        webSocketAdapter.connect(msg -> {
        }, err -> {
        });
    }

    @Test
    void send_message_encrypts_and_sends() throws Exception {
        byte[] payload = "Hello, World!".getBytes(StandardCharsets.UTF_8);

        messageSender.sendMessage(payload, "recipient-123", "text/plain", 3600);

        assertNotNull(webSocketAdapter.getLastSentMessage());
        // Verify it's valid JSON
        JsonCodec.fromJson(webSocketAdapter.getLastSentMessage(), EncryptedMessage.class);
    }

    @Test
    void send_message_throws_on_unknown_recipient() {
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);

        assertThrows(KeyNotFoundException.class, () -> {
            messageSender.sendMessage(payload, "unknown-recipient", "text/plain", 3600);
        });
    }

    @Test
    void send_message_validates_message() {
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);

        // This should work (validation happens after encryption)
        assertDoesNotThrow(() -> {
            messageSender.sendMessage(payload, "recipient-123", "text/plain", 3600);
        });
    }

    @Test
    void send_message_throws_on_null_payload() {
        assertThrows(IllegalArgumentException.class, () -> {
            messageSender.sendMessage(null, "recipient-123", "text/plain", 3600);
        });
    }

    @Test
    void send_message_throws_when_not_connected() {
        webSocketAdapter.close();
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> {
            messageSender.sendMessage(payload, "recipient-123", "text/plain", 3600);
        });
    }

    @Test
    void send_encrypted_message_parses_envelope_metadata() throws Exception {
        ApiStubWebSocketAdapter apiAdapter = new ApiStubWebSocketAdapter();
        apiAdapter.whenPost("/api/v1/messages", "{\"envelopeId\":\"env-1\",\"expiresAt\":123456}");
        MessageSender sender = new DefaultMessageSender(keyProvider, clockProvider, apiAdapter);

        MessageSender.SendResult result = sender.sendEncryptedMessage(new EncryptedMessage());

        assertEquals("env-1", result.envelopeId());
        assertEquals(123456L, result.expiresAtEpochMs());
    }

    @Test
    void send_encrypted_message_defaults_expires_at_when_not_numeric() throws Exception {
        ApiStubWebSocketAdapter apiAdapter = new ApiStubWebSocketAdapter();
        apiAdapter.whenPost("/api/v1/messages", "{\"envelopeId\":\"env-2\",\"expiresAt\":\"oops\"}");
        MessageSender sender = new DefaultMessageSender(keyProvider, clockProvider, apiAdapter);

        MessageSender.SendResult result = sender.sendEncryptedMessage(new EncryptedMessage());

        assertEquals("env-2", result.envelopeId());
        assertEquals(0L, result.expiresAtEpochMs());
    }

    @Test
    void attachment_and_policy_apis_decode_payloads() throws Exception {
        ApiStubWebSocketAdapter apiAdapter = new ApiStubWebSocketAdapter();
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
