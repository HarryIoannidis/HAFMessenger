package com.haf.client.viewmodels;

import com.haf.client.models.MessageType;
import com.haf.client.models.MessageVM;
import com.haf.client.network.MessageReceiver;
import com.haf.client.network.MessageSender;
import com.haf.shared.constants.AttachmentConstants;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.requests.AttachmentBindRequest;
import com.haf.shared.requests.AttachmentChunkRequest;
import com.haf.shared.requests.AttachmentCompleteRequest;
import com.haf.shared.responses.AttachmentDownloadResponse;
import com.haf.shared.requests.AttachmentInitRequest;
import com.haf.shared.dto.AttachmentInlinePayload;
import com.haf.shared.dto.AttachmentReferencePayload;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.responses.MessagingPolicyResponse;
import com.haf.shared.utils.AttachmentPayloadCodec;
import com.haf.shared.utils.JsonCodec;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ViewModel for the chat screen.
 */
public class MessageViewModel {

    @FunctionalInterface
    public interface PresenceListener {
        void onPresenceUpdate(String userId, boolean active);
    }

    @FunctionalInterface
    public interface IncomingMessageListener {
        void onIncomingMessage(String senderId, MessageVM message);
    }

    private final MessageSender messageSender;
    private final MessageReceiver messageReceiver;

    private final StringProperty status = new SimpleStringProperty("Ready");
    private final List<PresenceListener> presenceListeners = new CopyOnWriteArrayList<>();
    private final List<IncomingMessageListener> incomingMessageListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, ObservableList<MessageVM>> messagesByContact = new ConcurrentHashMap<>();

    private final Object policyLock = new Object();
    private volatile MessagingPolicyResponse cachedPolicy;
    private static volatile boolean fxToolkitUnavailable;

