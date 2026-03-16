package com.haf.client.viewmodels;

import com.haf.client.models.MessageVM;
import com.haf.client.models.MessageType;
import com.haf.client.network.MessageReceiver;
import com.haf.client.network.MessageSender;
import com.haf.shared.constants.MessageHeader;
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ViewModel for the chat screen.
 * 
 * Exposes an {@link ObservableList} of {@link MessageVM} records that the
 * controller feeds straight into {@code MessageBubbleFactory.create()} —
 * no raw string manipulation lives here.
 */
public class MessageViewModel {

    @FunctionalInterface
    public interface PresenceListener {
        void onPresenceUpdate(String userId, boolean active);
    }

    private final MessageSender messageSender;
    private final MessageReceiver messageReceiver;

    private final StringProperty status = new SimpleStringProperty("Ready");
    private final List<PresenceListener> presenceListeners = new CopyOnWriteArrayList<>();

    // Store messages by contact ID (sender or recipient)
    private final ConcurrentMap<String, ObservableList<MessageVM>> messagesByContact = new ConcurrentHashMap<>();

    /**
     * Creates a MessageViewModel.
     *
     * @param messageSender   the outbound message sender
     * @param messageReceiver the inbound message receiver
     */
    public MessageViewModel(MessageSender messageSender, MessageReceiver messageReceiver) {
        this.messageSender = messageSender;
        this.messageReceiver = messageReceiver;

        messageReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs, String envelopeId) {
                String contactId = normalizeContactId(senderId);
                MessageVM vm = decodeIncoming(plaintext, contactId, contentType, timestampEpochMs);
                runOnUiThread(() -> {
                    getMessages(contactId).add(vm);
                    status.set("Message received from " + contactId);
                });
            }

            @Override
            public void onError(Throwable error) {
                // Errors are surfaced through the status property only —
                // they do not appear as chat bubbles.
                runOnUiThread(() -> status.set("Error: " + error.getMessage()));
            }

            @Override
            public void onPresenceUpdate(String userId, boolean active) {
                runOnUiThread(() -> notifyPresenceListeners(userId, active));
            }
        });
    }

    /**
     * Sends a plain-text message and immediately adds it to the local list
     * as an outgoing bubble.
     *
     * @param recipientId the recipient's identifier
     * @param text        the text to send
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
            e.printStackTrace();
            runOnUiThread(() -> status.set("Failed to send: " + e.getMessage()));
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
     * Call this when the user opens the chat with that sender so the
     * server marks those envelopes as delivered.
     *
     * @param senderId the sender whose messages should be acknowledged
     */
    public void acknowledgeMessagesFrom(String senderId) {
        messageReceiver.acknowledgeEnvelopes(normalizeContactId(senderId));
    }

    /**
     * Observable list of chat messages for a specific contact, ordered
     * oldest-first.
     * Bind the chat scroll-pane directly to this list.
     *
     * @param contactId the contact's identifier
     * @return the observable message list
     */
    public ObservableList<MessageVM> getMessages(String contactId) {
        String normalizedContactId = normalizeContactId(contactId);
        return messagesByContact.computeIfAbsent(normalizedContactId, ignored -> FXCollections.observableArrayList());
    }

    /**
     * Status string property (last operation result / error summary).
     *
     * @return the status property
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

    private void notifyPresenceListeners(String userId, boolean active) {
        for (PresenceListener listener : presenceListeners) {
            try {
                listener.onPresenceUpdate(userId, active);
            } catch (Exception ignored) {
                // A bad listener must not break dispatching for others.
            }
        }
    }

    private static void runOnUiThread(Runnable action) {
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
                return;
            }
        } catch (RuntimeException ex) {
            // JavaFX toolkit may not be initialized in headless/unit-test runs.
            action.run();
            return;
        }

        try {
            Platform.runLater(action);
        } catch (RuntimeException ex) {
            action.run();
        }
    }

    private static String normalizeContactId(String contactId) {
        return contactId == null ? "" : contactId.trim();
    }

    /**
     * Converts raw network bytes into the correct {@link MessageVM} variant
     * based on the MIME content-type.
     */
    private static MessageVM decodeIncoming(byte[] plaintext, String senderId, String contentType,
            long timestampEpochMs) {
        LocalDateTime timestamp = Instant.ofEpochMilli(timestampEpochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        if (contentType == null) {
            contentType = "text/plain";
        }

        if (contentType.startsWith("text/")) {
            String text = new String(plaintext, StandardCharsets.UTF_8);
            return new MessageVM(false, MessageType.TEXT, text, null, null, null, timestamp);
        }

        if (contentType.startsWith("image/")) {
            // Write image bytes to a temp file and expose a file:// path so
            // ImageView can load without holding the raw bytes in memory.
            String localPath = writeTempFile(plaintext, "haf-img-", extensionFor(contentType));
            return new MessageVM(false, MessageType.IMAGE, localPath, null, null, null, timestamp);
        }

        // Everything else is treated as a generic file attachment.
        String ext = extensionFor(contentType);
        String fileName = senderId + "-attachment" + ext;
        String fileSize = formatSize(plaintext.length);
        String localPath = writeTempFile(plaintext, "haf-file-", ext);
        return new MessageVM(false, MessageType.FILE, null, localPath, fileName, fileSize, timestamp);
    }

    /**
     * Writes bytes to a temporary file and returns a {@code file://} URI string,
     * or {@code null} if writing fails.
     */
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

    /** Returns a dot-prefixed file extension for common MIME types. */
    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "application/zip" -> ".zip";
            case "application/octet-stream" -> ".bin";
            default -> ".dat";
        };
    }

    /** Formats a byte count as a human-readable string (B / KB / MB). */
    private static String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
