package com.haf.client.network;

import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.constants.AttachmentConstants;
import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.requests.AttachmentBindRequest;
import com.haf.shared.requests.AttachmentCompleteRequest;
import com.haf.shared.requests.AttachmentInitRequest;
import com.haf.shared.responses.AttachmentBindResponse;
import com.haf.shared.responses.AttachmentChunkResponse;
import com.haf.shared.responses.AttachmentCompleteResponse;
import com.haf.shared.responses.AttachmentInitResponse;
import com.haf.shared.responses.MessagingPolicyResponse;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.SigningKeyIO;
import com.haf.shared.websocket.RealtimeErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
        private final PublicKey recipientPublicKey;
        private final PrivateKey senderSigningPrivateKey;
        private final String senderSigningFingerprint;

        MockKeyProvider(
                String senderId,
                PublicKey recipientPublicKey,
                PrivateKey senderSigningPrivateKey,
                String senderSigningFingerprint) {
            this.senderId = senderId;
            this.recipientPublicKey = recipientPublicKey;
            this.senderSigningPrivateKey = senderSigningPrivateKey;
            this.senderSigningFingerprint = senderSigningFingerprint;
        }

        @Override
        public PublicKey getRecipientPublicKey(String recipientId) throws KeyNotFoundException {
            if ("recipient-123".equals(recipientId)) {
                return recipientPublicKey;
            }
            throw new KeyNotFoundException("Recipient not found: " + recipientId);
        }

        @Override
        public String getSenderId() {
            return senderId;
        }

        @Override
        public PrivateKey getSenderSigningPrivateKey() {
            return senderSigningPrivateKey;
        }

        @Override
        public String getSenderSigningKeyFingerprint() {
            return senderSigningFingerprint;
        }
    }

    static class ApiStubAuthHttpClient extends AuthHttpClient {
        private final Map<String, String> postResponses = new HashMap<>();
        private final Map<String, String> getResponses = new HashMap<>();
        private final Map<String, Throwable> postFailures = new HashMap<>();
        private final Map<String, Throwable> getFailures = new HashMap<>();
        private final Map<String, HttpResponse<byte[]>> getByteResponses = new HashMap<>();
        private String lastPostBytesPath;
        private byte[] lastPostBytesBody;
        private Map<String, String> lastPostBytesHeaders;

        ApiStubAuthHttpClient() {
            super(URI.create("https://localhost:8443"), "api-test-session");
        }

        void whenPost(String path, String responseBody) {
            postResponses.put(path, responseBody);
        }

        void whenGet(String path, String responseBody) {
            getResponses.put(path, responseBody);
        }

        void whenPostFailure(String path, Throwable failure) {
            postFailures.put(path, failure);
        }

        void whenGetFailure(String path, Throwable failure) {
            getFailures.put(path, failure);
        }

        void whenGetBytes(String path, HttpResponse<byte[]> response) {
            getByteResponses.put(path, response);
        }

        @Override
        public CompletableFuture<String> postAuthenticated(String path, String jsonBody, Map<String, String> extraHeaders) {
            if (postFailures.containsKey(path)) {
                return CompletableFuture.failedFuture(postFailures.get(path));
            }
            if (postResponses.containsKey(path)) {
                return CompletableFuture.completedFuture(postResponses.get(path));
            }
            return CompletableFuture.failedFuture(new IOException("No POST stub for " + path));
        }

        @Override
        public CompletableFuture<String> postAuthenticatedBytes(
                String path,
                byte[] body,
                String contentType,
                Map<String, String> extraHeaders) {
            lastPostBytesPath = path;
            lastPostBytesBody = body;
            lastPostBytesHeaders = extraHeaders;
            if (postFailures.containsKey(path)) {
                return CompletableFuture.failedFuture(postFailures.get(path));
            }
            if (postResponses.containsKey(path)) {
                return CompletableFuture.completedFuture(postResponses.get(path));
            }
            return CompletableFuture.failedFuture(new IOException("No binary POST stub for " + path));
        }

        @Override
        public CompletableFuture<String> getAuthenticated(String path) {
            if (getFailures.containsKey(path)) {
                return CompletableFuture.failedFuture(getFailures.get(path));
            }
            if (getResponses.containsKey(path)) {
                return CompletableFuture.completedFuture(getResponses.get(path));
            }
            return CompletableFuture.failedFuture(new IOException("No GET stub for " + path));
        }

        @Override
        public CompletableFuture<HttpResponse<byte[]>> getAuthenticatedBytes(String path) {
            if (getByteResponses.containsKey(path)) {
                return CompletableFuture.completedFuture(getByteResponses.get(path));
            }
            return CompletableFuture.failedFuture(new IOException("No binary GET stub for " + path));
        }
    }

    static class RecordingRealtimeTransport implements RealtimeTransport {
        private EncryptedMessage lastMessage;
        private String lastRecipientFingerprint;

        @Override
        public void setEventListener(Consumer<com.haf.shared.websocket.RealtimeEvent> listener) {
        }

        @Override
        public void setErrorListener(Consumer<Throwable> listener) {
        }

        @Override
        public void start() {
        }

        @Override
        public void reconnect() {
        }

        @Override
        public MessageSender.SendResult sendMessage(EncryptedMessage encryptedMessage, String recipientKeyFingerprint)
                throws IOException {
            lastMessage = encryptedMessage;
            lastRecipientFingerprint = recipientKeyFingerprint;
            return new MessageSender.SendResult("env-record", 1111L);
        }

        @Override
        public void sendDeliveryReceipt(List<String> envelopeIds, String recipientId) {
        }

        @Override
        public void sendReadReceipt(List<String> envelopeIds, String recipientId) {
        }

        @Override
        public void sendTypingStart(String recipientId) {
        }

        @Override
        public void sendTypingStop(String recipientId) {
        }

        @Override
        public void close() {
        }
    }

    private KeyProvider keyProvider;
    private ClockProvider clockProvider;
    private ApiStubAuthHttpClient authHttpClient;
    private RecordingRealtimeTransport realtimeTransport;
    private MessageSender messageSender;

    @BeforeEach
    void setup() throws Exception {
        KeyPair recipientEncKey = EccKeyIO.generate();
        KeyPair senderSigningKey = SigningKeyIO.generate();
        String senderSigningFingerprint = FingerprintUtil.sha256Hex(SigningKeyIO.publicDer(senderSigningKey.getPublic()));
        keyProvider = new MockKeyProvider(
                "sender-123",
                recipientEncKey.getPublic(),
                senderSigningKey.getPrivate(),
                senderSigningFingerprint);
        clockProvider = new FixedClockProvider(1_000_000L);
        authHttpClient = new ApiStubAuthHttpClient();
        realtimeTransport = new RecordingRealtimeTransport();
        messageSender = new DefaultMessageSender(keyProvider, clockProvider, authHttpClient, realtimeTransport);
    }

    @Test
    void send_message_encrypts_and_uses_wss_transport() throws Exception {
        byte[] payload = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        messageSender.sendMessage(payload, "recipient-123", "text/plain", 3600);

        assertNotNull(realtimeTransport.lastMessage);
        assertEquals("sender-123", realtimeTransport.lastMessage.getSenderId());
        assertEquals("recipient-123", realtimeTransport.lastMessage.getRecipientId());
        assertNotNull(realtimeTransport.lastRecipientFingerprint);
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
        RecordingRealtimeTransport realtime = new RecordingRealtimeTransport();
        MessageSender sender = new DefaultMessageSender(keyProvider, clockProvider, apiAdapter, realtime);

        MessageSender.SendResult result = sender.sendEncryptedMessage(new EncryptedMessage());

        assertEquals("env-record", result.envelopeId());
        assertEquals(1111L, result.expiresAtEpochMs());
    }

    @Test
    void send_message_retries_once_when_recipient_key_is_stale() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ApiStubAuthHttpClient apiAdapter = new ApiStubAuthHttpClient();
        RecordingRealtimeTransport realtime = new RecordingRealtimeTransport() {
            @Override
            public MessageSender.SendResult sendMessage(
                    EncryptedMessage encryptedMessage,
                    String recipientKeyFingerprint) throws IOException {
                attempts.incrementAndGet();
                if (attempts.get() == 1) {
                    throw new RealtimeClientTransport.RealtimeException(
                            RealtimeErrorCode.STALE_RECIPIENT_KEY,
                            "recipient key is stale",
                            0);
                }
                return new MessageSender.SendResult("env-retry", 9999L);
            }
        };
        MessageSender sender = new DefaultMessageSender(keyProvider, clockProvider, apiAdapter, realtime);

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
        MessageSender sender = new DefaultMessageSender(
                keyProvider,
                clockProvider,
                apiAdapter,
                new RecordingRealtimeTransport());

        MessagingPolicyResponse policy = MessagingPolicyResponse.success(1000, 100, 32, 60);
        apiAdapter.whenGet("/api/v1/config/messaging", JsonCodec.toJson(policy));

        AttachmentInitResponse init = AttachmentInitResponse.success("att-1", 32, 1000);
        apiAdapter.whenPost("/api/v1/attachments/init", JsonCodec.toJson(init));

        AttachmentChunkResponse chunk = AttachmentChunkResponse.success("att-1", 0, true);
        apiAdapter.whenPost("/api/v1/attachments/att-1/chunk", JsonCodec.toJson(chunk));

        AttachmentCompleteResponse complete = AttachmentCompleteResponse.success("att-1", 1, 12, "COMPLETE");
        apiAdapter.whenPost("/api/v1/attachments/att-1/complete", JsonCodec.toJson(complete));

        AttachmentBindResponse bind = AttachmentBindResponse.success("att-1", "env-1", 5555);
        apiAdapter.whenPost("/api/v1/attachments/att-1/bind", JsonCodec.toJson(bind));

        apiAdapter.whenGetBytes("/api/v1/attachments/att-1", byteResponse(
                200,
                new byte[] { 1, 2, 3 },
                Map.of(
                        AttachmentConstants.HEADER_ATTACHMENT_ID, java.util.List.of("att-1"),
                        AttachmentConstants.HEADER_ATTACHMENT_CONTENT_TYPE,
                        java.util.List.of(AttachmentConstants.CONTENT_TYPE_ENCRYPTED_BLOB),
                        AttachmentConstants.HEADER_ATTACHMENT_ENCRYPTED_SIZE, java.util.List.of("3"),
                        AttachmentConstants.HEADER_ATTACHMENT_CHUNK_COUNT, java.util.List.of("1"))));

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

        assertTrue(sender.uploadAttachmentChunk("att-1", 0, new byte[] { 1, 2, 3 }).isStored());
        assertEquals("/api/v1/attachments/att-1/chunk", apiAdapter.lastPostBytesPath);
        assertEquals("0", apiAdapter.lastPostBytesHeaders.get(AttachmentConstants.HEADER_CHUNK_INDEX));
        assertEquals(3, apiAdapter.lastPostBytesBody.length);

        AttachmentCompleteRequest completeReq = new AttachmentCompleteRequest();
        completeReq.setExpectedChunks(1);
        completeReq.setEncryptedSizeBytes(12);
        assertEquals("COMPLETE", sender.completeAttachmentUpload("att-1", completeReq).getStatus());

        AttachmentBindRequest bindReq = new AttachmentBindRequest();
        bindReq.setEnvelopeId("env-1");
        assertEquals("env-1", sender.bindAttachmentUpload("att-1", bindReq).getEnvelopeId());

        MessageSender.AttachmentDownload downloaded = sender.downloadAttachment("att-1");
        assertEquals("att-1", downloaded.attachmentId());
        assertEquals(3, downloaded.encryptedBlob().length);
    }

    @Test
    void attachment_http_failures_surface_server_error_message() {
        ApiStubAuthHttpClient apiAdapter = new ApiStubAuthHttpClient();
        MessageSender sender = new DefaultMessageSender(
                keyProvider,
                clockProvider,
                apiAdapter,
                new RecordingRealtimeTransport());

        apiAdapter.whenPostFailure(
                "/api/v1/attachments/init",
                new HttpCommunicationException(
                        "HTTP POST failed with status 409",
                        409,
                        "{\"error\":\"attachment exceeds maximum size\"}"));

        AttachmentInitRequest initReq = new AttachmentInitRequest();
        initReq.setRecipientId("recipient-123");
        initReq.setContentType("application/vnd.haf.encrypted-message+json");
        initReq.setPlaintextSizeBytes(2000);
        initReq.setEncryptedSizeBytes(3000);
        initReq.setExpectedChunks(2);

        IOException ex = assertThrows(IOException.class, () -> sender.initAttachmentUpload(initReq));
        assertTrue(ex.getMessage().contains("HTTP 409 from server"));
        assertTrue(ex.getMessage().contains("attachment exceeds maximum size"));
    }

    @Test
    void attachment_decode_failure_reports_invalid_payload() {
        ApiStubAuthHttpClient apiAdapter = new ApiStubAuthHttpClient();
        MessageSender sender = new DefaultMessageSender(
                keyProvider,
                clockProvider,
                apiAdapter,
                new RecordingRealtimeTransport());

        apiAdapter.whenGet("/api/v1/config/messaging", "not-json");

        IOException ex = assertThrows(IOException.class, sender::fetchMessagingPolicy);
        assertTrue(ex.getMessage().contains("Failed to decode server response"));
    }

    private static HttpResponse<byte[]> byteResponse(int status, byte[] body, Map<String, java.util.List<String>> headers) {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://localhost")).build();
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<byte[]>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (name, value) -> true);
            }

            @Override
            public byte[] body() {
                return body;
            }

            @Override
            public Optional<javax.net.ssl.SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return request.uri();
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }
}