    /**
     * Creates a MessageViewModel.
     */
    public MessageViewModel(MessageSender messageSender, MessageReceiver messageReceiver) {
        this.messageSender = messageSender;
        this.messageReceiver = messageReceiver;

        messageReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs,
                    String envelopeId) {
                String contactId = normalizeContactId(senderId);
                try {
                    MessageVM vm = decodeIncoming(plaintext, contactId, contentType, timestampEpochMs);
                    runOnUiThread(() -> {
                        getMessages(contactId).add(vm);
                        notifyIncomingMessageListeners(contactId, vm);
                        status.set("Message received from " + contactId);
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> status.set("Failed to render message: " + ex.getMessage()));
                }
            }

            @Override
            public void onError(Throwable error) {
                runOnUiThread(() -> status.set("Error: " + error.getMessage()));
            }

            @Override
            public void onPresenceUpdate(String userId, boolean active) {
                runOnUiThread(() -> notifyPresenceListeners(userId, active));
            }
        });
    }

    /**
     * Sends a plain-text message and adds it locally as outgoing bubble.
     */
    public void sendTextMessage(String recipientId, String text) {
        String contactId = normalizeContactId(recipientId);
        try {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            messageSender.sendMessage(payload, contactId, "text/plain", MessageHeader.MAX_TTL_SECONDS);

            MessageVM vm = MessageVM.outgoingText(text, LocalDateTime.now());
            runOnUiThread(() -> {
                getMessages(contactId).add(vm);
                status.set("Message sent to " + contactId);
            });
        } catch (Exception e) {
            runOnUiThread(() -> status.set("Failed to send: " + e.getMessage()));
        }
    }

    /**
     * Sends one attachment using inline or chunked transport based on size.
     */
    public void sendAttachment(String recipientId, Path filePath, String mediaTypeHint) {
        String contactId = normalizeContactId(recipientId);
        try {
            if (filePath == null || !Files.exists(filePath)) {
                throw new IllegalArgumentException("Attachment file is missing");
            }

            byte[] fileBytes = Files.readAllBytes(filePath);
            if (fileBytes.length == 0) {
                throw new IllegalArgumentException("Attachment file is empty");
            }

            MessagingPolicyResponse policy = getMessagingPolicy();
            String mediaType = normalizeMediaType(mediaTypeHint, filePath);
            validateAgainstPolicy(mediaType, fileBytes.length, policy);
            String fileName = sanitizeFileName(filePath.getFileName().toString());

            if (fileBytes.length <= policy.getAttachmentInlineMaxBytes()) {
                sendInlineAttachment(contactId, fileName, mediaType, fileBytes);
            } else {
                sendChunkedAttachment(contactId, fileName, mediaType, fileBytes, policy);
            }

            MessageVM outgoing = toAttachmentVm(true, fileBytes, mediaType, fileName, LocalDateTime.now());
            runOnUiThread(() -> {
                getMessages(contactId).add(outgoing);
                status.set("Attachment sent to " + contactId);
            });
        } catch (Exception ex) {
            runOnUiThread(() -> status.set("Failed to send attachment: " + ex.getMessage()));
        }
    }

    /**
     * Starts the message receiver.
     */
    public void startReceiving() {
        try {
            messageReceiver.start();
            runOnUiThread(() -> status.set("Receiving messages…"));
        } catch (Exception e) {
            runOnUiThread(() -> status.set("Failed to start receiving: " + e.getMessage()));
        }
    }

    /**
     * Stops the message receiver.
     */
    public void stopReceiving() {
        messageReceiver.stop();
        runOnUiThread(() -> status.set("Stopped receiving messages"));
    }

    /**
     * Acknowledges all pending messages received from a specific sender.
     */
    public void acknowledgeMessagesFrom(String senderId) {
        messageReceiver.acknowledgeEnvelopes(normalizeContactId(senderId));
    }

    /**
     * Observable list of chat messages for a specific contact.
     */
    public ObservableList<MessageVM> getMessages(String contactId) {
        String normalizedContactId = normalizeContactId(contactId);
        return messagesByContact.computeIfAbsent(normalizedContactId, ignored -> FXCollections.observableArrayList());
    }

    /**
     * Status string property.
     */
    public StringProperty statusProperty() {
        return status;
    }

    public void addPresenceListener(PresenceListener listener) {
        if (listener != null) {
            presenceListeners.add(listener);
        }
    }

    public void removePresenceListener(PresenceListener listener) {
        if (listener != null) {
            presenceListeners.remove(listener);
        }
    }

    public void addIncomingMessageListener(IncomingMessageListener listener) {
        if (listener != null) {
            incomingMessageListeners.add(listener);
        }
    }

    public void removeIncomingMessageListener(IncomingMessageListener listener) {
        if (listener != null) {
            incomingMessageListeners.remove(listener);
        }
    }

    private void notifyPresenceListeners(String userId, boolean active) {
        for (PresenceListener listener : presenceListeners) {
            try {
                listener.onPresenceUpdate(userId, active);
            } catch (Exception ignored) {
                // A bad listener must not break dispatching for others.
            }
        }
    }

    private void notifyIncomingMessageListeners(String senderId, MessageVM message) {
        for (IncomingMessageListener listener : incomingMessageListeners) {
            try {
                listener.onIncomingMessage(senderId, message);
            } catch (Exception ignored) {
                // A bad listener must not break dispatching for others.
            }
        }
    }

    private static void runOnUiThread(Runnable action) {
        if (fxToolkitUnavailable) {
            action.run();
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
                return;
            }
        } catch (Throwable ex) {
            fxToolkitUnavailable = true;
            action.run();
            return;
        }

        try {
            Platform.runLater(action);
        } catch (Throwable ex) {
            fxToolkitUnavailable = true;
            action.run();
        }
    }

    private static String normalizeContactId(String contactId) {
        return contactId == null ? "" : contactId.trim();
    }

    private void sendInlineAttachment(String recipientId,
            String fileName,
            String mediaType,
            byte[] fileBytes) throws Exception {
        AttachmentInlinePayload inlinePayload = new AttachmentInlinePayload();
        inlinePayload.setFileName(fileName);
        inlinePayload.setMediaType(mediaType);
        inlinePayload.setSizeBytes(fileBytes.length);
        inlinePayload.setDataB64(Base64.getEncoder().encodeToString(fileBytes));

        String inlineJson = AttachmentPayloadCodec.toInlineJson(inlinePayload);
        messageSender.sendMessage(
                inlineJson.getBytes(StandardCharsets.UTF_8),
                recipientId,
                AttachmentConstants.CONTENT_TYPE_INLINE,
                MessageHeader.MAX_TTL_SECONDS);
    }

    private void sendChunkedAttachment(String recipientId,
            String fileName,
            String mediaType,
            byte[] fileBytes,
            MessagingPolicyResponse policy) throws Exception {

        EncryptedMessage encryptedAttachment = messageSender.encryptMessage(
                fileBytes,
                recipientId,
                mediaType,
                MessageHeader.MAX_TTL_SECONDS);
        byte[] encryptedBlob = JsonCodec.toJson(encryptedAttachment).getBytes(StandardCharsets.UTF_8);

        int chunkBytes = policy.getAttachmentChunkBytes();
        int expectedChunks = (int) Math.ceil(encryptedBlob.length / (double) chunkBytes);

        AttachmentInitRequest initRequest = new AttachmentInitRequest();
        initRequest.setRecipientId(recipientId);
        initRequest.setContentType(AttachmentConstants.CONTENT_TYPE_ENCRYPTED_BLOB);
        initRequest.setPlaintextSizeBytes(fileBytes.length);
        initRequest.setEncryptedSizeBytes(encryptedBlob.length);
        initRequest.setExpectedChunks(expectedChunks);
        var initResponse = messageSender.initAttachmentUpload(initRequest);
        ensureNoError(initResponse.getError());
        String attachmentId = initResponse.getAttachmentId();
        if (attachmentId == null || attachmentId.isBlank()) {
            throw new IOException("Attachment init did not return attachmentId");
        }

        for (int chunkIndex = 0; chunkIndex < expectedChunks; chunkIndex++) {
            int from = chunkIndex * chunkBytes;
            int to = Math.min(encryptedBlob.length, from + chunkBytes);
            byte[] chunk = java.util.Arrays.copyOfRange(encryptedBlob, from, to);

            AttachmentChunkRequest chunkRequest = new AttachmentChunkRequest();
            chunkRequest.setChunkIndex(chunkIndex);
            chunkRequest.setChunkDataB64(Base64.getEncoder().encodeToString(chunk));
            var chunkResponse = messageSender.uploadAttachmentChunk(attachmentId, chunkRequest);
            ensureNoError(chunkResponse.getError());
        }

        AttachmentCompleteRequest completeRequest = new AttachmentCompleteRequest();
        completeRequest.setExpectedChunks(expectedChunks);
        completeRequest.setEncryptedSizeBytes(encryptedBlob.length);
        var completeResponse = messageSender.completeAttachmentUpload(attachmentId, completeRequest);
        ensureNoError(completeResponse.getError());

        AttachmentReferencePayload referencePayload = new AttachmentReferencePayload();
        referencePayload.setAttachmentId(attachmentId);
        referencePayload.setFileName(fileName);
        referencePayload.setMediaType(mediaType);
        referencePayload.setSizeBytes(fileBytes.length);

        String refJson = AttachmentPayloadCodec.toReferenceJson(referencePayload);
        MessageSender.SendResult sendResult = messageSender.sendMessageWithResult(
                refJson.getBytes(StandardCharsets.UTF_8),
                recipientId,
                AttachmentConstants.CONTENT_TYPE_REFERENCE,
                MessageHeader.MAX_TTL_SECONDS);

        if (sendResult == null || sendResult.envelopeId() == null || sendResult.envelopeId().isBlank()) {
            throw new IOException("Attachment reference send did not return envelopeId");
        }

        AttachmentBindRequest bindRequest = new AttachmentBindRequest();
        bindRequest.setEnvelopeId(sendResult.envelopeId());
        var bindResponse = messageSender.bindAttachmentUpload(attachmentId, bindRequest);
        ensureNoError(bindResponse.getError());
    }

    private MessageVM decodeIncoming(byte[] plaintext,
            String senderId,
            String contentType,
            long timestampEpochMs) throws Exception {
        LocalDateTime timestamp = Instant.ofEpochMilli(timestampEpochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if (contentType == null) {
            contentType = "text/plain";
        }

        if (AttachmentConstants.CONTENT_TYPE_INLINE.equals(contentType)) {
            AttachmentInlinePayload payload = AttachmentPayloadCodec.fromInlineJson(plaintext);
            byte[] fileBytes = Base64.getDecoder().decode(payload.getDataB64());
            return toAttachmentVm(false, fileBytes, payload.getMediaType(), payload.getFileName(), timestamp);
        }

        if (AttachmentConstants.CONTENT_TYPE_REFERENCE.equals(contentType)) {
            AttachmentReferencePayload ref = AttachmentPayloadCodec.fromReferenceJson(plaintext);
            AttachmentDownloadResponse downloadResponse = messageSender.downloadAttachment(ref.getAttachmentId());
            ensureNoError(downloadResponse.getError());
            if (downloadResponse.getEncryptedBlobB64() == null || downloadResponse.getEncryptedBlobB64().isBlank()) {
                throw new IOException("Attachment download response is empty");
            }

            byte[] encryptedBlob = Base64.getDecoder().decode(downloadResponse.getEncryptedBlobB64());
            EncryptedMessage encryptedMessage = JsonCodec.fromJson(
                    new String(encryptedBlob, StandardCharsets.UTF_8),
                    EncryptedMessage.class);
            byte[] fileBytes = messageReceiver.decryptDetachedMessage(encryptedMessage);
            return toAttachmentVm(false, fileBytes, ref.getMediaType(), ref.getFileName(), timestamp);
        }

        if (contentType.startsWith("text/")) {
            String text = new String(plaintext, StandardCharsets.UTF_8);
            return new MessageVM(false, MessageType.TEXT, text, null, null, null, timestamp);
        }

        if (contentType.startsWith("image/")) {
            String localPath = writeTempFile(plaintext, "haf-img-", extensionFor(contentType));
            return new MessageVM(false, MessageType.IMAGE, localPath, null, null, null, timestamp);
        }

        String ext = extensionFor(contentType);
        String fileName = senderId + "-attachment" + ext;
        String fileSize = formatSize(plaintext.length);
        String localPath = writeTempFile(plaintext, "haf-file-", ext);
        return new MessageVM(false, MessageType.FILE, null, localPath, fileName, fileSize, timestamp);
    }

    private MessagingPolicyResponse getMessagingPolicy() throws IOException {
        MessagingPolicyResponse existing = cachedPolicy;
        if (existing != null) {
            return existing;
        }

        synchronized (policyLock) {
            if (cachedPolicy == null) {
                MessagingPolicyResponse fetched = messageSender.fetchMessagingPolicy();
                if (fetched == null) {
                    throw new IOException("Messaging policy response is empty");
                }
                ensureNoError(fetched.getError());
                cachedPolicy = fetched;
            }
            return cachedPolicy;
        }
    }

    private void validateAgainstPolicy(String mediaType, long sizeBytes, MessagingPolicyResponse policy) {
        if (sizeBytes > policy.getAttachmentMaxBytes()) {
            throw new IllegalArgumentException("Attachment exceeds maximum allowed size");
        }

        Set<String> allowed = new HashSet<>();
        if (policy.getAttachmentAllowedTypes() == null || policy.getAttachmentAllowedTypes().isEmpty()) {
            throw new IllegalArgumentException("Attachment policy allowlist is empty");
        }
        for (String value : policy.getAttachmentAllowedTypes()) {
            String normalized = AttachmentConstants.normalizeMimeType(value);
            if (normalized != null) {
                allowed.add(normalized);
            }
        }
        if (!allowed.contains(mediaType)) {
            throw new IllegalArgumentException("Attachment type is not allowed: " + mediaType);
        }
    }

    private static String normalizeMediaType(String hint, Path filePath) {
        String normalized = AttachmentConstants.normalizeMimeType(hint);
        if (normalized != null) {
            return normalized;
        }

        try {
            normalized = AttachmentConstants.normalizeMimeType(Files.probeContentType(filePath));
            if (normalized != null) {
                return normalized;
            }
        } catch (IOException ignored) {
            // Fallback to extension mapping below.
        }

        String fileName = filePath.getFileName() == null ? "" : filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (fileName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (fileName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }

        throw new IllegalArgumentException("Unable to detect attachment MIME type");
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Attachment filename is invalid");
        }
        if (fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Attachment filename must not contain path separators");
        }
        return fileName;
    }

    private static void ensureNoError(String error) throws IOException {
        if (error != null && !error.isBlank()) {
            throw new IOException(error);
        }
    }

    private static MessageVM toAttachmentVm(boolean outgoing,
            byte[] fileBytes,
            String mediaType,
            String fileName,
            LocalDateTime timestamp) {
        if (mediaType != null && mediaType.startsWith("image/")) {
            String localPath = writeTempFile(fileBytes, "haf-img-", extensionFor(mediaType));
            return new MessageVM(outgoing, MessageType.IMAGE, localPath, null, null, null, timestamp);
        }

        String ext = extensionFor(mediaType);
        String localPath = writeTempFile(fileBytes, "haf-file-", ext);
        return new MessageVM(outgoing, MessageType.FILE, null, localPath, fileName, formatSize(fileBytes.length), timestamp);
    }

    private static String writeTempFile(byte[] data, String prefix, String suffix) {
        try {
            Path tmp = Files.createTempFile(prefix, suffix);
            Files.write(tmp, data);
            tmp.toFile().deleteOnExit();
            return tmp.toUri().toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx";
            case "application/zip" -> ".zip";
            case "application/octet-stream" -> ".bin";
            default -> ".dat";
        };
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
