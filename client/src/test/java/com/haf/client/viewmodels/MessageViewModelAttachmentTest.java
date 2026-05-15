package com.haf.client.viewmodels;

import com.haf.client.models.MessageType;
import com.haf.client.models.MessageVM;
import com.haf.client.network.MessageReceiver;
import com.haf.client.network.MessageSender;
import com.haf.client.utils.ImageSaveSupport;
import com.haf.client.utils.ClientSettings;
import com.haf.shared.constants.AttachmentConstants;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.requests.AttachmentBindRequest;
import com.haf.shared.responses.AttachmentBindResponse;
import com.haf.shared.responses.AttachmentChunkResponse;
import com.haf.shared.requests.AttachmentCompleteRequest;
import com.haf.shared.responses.AttachmentCompleteResponse;
import com.haf.shared.requests.AttachmentInitRequest;
import com.haf.shared.responses.AttachmentInitResponse;
import com.haf.shared.dto.AttachmentInlinePayload;
import com.haf.shared.dto.AttachmentReferencePayload;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.responses.MessagingPolicyResponse;
import com.haf.shared.utils.AttachmentPayloadCodec;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class MessageViewModelAttachmentTest {

    @TempDir
    Path tempDir;

    @Test
    void inline_path_is_used_when_file_is_within_inline_limit() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(1_024, 512, 256);
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        Path file = tempDir.resolve("image.png");
        Files.write(file, pseudoPngBytes(256));

        viewModel.sendAttachment("bob", file, "image/png");
        awaitCondition(() -> sender.sendWithResultCalls == 1 && viewModel.getMessages("bob").size() == 1);

        assertEquals(0, sender.sendCalls);
        assertEquals(1, sender.sendWithResultCalls);
        assertEquals(0, sender.initCalls);
        assertEquals(AttachmentConstants.CONTENT_TYPE_INLINE, sender.lastSendWithResultContentType);
        MessageVM first = viewModel.getMessages("bob").getFirst();
        assertEquals(MessageType.IMAGE, first.type());
        assertEquals("env-123", first.envelopeId());
    }

    @Test
    void arbitrary_valid_file_attachment_types_are_allowed() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(1_024, 512, 256);
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        Path file = tempDir.resolve("installer.exe");
        Files.write(file, new byte[256]);

        viewModel.sendAttachment("bob", file, "application/vnd.microsoft.portable-executable");
        awaitCondition(() -> sender.sendWithResultCalls == 1 && viewModel.getMessages("bob").size() == 1);

        MessageVM message = viewModel.getMessages("bob").getFirst();
        assertEquals(AttachmentConstants.CONTENT_TYPE_INLINE, sender.lastSendWithResultContentType);
        assertEquals(MessageType.FILE, message.type());
        assertEquals("installer.exe", message.fileName());
    }

    @Test
    void outgoing_inline_image_shows_loading_then_swaps_to_image() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(1_024, 512, 256);
        sender.sendDelayMs = 120L;
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        Path file = tempDir.resolve("small-photo.png");
        Files.write(file, pseudoPngBytes(256));

        viewModel.sendAttachment("bob", file, "image/png");

        awaitCondition(() -> !viewModel.getMessages("bob").isEmpty());
        MessageVM loading = viewModel.getMessages("bob").getFirst();
        assertEquals(MessageType.IMAGE, loading.type());
        assertTrue(loading.isLoading());
        assertNull(loading.content());

        awaitCondition(() -> {
            MessageVM message = viewModel.getMessages("bob").getFirst();
            return sender.sendWithResultCalls == 1
                    && message.type() == MessageType.IMAGE
                    && !message.isLoading()
                    && message.content() != null;
        });

        MessageVM resolved = viewModel.getMessages("bob").getFirst();
        assertFalse(resolved.isLoading());
        assertEquals("small-photo.png", resolved.fileName());
    }

    @Test
    void resolved_outgoing_inline_image_content_points_to_existing_local_file() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(1_024, 512, 256);
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        Path file = tempDir.resolve("small-photo.png");
        Files.write(file, pseudoPngBytes(256));

        viewModel.sendAttachment("bob", file, "image/png");
        awaitCondition(() -> sender.sendWithResultCalls == 1 && !viewModel.getMessages("bob").isEmpty());

        MessageVM resolved = viewModel.getMessages("bob").getFirst();
        assertEquals(MessageType.IMAGE, resolved.type());
        assertNotNull(resolved.content());
        Path localImagePath = ImageSaveSupport.resolveLocalSourcePath(resolved.content());
        assertNotNull(localImagePath);
        assertTrue(Files.exists(localImagePath));
    }

    @Test
    void image_quality_100_sends_original_inline_image_bytes() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(1_000_000, 1_000_000, 256);
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        byte[] original = realPngBytes(80, 80);
        Path file = tempDir.resolve("photo.png");
        Files.write(file, original);

        viewModel.sendAttachment("bob", file, "image/png");
        awaitCondition(() -> sender.sendWithResultCalls == 1);

        AttachmentInlinePayload sent = AttachmentPayloadCodec.fromInlineJson(
                new String(sender.lastSentPayload, StandardCharsets.UTF_8));
        assertEquals("image/png", sent.getMediaType());
        assertEquals("photo.png", sent.getFileName());
        assertArrayEquals(original, Base64.getDecoder().decode(sent.getDataB64()));
    }

    @Test
    void lower_image_quality_optimizes_image_before_send() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(2_000_000, 2_000_000, 256);
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);
        ClientSettings settings = ClientSettings.defaults();
        settings.setMediaSendInMaxQuality(false);
        settings.setMediaImageSendQuality(60);
        viewModel.setSettings(settings);

        byte[] original = realPngBytes(360, 360);
        Path file = tempDir.resolve("photo.png");
        Files.write(file, original);

        viewModel.sendAttachment("bob", file, "image/png");
        awaitCondition(() -> sender.sendWithResultCalls == 1);

        AttachmentInlinePayload sent = AttachmentPayloadCodec.fromInlineJson(
                new String(sender.lastSentPayload, StandardCharsets.UTF_8));
        byte[] optimized = Base64.getDecoder().decode(sent.getDataB64());
        assertEquals("image/jpeg", sent.getMediaType());
        assertEquals("photo.jpg", sent.getFileName());
        assertTrue(optimized.length < original.length);
    }

    @Test
    void send_in_max_quality_ignores_lower_slider_quality() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(2_000_000, 2_000_000, 256);
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);
        ClientSettings settings = ClientSettings.defaults();
        settings.setMediaImageSendQuality(60);
        settings.setMediaSendInMaxQuality(true);
        viewModel.setSettings(settings);

        byte[] original = realPngBytes(360, 360);
        Path file = tempDir.resolve("photo.png");
        Files.write(file, original);

        viewModel.sendAttachment("bob", file, "image/png");
        awaitCondition(() -> sender.sendWithResultCalls == 1);

        AttachmentInlinePayload sent = AttachmentPayloadCodec.fromInlineJson(
                new String(sender.lastSentPayload, StandardCharsets.UTF_8));
        assertEquals("image/png", sent.getMediaType());
        assertEquals("photo.png", sent.getFileName());
        assertArrayEquals(original, Base64.getDecoder().decode(sent.getDataB64()));
    }

    @Test
    void chunked_path_is_used_when_file_exceeds_inline_limit() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(8_192, 512, 256);
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        Path file = tempDir.resolve("report.pdf");
        Files.write(file, new byte[2_048]);

        viewModel.sendAttachment("bob", file, "application/pdf");
        awaitCondition(() -> sender.bindCalls == 1 && viewModel.getMessages("bob").size() == 1);

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
    void chunked_path_is_used_when_inline_would_exceed_envelope_budget() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(10_000_000, 10_000_000, 1_048_576);
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        Path file = tempDir.resolve("large-photo.png");
        Files.write(file, pseudoPngBytes(2_600_000));

        viewModel.sendAttachment("bob", file, "image/png");
        awaitCondition(() -> sender.bindCalls == 1 && viewModel.getMessages("bob").size() == 1);

        assertEquals(0, sender.sendCalls);
        assertEquals(1, sender.initCalls);
        assertTrue(sender.chunkCalls > 0);
        assertEquals(1, sender.completeCalls);
        assertEquals(1, sender.bindCalls);
        assertEquals(1, sender.sendWithResultCalls);
        assertEquals(AttachmentConstants.CONTENT_TYPE_REFERENCE, sender.lastSendWithResultContentType);
        assertEquals(MessageType.IMAGE, viewModel.getMessages("bob").getFirst().type());
    }

    @Test
    void outgoing_chunked_file_shows_loading_then_swaps_to_file() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(8_192, 512, 256);
        sender.chunkUploadDelayMs = 35L;
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        Path file = tempDir.resolve("report.pdf");
        Files.write(file, new byte[2_048]);

        viewModel.sendAttachment("bob", file, "application/pdf");

        awaitCondition(() -> !viewModel.getMessages("bob").isEmpty());
        MessageVM first = viewModel.getMessages("bob").getFirst();
        assertEquals(MessageType.FILE, first.type());
        assertTrue(first.isLoading());
        assertNull(first.localPath());

        awaitCondition(() -> {
            MessageVM message = viewModel.getMessages("bob").getFirst();
            return message.type() == MessageType.FILE && !message.isLoading() && message.localPath() != null;
        });

        MessageVM resolved = viewModel.getMessages("bob").getFirst();
        assertFalse(resolved.isLoading());
        assertEquals(MessageType.FILE, resolved.type());
        assertNotNull(resolved.localPath());
        assertEquals("report.pdf", resolved.fileName());
    }

    @Test
    void outgoing_chunked_image_shows_loading_then_swaps_to_image() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(8_192, 512, 256);
        sender.chunkUploadDelayMs = 35L;
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        Path file = tempDir.resolve("photo.png");
        Files.write(file, pseudoPngBytes(2_048));

        viewModel.sendAttachment("bob", file, "image/png");

        awaitCondition(() -> !viewModel.getMessages("bob").isEmpty());
        MessageVM first = viewModel.getMessages("bob").getFirst();
        assertEquals(MessageType.IMAGE, first.type());
        assertTrue(first.isLoading());
        assertNull(first.content());

        awaitCondition(() -> {
            MessageVM message = viewModel.getMessages("bob").getFirst();
            return message.type() == MessageType.IMAGE && !message.isLoading() && message.content() != null;
        });

        MessageVM resolved = viewModel.getMessages("bob").getFirst();
        assertFalse(resolved.isLoading());
        assertEquals(MessageType.IMAGE, resolved.type());
        assertNotNull(resolved.content());
        assertEquals("photo.png", resolved.fileName());
    }

    @Test
    void reference_message_downloads_decrypts_and_renders() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(8_192, 512, 256);

        StubReceiver receiver = new StubReceiver();
        receiver.detachedDecryptResult = "pdf-content".getBytes(StandardCharsets.UTF_8);

        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        byte[] fakeEncryptedBlob = buildEncryptedMessageJsonBytes();
        sender.downloadResponse = new MessageSender.AttachmentDownload(
                "att-1",
                AttachmentConstants.CONTENT_TYPE_ENCRYPTED_BLOB,
                fakeEncryptedBlob.length,
                2,
                fakeEncryptedBlob);

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
    void incoming_inline_image_notifies_loading_then_swaps_to_image() throws Exception {
        RecordingSender sender = new RecordingSender();
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);
        List<MessageVM> incomingNotifications = new CopyOnWriteArrayList<>();
        viewModel.addIncomingMessageListener((senderId, message) -> incomingNotifications.add(message));

        receiver.emitMessage(
                inlinePayloadBytes("small-photo.png", "image/png", pseudoPngBytes(256)),
                "alice",
                AttachmentConstants.CONTENT_TYPE_INLINE,
                Instant.now().toEpochMilli(),
                "env-inline-1");

        awaitCondition(() -> !incomingNotifications.isEmpty());
        MessageVM notified = incomingNotifications.getFirst();
        assertEquals(MessageType.IMAGE, notified.type());
        assertTrue(notified.isLoading());
        assertNull(notified.content());

        awaitCondition(() -> {
            MessageVM first = viewModel.getMessages("alice").getFirst();
            return first.type() == MessageType.IMAGE && !first.isLoading() && first.content() != null;
        });

        MessageVM resolved = viewModel.getMessages("alice").getFirst();
        assertFalse(resolved.isLoading());
        assertEquals("small-photo.png", resolved.fileName());
    }

    @Test
    void chunked_image_reference_shows_loading_then_swaps_to_image() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(8_192, 512, 256);
        sender.downloadDelayMs = 120L;

        StubReceiver receiver = new StubReceiver();
        receiver.detachedDecryptResult = pseudoPngBytes(64);
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        byte[] fakeEncryptedBlob = buildEncryptedMessageJsonBytes();
        sender.downloadResponse = new MessageSender.AttachmentDownload(
                "att-img-1",
                AttachmentConstants.CONTENT_TYPE_ENCRYPTED_BLOB,
                fakeEncryptedBlob.length,
                2,
                fakeEncryptedBlob);

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
        assertEquals("photo.png", resolved.fileName());
    }

    @Test
    void declared_image_payload_with_webp_signature_renders_as_file() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(1_024, 512, 256);
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        Path file = tempDir.resolve("cloud.png");
        Files.write(file, pseudoWebpBytes(128));

        viewModel.sendAttachment("bob", file, "image/png");
        awaitCondition(() -> {
            if (sender.sendWithResultCalls != 1 || viewModel.getMessages("bob").isEmpty()) {
                return false;
            }
            MessageVM first = viewModel.getMessages("bob").getFirst();
            return first.type() == MessageType.FILE && !first.isLoading() && first.localPath() != null;
        });

        MessageVM message = viewModel.getMessages("bob").getFirst();
        assertEquals(MessageType.FILE, message.type());
        assertNotNull(message.localPath());
        assertEquals("cloud.png", message.fileName());
    }

    @Test
    void declared_image_payload_with_webp_signature_emits_image_fallback_notice() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(1_024, 512, 256);
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);
        List<MessagesViewModel.ImagePreviewFallbackNotice> notices = new CopyOnWriteArrayList<>();
        viewModel.addImagePreviewFallbackListener(notices::add);

        Path file = tempDir.resolve("cloud.png");
        Files.write(file, pseudoWebpBytes(128));

        viewModel.sendAttachment("bob", file, "image/png");
        awaitCondition(() -> sender.sendWithResultCalls == 1 && notices.size() == 1);

        MessagesViewModel.ImagePreviewFallbackNotice notice = notices.getFirst();
        assertEquals("bob", notice.contactId());
        assertTrue(notice.outgoing());
        assertEquals("cloud.png", notice.fileName());
        assertEquals("image/png", notice.declaredMediaType());
        assertEquals("webp", notice.detectedPayloadSignature());
    }

    @Test
    void renderable_image_payload_does_not_emit_image_fallback_notice() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(1_024, 512, 256);
        StubReceiver receiver = new StubReceiver();
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);
        List<MessagesViewModel.ImagePreviewFallbackNotice> notices = new CopyOnWriteArrayList<>();
        viewModel.addImagePreviewFallbackListener(notices::add);

        Path file = tempDir.resolve("renderable.png");
        Files.write(file, pseudoPngBytes(256));

        viewModel.sendAttachment("bob", file, "image/png");
        awaitCondition(() -> {
            if (sender.sendWithResultCalls != 1 || viewModel.getMessages("bob").isEmpty()) {
                return false;
            }
            MessageVM first = viewModel.getMessages("bob").getFirst();
            return first.type() == MessageType.IMAGE && !first.isLoading() && first.content() != null;
        });

        assertTrue(notices.isEmpty());
    }

    @Test
    void chunked_file_reference_shows_loading_then_swaps_to_file() throws Exception {
        RecordingSender sender = new RecordingSender();
        sender.policy = policy(8_192, 512, 256);
        sender.downloadDelayMs = 120L;

        StubReceiver receiver = new StubReceiver();
        receiver.detachedDecryptResult = "pdf-content".getBytes(StandardCharsets.UTF_8);
        MessagesViewModel viewModel = new MessagesViewModel(sender, receiver);

        byte[] fakeEncryptedBlob = buildEncryptedMessageJsonBytes();
        sender.downloadResponse = new MessageSender.AttachmentDownload(
                "att-file-1",
                AttachmentConstants.CONTENT_TYPE_ENCRYPTED_BLOB,
                fakeEncryptedBlob.length,
                2,
                fakeEncryptedBlob);

        AttachmentReferencePayload ref = new AttachmentReferencePayload();
        ref.setAttachmentId("att-file-1");
        ref.setFileName("report.pdf");
        ref.setMediaType("application/pdf");
        ref.setSizeBytes(11);

        receiver.emitMessage(
                AttachmentPayloadCodec.toReferenceJson(ref).getBytes(StandardCharsets.UTF_8),
                "alice",
                AttachmentConstants.CONTENT_TYPE_REFERENCE,
                Instant.now().toEpochMilli(),
                "env-3");

        assertEquals(1, viewModel.getMessages("alice").size());
        MessageVM first = viewModel.getMessages("alice").getFirst();
        assertEquals(MessageType.FILE, first.type());
        assertTrue(first.isLoading());
        assertNull(first.localPath());

        awaitCondition(() -> {
            MessageVM message = viewModel.getMessages("alice").getFirst();
            return message.type() == MessageType.FILE && !message.isLoading() && message.localPath() != null;
        });

        MessageVM resolved = viewModel.getMessages("alice").getFirst();
        assertEquals(MessageType.FILE, resolved.type());
        assertFalse(resolved.isLoading());
        assertNotNull(resolved.localPath());
        assertEquals("report.pdf", resolved.fileName());
    }

    private static MessagingPolicyResponse policy(long max, long inlineMax, int chunk) {
        return MessagingPolicyResponse.success(
                max,
                inlineMax,
                chunk,
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

    private static byte[] inlinePayloadBytes(String fileName, String mediaType, byte[] fileBytes) {
        AttachmentInlinePayload payload = new AttachmentInlinePayload();
        payload.setFileName(fileName);
        payload.setMediaType(mediaType);
        payload.setSizeBytes(fileBytes.length);
        payload.setDataB64(Base64.getEncoder().encodeToString(fileBytes));
        return AttachmentPayloadCodec.toInlineJson(payload).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] pseudoPngBytes(int size) {
        int length = Math.max(size, 8);
        byte[] bytes = new byte[length];
        bytes[0] = (byte) 0x89;
        bytes[1] = 0x50;
        bytes[2] = 0x4E;
        bytes[3] = 0x47;
        bytes[4] = 0x0D;
        bytes[5] = 0x0A;
        bytes[6] = 0x1A;
        bytes[7] = 0x0A;
        return bytes;
    }

    private static byte[] pseudoWebpBytes(int size) {
        int length = Math.max(size, 12);
        byte[] bytes = new byte[length];
        bytes[0] = 'R';
        bytes[1] = 'I';
        bytes[2] = 'F';
        bytes[3] = 'F';
        bytes[8] = 'W';
        bytes[9] = 'E';
        bytes[10] = 'B';
        bytes[11] = 'P';
        return bytes;
    }

    private static byte[] realPngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (x * 37 + y * 11) & 0xFF;
                int g = (x * 13 + y * 29) & 0xFF;
                int b = (x * 17 + y * 19) & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(10_000_000L); // 10ms
            if (Thread.interrupted()) {
                fail("Interrupted while waiting for condition");
            }
        }
        fail("Condition not met within timeout");
    }

    private static final class RecordingSender implements MessageSender {
        private MessagingPolicyResponse policy;
        private MessageSender.AttachmentDownload downloadResponse;
        private long downloadDelayMs;
        private long chunkUploadDelayMs;
        private long sendDelayMs;

        private int sendCalls;
        private int sendWithResultCalls;
        private int initCalls;
        private int chunkCalls;
        private int completeCalls;
        private int bindCalls;

        private String lastSendWithResultContentType;
        private byte[] lastSentPayload;

        @Override
        public void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds) {
            sleepQuietly(sendDelayMs);
            sendCalls++;
            lastSentPayload = payload;
        }

        @Override
        public SendResult sendMessageWithResult(byte[] payload, String recipientId, String contentType,
                long ttlSeconds) {
            sleepQuietly(sendDelayMs);
            sendWithResultCalls++;
            lastSendWithResultContentType = contentType;
            lastSentPayload = payload;
            return new SendResult("env-123", System.currentTimeMillis() + 60_000);
        }

        @Override
        public EncryptedMessage encryptMessage(byte[] payload, String recipientId, String contentType,
                long ttlSeconds) {
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
            return AttachmentInitResponse.success("att-1", policy.getAttachmentChunkBytes(),
                    System.currentTimeMillis() + 60_000);
        }

        @Override
        public AttachmentChunkResponse uploadAttachmentChunk(String attachmentId, int chunkIndex, byte[] chunkBytes) {
            sleepQuietly(chunkUploadDelayMs);
            chunkCalls++;
            return AttachmentChunkResponse.success(attachmentId, chunkIndex, true);
        }

        @Override
        public AttachmentCompleteResponse completeAttachmentUpload(String attachmentId,
                AttachmentCompleteRequest request) {
            completeCalls++;
            return AttachmentCompleteResponse.success(attachmentId, request.getExpectedChunks(),
                    request.getEncryptedSizeBytes(), "COMPLETE");
        }

        @Override
        public AttachmentBindResponse bindAttachmentUpload(String attachmentId, AttachmentBindRequest request) {
            bindCalls++;
            return AttachmentBindResponse.success(attachmentId, request.getEnvelopeId(),
                    System.currentTimeMillis() + 60_000);
        }

        @Override
        public MessageSender.AttachmentDownload downloadAttachment(String attachmentId) {
            sleepQuietly(downloadDelayMs);
            return downloadResponse;
        }

        private void sleepQuietly(long delayMs) {
            if (delayMs <= 0) {
                return;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(delayMs));
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(new InterruptedException());
            }
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
