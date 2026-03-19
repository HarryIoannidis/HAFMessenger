package com.haf.client.viewmodels;

import com.haf.client.models.MessageType;
import com.haf.client.models.MessageVM;
import com.haf.client.network.MessageReceiver;
import com.haf.client.network.MessageSender;
import com.haf.shared.constants.AttachmentConstants;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.requests.AttachmentBindRequest;
import com.haf.shared.responses.AttachmentBindResponse;
import com.haf.shared.requests.AttachmentChunkRequest;
import com.haf.shared.responses.AttachmentChunkResponse;
import com.haf.shared.requests.AttachmentCompleteRequest;
import com.haf.shared.responses.AttachmentCompleteResponse;
import com.haf.shared.responses.AttachmentDownloadResponse;
import com.haf.shared.requests.AttachmentInitRequest;
import com.haf.shared.responses.AttachmentInitResponse;
import com.haf.shared.dto.AttachmentReferencePayload;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.responses.MessagingPolicyResponse;
import com.haf.shared.utils.AttachmentPayloadCodec;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class MessageViewModelAttachmentTest {

    @TempDir
    Path tempDir;

    @Test
    void inline_path_is_used_when_file_is_within_inline_limit() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(1_024, 512, 256);
        StubReceiver receiver = new StubReceiver();
        MessageViewModel viewModel = new MessageViewModel(sender, receiver);

        Path file = tempDir.resolve("image.png");
        Files.write(file, new byte[256]);

        viewModel.sendAttachment("bob", file, "image/png");

        assertEquals(1, sender.sendCalls);
        assertEquals(0, sender.initCalls);
        assertEquals(AttachmentConstants.CONTENT_TYPE_INLINE, sender.lastSentContentType);
        assertEquals(MessageType.IMAGE, viewModel.getMessages("bob").getFirst().type());
    }

    @Test
    void chunked_path_is_used_when_file_exceeds_inline_limit() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(8_192, 512, 256);
        StubReceiver receiver = new StubReceiver();
        MessageViewModel viewModel = new MessageViewModel(sender, receiver);

        Path file = tempDir.resolve("report.pdf");
        Files.write(file, new byte[2_048]);

        viewModel.sendAttachment("bob", file, "application/pdf");

        assertEquals(0, sender.sendCalls);
        assertEquals(1, sender.initCalls);
        assertTrue(sender.chunkCalls > 0);
        assertEquals(1, sender.completeCalls);
        assertEquals(1, sender.bindCalls);
        assertEquals(1, sender.sendWithResultCalls);
        assertEquals(AttachmentConstants.CONTENT_TYPE_REFERENCE, sender.lastSendWithResultContentType);
        assertEquals(MessageType.FILE, viewModel.getMessages("bob").getFirst().type());
    }

    @Test
    void disallowed_mime_is_blocked_before_send() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(2_048, 512, 256);
        sender.policy.setAttachmentAllowedTypes(List.of("image/png"));

        StubReceiver receiver = new StubReceiver();
        MessageViewModel viewModel = new MessageViewModel(sender, receiver);

        Path file = tempDir.resolve("doc.pdf");
        Files.write(file, new byte[200]);

        viewModel.sendAttachment("bob", file, "application/pdf");

        assertEquals(0, sender.sendCalls);
        assertEquals(0, sender.initCalls);
        assertTrue(viewModel.statusProperty().get().contains("Failed to send attachment"));
    }

    @Test
    void reference_message_downloads_decrypts_and_renders() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(8_192, 512, 256);

        StubReceiver receiver = new StubReceiver();
        receiver.detachedDecryptResult = "pdf-content".getBytes(StandardCharsets.UTF_8);

        MessageViewModel viewModel = new MessageViewModel(sender, receiver);

        byte[] fakeEncryptedBlob = buildEncryptedMessageJsonBytes();
        sender.downloadResponse = AttachmentDownloadResponse.success(
                "att-1",
                "alice",
                "me",
                AttachmentConstants.CONTENT_TYPE_ENCRYPTED_BLOB,
                fakeEncryptedBlob.length,
                2,
                Base64.getEncoder().encodeToString(fakeEncryptedBlob));

        AttachmentReferencePayload ref = new AttachmentReferencePayload();
        ref.setAttachmentId("att-1");
        ref.setFileName("report.pdf");
        ref.setMediaType("application/pdf");
        ref.setSizeBytes(11);

        receiver.emitMessage(
                AttachmentPayloadCodec.toReferenceJson(ref).getBytes(StandardCharsets.UTF_8),
                "alice",
                AttachmentConstants.CONTENT_TYPE_REFERENCE,
                Instant.now().toEpochMilli(),
                "env-1");

        assertEquals(1, viewModel.getMessages("alice").size());
        assertEquals(MessageType.FILE, viewModel.getMessages("alice").getFirst().type());
        assertEquals("report.pdf", viewModel.getMessages("alice").getFirst().fileName());
    }

    @Test
    void chunked_image_reference_shows_loading_then_swaps_to_image() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(8_192, 512, 256);
        sender.downloadDelayMs = 120L;

        StubReceiver receiver = new StubReceiver();
        receiver.detachedDecryptResult = new byte[] { 1, 2, 3, 4 };
        MessageViewModel viewModel = new MessageViewModel(sender, receiver);

        byte[] fakeEncryptedBlob = buildEncryptedMessageJsonBytes();
        sender.downloadResponse = AttachmentDownloadResponse.success(
                "att-img-1",
                "alice",
                "me",
                AttachmentConstants.CONTENT_TYPE_ENCRYPTED_BLOB,
                fakeEncryptedBlob.length,
                2,
                Base64.getEncoder().encodeToString(fakeEncryptedBlob));

        AttachmentReferencePayload ref = new AttachmentReferencePayload();
        ref.setAttachmentId("att-img-1");
        ref.setFileName("photo.png");
        ref.setMediaType("image/png");
        ref.setSizeBytes(4);

        receiver.emitMessage(
                AttachmentPayloadCodec.toReferenceJson(ref).getBytes(StandardCharsets.UTF_8),
                "alice",
                AttachmentConstants.CONTENT_TYPE_REFERENCE,
                Instant.now().toEpochMilli(),
                "env-2");

        assertEquals(1, viewModel.getMessages("alice").size());
        assertEquals(MessageType.IMAGE, viewModel.getMessages("alice").getFirst().type());
        assertTrue(viewModel.getMessages("alice").getFirst().isLoading());

        awaitCondition(() -> {
            MessageVM first = viewModel.getMessages("alice").getFirst();
            return first.type() == MessageType.IMAGE && !first.isLoading() && first.content() != null;
        });

        MessageVM resolved = viewModel.getMessages("alice").getFirst();
        assertEquals(MessageType.IMAGE, resolved.type());
        assertFalse(resolved.isLoading());
        assertNotNull(resolved.content());
    }

    private static MessagingPolicyResponse policy(long max, long inlineMax, int chunk) {
        return MessagingPolicyResponse.success(
                max,
                inlineMax,
                chunk,
                List.of(
                        "image/png",
                        "image/jpeg",
                        "image/gif",
                        "image/webp",
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                1800);
    }

    private static byte[] buildEncryptedMessageJsonBytes() {
        EncryptedMessage m = new EncryptedMessage();
        m.setVersion(MessageHeader.VERSION);
        m.setAlgorithm(MessageHeader.ALGO_AEAD);
        m.setSenderId("alice");
        m.setRecipientId("me");
        m.setTimestampEpochMs(System.currentTimeMillis());
        m.setTtlSeconds(3600);
        m.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
        m.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[32]));
        m.setCiphertextB64(Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8)));
        m.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
        m.setContentType("application/pdf");
        m.setContentLength(1);
        return JsonCodec.toJson(m).getBytes(StandardCharsets.UTF_8);
    }

    private static void awaitCondition(BooleanSupplier condition) throws InterruptedException {
        long timeoutAt = System.currentTimeMillis() + 2_000L;
        while (System.currentTimeMillis() < timeoutAt) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Condition not met within timeout");
    }

    private static final class RecordingSender implements MessageSender {
        private MessagingPolicyResponse policy;
        private AttachmentDownloadResponse downloadResponse;
        private long downloadDelayMs;

        private int sendCalls;
        private int sendWithResultCalls;
        private int initCalls;
        private int chunkCalls;
        private int completeCalls;
        private int bindCalls;

        private String lastSentContentType;
        private String lastSendWithResultContentType;

        @Override
        public void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds) {
            sendCalls++;
            lastSentContentType = contentType;
        }

        @Override
        public SendResult sendMessageWithResult(byte[] payload, String recipientId, String contentType, long ttlSeconds) {
            sendWithResultCalls++;
            lastSendWithResultContentType = contentType;
            return new SendResult("env-123", System.currentTimeMillis() + 60_000);
        }

        @Override
        public EncryptedMessage encryptMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds) {
            EncryptedMessage m = new EncryptedMessage();
            m.setVersion(MessageHeader.VERSION);
            m.setAlgorithm(MessageHeader.ALGO_AEAD);
            m.setSenderId("me");
            m.setRecipientId(recipientId);
            m.setTimestampEpochMs(System.currentTimeMillis());
            m.setTtlSeconds(3600);
            m.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
            m.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[32]));
            m.setCiphertextB64(Base64.getEncoder().encodeToString(payload));
            m.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
            m.setContentType(contentType);
            m.setContentLength(payload.length);
            return m;
        }

        @Override
        public MessagingPolicyResponse fetchMessagingPolicy() {
            return policy;
        }

        @Override
        public AttachmentInitResponse initAttachmentUpload(AttachmentInitRequest request) {
            initCalls++;
            return AttachmentInitResponse.success("att-1", policy.getAttachmentChunkBytes(), System.currentTimeMillis() + 60_000);
        }

        @Override
        public AttachmentChunkResponse uploadAttachmentChunk(String attachmentId, AttachmentChunkRequest request) {
            chunkCalls++;
            return AttachmentChunkResponse.success(attachmentId, request.getChunkIndex(), true);
        }

        @Override
        public AttachmentCompleteResponse completeAttachmentUpload(String attachmentId, AttachmentCompleteRequest request) {
            completeCalls++;
            return AttachmentCompleteResponse.success(attachmentId, request.getExpectedChunks(), request.getEncryptedSizeBytes(), "COMPLETE");
        }

        @Override
        public AttachmentBindResponse bindAttachmentUpload(String attachmentId, AttachmentBindRequest request) {
            bindCalls++;
            return AttachmentBindResponse.success(attachmentId, request.getEnvelopeId(), System.currentTimeMillis() + 60_000);
        }

        @Override
        public AttachmentDownloadResponse downloadAttachment(String attachmentId) {
            if (downloadDelayMs > 0) {
                try {
                    Thread.sleep(downloadDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            return downloadResponse;
        }
    }

    private static final class StubReceiver implements MessageReceiver {
        private MessageListener listener;
        private byte[] detachedDecryptResult = new byte[0];
        private final List<String> acked = new ArrayList<>();

        @Override
        public void setMessageListener(MessageListener listener) {
            this.listener = listener;
        }

        @Override
        public void start() throws IOException {
            // no-op
        }

        @Override
        public void stop() {
            // no-op
        }

        @Override
        public void acknowledgeEnvelopes(String senderId) {
            acked.add(senderId);
        }

        @Override
        public byte[] decryptDetachedMessage(EncryptedMessage encryptedMessage) {
            return detachedDecryptResult;
        }

        void emitMessage(byte[] plaintext, String senderId, String contentType, long timestamp, String envelopeId) {
            if (listener != null) {
                listener.onMessage(plaintext, senderId, contentType, timestamp, envelopeId);
            }
        }
    }
}
