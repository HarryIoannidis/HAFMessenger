package com.haf.client.viewmodels;

import com.haf.client.exceptions.UiDispatchException;
import com.haf.client.exceptions.HttpCommunicationException;
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
import com.haf.shared.exceptions.MessageTamperedException;
import com.haf.shared.responses.MessagingPolicyResponse;
import com.haf.shared.utils.AttachmentPayloadCodec;
import com.haf.shared.utils.JsonCodec;
import com.haf.client.utils.RuntimeIssue;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * ViewModel for the chat screen.
 */
public class MessagesViewModel {

    @FunctionalInterface
    public interface PresenceListener {
        /**
         * Receives a presence state transition for a user currently relevant to the
         * chat screen.
         *
         * @param userId user id whose presence changed
         * @param active {@code true} when the user is online/active, {@code false}
         *               otherwise
         */
        void onPresenceUpdate(String userId, boolean active);
    }

    @FunctionalInterface
    public interface IncomingMessageListener {
        /**
         * Receives an incoming message after it has been materialized into a UI message
         * view-model.
         *
         * @param senderId normalized sender/contact id
         * @param message  rendered message model to display in UI
         */
        void onIncomingMessage(String senderId, MessageVM message);
    }

    @FunctionalInterface
    public interface ImagePreviewFallbackListener {
        /**
         * Receives an event when an image payload is intentionally rendered as a
         * generic file attachment because in-app preview is not supported/safe.
         *
         * @param notice fallback notice describing why preview was downgraded
         */
        void onImageFallback(ImagePreviewFallbackNotice notice);
    }

    /**
     * Immutable event emitted when an image-like payload is displayed as a file
     * bubble instead of an image preview.
     *
     * @param contactId                normalized contact id for the chat timeline
     * @param outgoing                 {@code true} for local/outgoing messages
     * @param fileName                 display file name shown in the file bubble
     * @param declaredMediaType        normalized media type declared by metadata
     * @param detectedPayloadSignature detected payload signature (`png`, `jpeg`,
     *                                 `gif`, `webp`, `unknown`)
     */
    public record ImagePreviewFallbackNotice(
            String contactId,
            boolean outgoing,
            String fileName,
            String declaredMediaType,
            String detectedPayloadSignature) {
    }

    private record DecodedMessage(MessageVM message, ImagePreviewFallbackNotice fallbackNotice) {
    }

    private final MessageSender messageSender;
    private final MessageReceiver messageReceiver;

    private final StringProperty status = new SimpleStringProperty("Ready");
    private final List<PresenceListener> presenceListeners = new CopyOnWriteArrayList<>();
    private final List<IncomingMessageListener> incomingMessageListeners = new CopyOnWriteArrayList<>();
    private final List<ImagePreviewFallbackListener> imagePreviewFallbackListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<RuntimeIssue>> runtimeIssueListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, ObservableList<MessageVM>> messagesByContact = new ConcurrentHashMap<>();

    private final Object policyLock = new Object();
    private volatile MessagingPolicyResponse cachedPolicy;
    private volatile boolean receivingStarted;
    private final AtomicReference<Runnable> lastFailedSendRetryAction = new AtomicReference<>();
    private static volatile boolean fxToolkitUnavailable;
    private static final int DEFAULT_WS_INBOUND_MESSAGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_WS_INBOUND_MESSAGE_BYTES = resolveWsInboundMessageBytes();
    private static final int INLINE_WIRE_HEADROOM_BYTES = 16 * 1024;
    private static final int INLINE_ESTIMATED_ENVELOPE_OVERHEAD_BYTES = 2048;
    private static final String SESSION_TAKEOVER_ISSUE_KEY = "messaging.session.takeover";
    private static final String SESSION_TAKEOVER_TITLE = "Logged out";
    private static final String SESSION_TAKEOVER_MESSAGE =
            "You were logged out because this account was signed in on another device.";

    /**
     * Creates a MessagesViewModel.
     *
     * @param messageSender   sender used for outbound messages and attachment API
     *                        calls
     * @param messageReceiver receiver used for inbound messages and presence
     *                        updates
     */
    public MessagesViewModel(MessageSender messageSender, MessageReceiver messageReceiver) {
        this.messageSender = messageSender;
        this.messageReceiver = messageReceiver;

        messageReceiver.setMessageListener(new ViewModelMessageListener());
    }

    /**
     * Receives transport callbacks and delegates them to focused handlers.
     */
    private final class ViewModelMessageListener implements MessageReceiver.MessageListener {
        /**
         * Handles a decrypted inbound envelope and converts it into a message item for
         * the sender timeline.
         *
         * @param plaintext        decrypted payload bytes
         * @param senderId         backend sender id
         * @param contentType      payload MIME/content type
         * @param timestampEpochMs server timestamp in epoch milliseconds
         * @param envelopeId       backend envelope id
         */
        @Override
        public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs,
                String envelopeId) {
            handleIncomingEnvelope(plaintext, senderId, contentType, timestampEpochMs);
        }

        /**
         * Surfaces receiver-side pipeline failures to the status property on the UI
         * thread.
         *
         * @param error failure raised by the receiver
         */
        @Override
        public void onError(Throwable error) {
            handleReceiverError(error);
        }

        /**
         * Forwards incoming presence updates to registered presence listeners on the UI
         * thread.
         *
         * @param userId user whose presence changed
         * @param active latest activity flag
         */
        @Override
        public void onPresenceUpdate(String userId, boolean active) {
            runOnUiThread(() -> notifyPresenceListeners(userId, active));
        }

        /**
         * Handles a decrypted inbound envelope and converts it into a message item for
         * the sender timeline.
         *
         * @param plaintext        decrypted payload bytes
         * @param senderId         backend sender id
         * @param contentType      payload MIME/content type
         * @param timestampEpochMs server timestamp in epoch milliseconds
         */
        private void handleIncomingEnvelope(byte[] plaintext,
                String senderId,
                String contentType,
                long timestampEpochMs) {
            String contactId = normalizeContactId(senderId);
            try {
                routeIncomingEnvelope(plaintext, contactId, contentType, timestampEpochMs);
            } catch (Exception ex) {
                handleIncomingRenderFailure(ex);
            }
        }

        /**
         * Routes incoming payloads through the transport-specific attachment handling
         * path before falling back to ordinary payload decoding.
         *
         * @param plaintext        decrypted payload bytes
         * @param contactId        normalized sender/contact id
         * @param contentType      payload MIME/content type
         * @param timestampEpochMs server timestamp in epoch milliseconds
         * @throws Exception when payload decoding or attachment resolution setup fails
         */
        private void routeIncomingEnvelope(byte[] plaintext,
                String contactId,
                String contentType,
                long timestampEpochMs) throws Exception {
            if (AttachmentConstants.CONTENT_TYPE_REFERENCE.equals(contentType)) {
                receiveReferencePayload(plaintext, contactId, timestampEpochMs);
                return;
            }
            if (AttachmentConstants.CONTENT_TYPE_INLINE.equals(contentType)) {
                receiveInlinePayload(plaintext, contactId, timestampEpochMs);
                return;
            }

            DecodedMessage decoded = decodeIncoming(plaintext, contactId, contentType, timestampEpochMs);
            appendDecodedIncomingMessage(contactId, decoded);
        }

        /**
         * Starts the existing reference-attachment loading flow for incoming chunked
         * attachments.
         *
         * @param plaintext        decrypted reference payload bytes
         * @param contactId        normalized sender/contact id
         * @param timestampEpochMs server timestamp in epoch milliseconds
         */
        private void receiveReferencePayload(byte[] plaintext, String contactId, long timestampEpochMs) {
            AttachmentReferencePayload reference = AttachmentPayloadCodec.fromReferenceJson(plaintext);
            receiveChunkedReferenceAsync(contactId, reference, timestampEpochMs);
        }

        /**
         * Starts inline-image placeholder handling or directly appends non-image inline
         * attachments.
         *
         * @param plaintext        decrypted inline payload bytes
         * @param contactId        normalized sender/contact id
         * @param timestampEpochMs server timestamp in epoch milliseconds
         */
        private void receiveInlinePayload(byte[] plaintext, String contactId, long timestampEpochMs) {
            AttachmentInlinePayload inlinePayload = AttachmentPayloadCodec.fromInlineJson(plaintext);
            if (shouldShowInlineLoadingPlaceholder(inlinePayload.getMediaType())) {
                receiveInlineAttachmentAsync(contactId, inlinePayload, timestampEpochMs);
                return;
            }

            DecodedMessage decoded = decodeInlineAttachment(contactId, inlinePayload,
                    toLocalTimestamp(timestampEpochMs));
            appendDecodedIncomingMessage(contactId, decoded);
        }

        /**
         * Appends a decoded incoming message to the timeline on the UI thread.
         *
         * @param contactId normalized sender/contact id
         * @param decoded   decoded message and optional fallback notice
         */
        private void appendDecodedIncomingMessage(String contactId, DecodedMessage decoded) {
            runOnUiThread(() -> {
                getMessages(contactId).add(decoded.message());
                if (decoded.fallbackNotice() != null) {
                    notifyImagePreviewFallbackListeners(decoded.fallbackNotice());
                }
                notifyIncomingMessageListeners(contactId, decoded.message());
                status.set("Message received from " + contactId);
            });
        }

        /**
         * Surfaces incoming render failures to status and runtime issue listeners.
         *
         * @param ex failure raised while rendering an incoming message
         */
        private void handleIncomingRenderFailure(Exception ex) {
            runOnUiThread(() -> status.set("Failed to render message: " + ex.getMessage()));
            publishRuntimeIssue(
                    "messaging.render.failed",
                    "Message processing failed",
                    "An incoming message could not be processed. " + resolveErrorMessage(ex, "Please retry."),
                    MessagesViewModel.this::retryLastFailedOperation);
        }

        /**
         * Surfaces receiver-side pipeline failures to the status property on the UI
         * thread.
         *
         * @param error failure raised by the receiver
         */
        private void handleReceiverError(Throwable error) {
            if (publishReceiverIssueIfHandled(error)) {
                return;
            }
            runOnUiThread(() -> status.set("Error: " + error.getMessage()));
            publishRuntimeIssue(
                    "messaging.receiver.error",
                    "Connection issue",
                    "The messaging channel reported an error. "
                            + resolveErrorMessage(error, "Please retry."),
                    MessagesViewModel.this::retryLastFailedOperation);
        }

        /**
         * Publishes specialized receiver issues that do not need generic connection
         * handling.
         *
         * @param error failure raised by the receiver
         * @return {@code true} when a specialized issue was published
         */
        private boolean publishReceiverIssueIfHandled(Throwable error) {
            if (isSessionTakeoverError(error)) {
                publishRuntimeIssue(
                        SESSION_TAKEOVER_ISSUE_KEY,
                        SESSION_TAKEOVER_TITLE,
                        SESSION_TAKEOVER_MESSAGE,
                        () -> {
                        });
                return true;
            }
            if (isRevokedSessionError(error)) {
                publishRuntimeIssue(
                        "messaging.session.revoked",
                        "Session expired",
                        "Your session expired due to inactivity. Please log in again.",
                        () -> {
                        });
                return true;
            }
            if (isUndecryptableEnvelopeError(error)) {
                publishRuntimeIssue(
                        "messaging.undecryptable.envelopes",
                        "Some messages could not be decrypted",
                        "Some messages were encrypted with keys not available on this device and were skipped.",
                        () -> {
                        });
                return true;
            }
            return false;
        }

        /**
         * Notifies all registered presence listeners while isolating failures per
         * listener.
         *
         * @param userId user whose presence changed
         * @param active latest active flag
         */
        private void notifyPresenceListeners(String userId, boolean active) {
            for (PresenceListener listener : presenceListeners) {
                try {
                    listener.onPresenceUpdate(userId, active);
                } catch (Exception ignored) {
                    // A bad listener must not break dispatching for others.
                }
            }
        }
    }

    /**
     * Sends a plain-text message asynchronously and adds it locally as outgoing
     * bubble.
     *
     * @param recipientId recipient/contact id
     * @param text        message body to send
     */
    public void sendTextMessage(String recipientId, String text) {
        String contactId = normalizeContactId(recipientId);
        MessageVM pendingVm = MessageVM.outgoingLoadingText(text, LocalDateTime.now());
        runOnUiThread(() -> {
            getMessages(contactId).add(pendingVm);
            status.set("Sending message to " + contactId + "…");
        });
        CompletableFuture.runAsync(() -> sendTextMessageInternal(contactId, text, pendingVm));
    }

    /**
     * Performs text-message encryption + network send work off the UI thread.
     *
     * @param contactId normalized recipient/contact id
     * @param text      message body to send
     * @param pendingVm optimistic pending bubble to resolve on success/failure
     */
    private void sendTextMessageInternal(String contactId, String text, MessageVM pendingVm) {
        try {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            messageSender.sendMessage(payload, contactId, "text/plain", MessageHeader.MAX_TTL_SECONDS);

            MessageVM vm = MessageVM.outgoingText(text, pendingVm.timestamp());
            runOnUiThread(() -> replaceLoadingMessage(contactId, pendingVm, vm, "Message sent to " + contactId));
            clearFailedSendRetryAction();
        } catch (Exception e) {
            String outgoingText = text;
            captureFailedSendRetryAction(() -> retryPendingTextMessage(contactId, outgoingText, pendingVm));
            runOnUiThread(() -> status.set("Failed to send: " + e.getMessage()));
            publishRuntimeIssue(
                    "messaging.send.failed",
                    "Message could not be sent",
                    "Could not send your message. " + resolveErrorMessage(e, "Please retry."),
                    this::retryLastFailedOperation);
        }
    }

    /**
     * Retries a failed text send while preserving the existing pending bubble.
     *
     * @param contactId normalized recipient/contact id
     * @param text      message body to resend
     * @param pendingVm pending bubble to keep/reuse during retry
     */
    private void retryPendingTextMessage(String contactId, String text, MessageVM pendingVm) {
        runOnUiThread(() -> {
            ObservableList<MessageVM> messages = getMessages(contactId);
            if (!messages.contains(pendingVm)) {
                messages.add(pendingVm);
            }
            status.set("Retrying message to " + contactId + "…");
        });
        CompletableFuture.runAsync(() -> sendTextMessageInternal(contactId, text, pendingVm));
    }

    /**
     * Sends one attachment using inline or chunked transport based on size.
     *
     * @param recipientId   recipient/contact id
     * @param filePath      selected attachment path
     * @param mediaTypeHint optional MIME hint from the UI/file chooser
     */
    public void sendAttachment(String recipientId, Path filePath, String mediaTypeHint) {
        String contactId = normalizeContactId(recipientId);
        CompletableFuture.runAsync(() -> sendAttachmentInternal(contactId, filePath, mediaTypeHint));
    }

    /**
     * Validates, encodes and sends an attachment, then appends a local outgoing
     * message bubble.
     *
     * This method chooses inline versus chunked transport according to backend
     * messaging policy.
     *
     * @param contactId     normalized contact id of the recipient
     * @param filePath      path to the attachment selected by the user
     * @param mediaTypeHint optional MIME hint from file chooser/UI
     */
    private void sendAttachmentInternal(String contactId, Path filePath, String mediaTypeHint) {
        MessageVM loadingVm = null;
        try {
            if (filePath == null || !Files.exists(filePath)) {
                throw new IllegalArgumentException("Attachment file is missing");
            }

            long fileSizeBytes = Files.size(filePath);
            if (fileSizeBytes <= 0) {
                throw new IllegalArgumentException("Attachment file is empty");
            }

            MessagingPolicyResponse policy = getMessagingPolicy();
            String mediaType = normalizeMediaType(mediaTypeHint, filePath);
            validateAgainstPolicy(mediaType, fileSizeBytes, policy);
            String fileName = sanitizeFileName(filePath.getFileName().toString());

            byte[] fileBytes = Files.readAllBytes(filePath);
            if (fileBytes.length == 0) {
                throw new IllegalArgumentException("Attachment file is empty");
            }

            LocalDateTime timestamp = LocalDateTime.now();
            if (shouldSendInlineAttachment(fileBytes, policy, fileName, mediaType)) {
                if (shouldShowInlineLoadingPlaceholder(mediaType)) {
                    loadingVm = showOutgoingAttachmentLoadingPlaceholder(contactId, mediaType, fileName,
                            fileBytes.length, timestamp);
                }
                sendInlineAttachmentAndRender(contactId, fileName, mediaType, fileBytes, timestamp, loadingVm);
            } else {
                loadingVm = showOutgoingAttachmentLoadingPlaceholder(contactId, mediaType, fileName, fileBytes.length,
                        timestamp);
                sendChunkedAttachmentAndRender(
                        contactId,
                        fileName,
                        mediaType,
                        fileBytes,
                        policy,
                        timestamp,
                        loadingVm);
            }

            clearFailedSendRetryAction();
        } catch (Exception ex) {
            handleAttachmentSendFailure(contactId, filePath, mediaTypeHint, loadingVm, ex);
        }
    }

    /**
     * Handles attachment-send failures by removing stale loading placeholders,
     * recording retry actions, updating status text, and publishing a runtime
     * issue.
     *
     * @param contactId     normalized recipient/contact id
     * @param filePath      selected attachment path
     * @param mediaTypeHint original MIME hint from UI
     * @param loadingVm     loading placeholder currently displayed, when any
     * @param ex            failure that interrupted send flow
     */
    private void handleAttachmentSendFailure(String contactId,
            Path filePath,
            String mediaTypeHint,
            MessageVM loadingVm,
            Exception ex) {
        MessageVM staleLoadingVm = loadingVm;
        if (staleLoadingVm != null) {
            runOnUiThread(() -> removeLoadingMessage(contactId, staleLoadingVm));
        }
        String retryMediaTypeHint = mediaTypeHint;
        captureFailedSendRetryAction(() -> sendAttachment(contactId, filePath, retryMediaTypeHint));
        runOnUiThread(() -> status.set("Failed to send attachment: " + ex.getMessage()));
        publishRuntimeIssue(
                "messaging.attachment.send.failed",
                "Attachment could not be sent",
                "Could not send the selected attachment. " + resolveErrorMessage(ex, "Please retry."),
                this::retryLastFailedOperation);
    }

    /**
     * Returns whether attachment should be sent inline (single envelope) instead of
     * chunked upload.
     *
     * @param fileBytes attachment bytes
     * @param policy    active messaging policy snapshot
     * @param fileName  attachment file name
     * @param mediaType normalized media type
     * @return {@code true} when inline transport should be used
     */
    private boolean shouldSendInlineAttachment(byte[] fileBytes,
            MessagingPolicyResponse policy,
            String fileName,
            String mediaType) {
        return fileBytes.length <= policy.getAttachmentInlineMaxBytes()
                && canSendInlineWithinWsBudget(fileName, mediaType, fileBytes.length);
    }

    /**
     * Sends attachment using inline transport and appends or replaces the resolved
     * outgoing message bubble.
     *
     * @param contactId recipient/contact id
     * @param fileName  attachment file name
     * @param mediaType normalized media type
     * @param fileBytes attachment bytes
     * @param timestamp message timestamp
     * @param loadingVm existing loading placeholder to replace, or {@code null}
     *                  to append after send
     * @throws Exception when inline transport fails
     */
    private void sendInlineAttachmentAndRender(String contactId,
            String fileName,
            String mediaType,
            byte[] fileBytes,
            LocalDateTime timestamp,
            MessageVM loadingVm) throws Exception {
        sendInlineAttachment(contactId, fileName, mediaType, fileBytes);
        DecodedMessage outgoing = decodeAttachment(contactId, true, fileBytes, mediaType, fileName, timestamp);
        MessageVM pendingVm = loadingVm;
        runOnUiThread(() -> {
            if (pendingVm == null) {
                getMessages(contactId).add(outgoing.message());
                status.set(buildAttachmentDeliveredStatus(contactId, mediaType, true));
            } else {
                replaceLoadingMessage(
                        contactId,
                        pendingVm,
                        outgoing.message(),
                        buildAttachmentDeliveredStatus(contactId, mediaType, true));
            }
            if (outgoing.fallbackNotice() != null) {
                notifyImagePreviewFallbackListeners(outgoing.fallbackNotice());
            }
        });
    }

    /**
     * Shows outgoing loading placeholder while an attachment transfer is running.
     *
     * @param contactId     recipient/contact id
     * @param mediaType     normalized media type
     * @param fileName      attachment file name
     * @param fileSizeBytes attachment size in bytes
     * @param timestamp     message timestamp
     * @return loading placeholder message model
     */
    private MessageVM showOutgoingAttachmentLoadingPlaceholder(String contactId,
            String mediaType,
            String fileName,
            long fileSizeBytes,
            LocalDateTime timestamp) {
        MessageVM loadingVm = buildLoadingAttachmentVm(true, mediaType, fileName, fileSizeBytes, timestamp);
        MessageVM finalLoadingVm = loadingVm;
        runOnUiThread(() -> {
            getMessages(contactId).add(finalLoadingVm);
            status.set(buildAttachmentLoadingStatus(contactId, mediaType, true));
        });
        return loadingVm;
    }

    /**
     * Sends attachment using chunked transport and replaces loading placeholder
     * with resolved outgoing message bubble.
     *
     * @param contactId recipient/contact id
     * @param fileName  attachment file name
     * @param mediaType normalized media type
     * @param fileBytes attachment bytes
     * @param policy    active messaging policy snapshot
     * @param timestamp message timestamp
     * @param loadingVm loading placeholder message
     * @throws Exception when chunked upload flow fails
     */
    private void sendChunkedAttachmentAndRender(String contactId,
            String fileName,
            String mediaType,
            byte[] fileBytes,
            MessagingPolicyResponse policy,
            LocalDateTime timestamp,
            MessageVM loadingVm) throws Exception {
        sendChunkedAttachment(contactId, fileName, mediaType, fileBytes, policy);

        DecodedMessage outgoing = decodeAttachment(contactId, true, fileBytes, mediaType, fileName, timestamp);
        MessageVM resolvedLoadingVm = loadingVm;
        runOnUiThread(() -> {
            replaceLoadingMessage(
                    contactId,
                    resolvedLoadingVm,
                    outgoing.message(),
                    buildAttachmentDeliveredStatus(contactId, mediaType, true));
            if (outgoing.fallbackNotice() != null) {
                notifyImagePreviewFallbackListeners(outgoing.fallbackNotice());
            }
        });
    }

    /**
     * Determines whether an inline attachment is expected to fit comfortably within
     * the websocket inbound frame budget after JSON + crypto/Base64 expansion.
     *
     * @param fileName     attachment filename metadata
     * @param mediaType    normalized MIME type metadata
     * @param fileBytesLen plaintext attachment byte length
     * @return {@code true} when inline transport is expected to fit safely
     */
    private boolean canSendInlineWithinWsBudget(String fileName, String mediaType, int fileBytesLen) {
        long estimatedInlinePayloadBytes = estimateInlinePayloadBytes(fileName, mediaType, fileBytesLen);
        long estimatedWireBytes = estimateEncryptedEnvelopeWireBytes(estimatedInlinePayloadBytes);
        long budget = Math.max(1L, (long) MAX_WS_INBOUND_MESSAGE_BYTES - (long) INLINE_WIRE_HEADROOM_BYTES);
        return estimatedWireBytes <= budget;
    }

    /**
     * Estimates the UTF-8 byte size of the inline attachment JSON payload before
     * encryption.
     *
     * @param fileName     attachment filename metadata
     * @param mediaType    normalized MIME type metadata
     * @param fileBytesLen plaintext attachment byte length
     * @return estimated payload bytes
     */
    private long estimateInlinePayloadBytes(String fileName, String mediaType, int fileBytesLen) {
        long dataB64Bytes = base64EncodedLength(Math.max(0L, fileBytesLen));
        int fileNameLen = fileName == null ? 0 : fileName.length();
        int mediaTypeLen = mediaType == null ? 0 : mediaType.length();
        int sizeDigits = String.valueOf(Math.max(0, fileBytesLen)).length();
        return dataB64Bytes + fileNameLen + mediaTypeLen + sizeDigits + 96L;
    }

    /**
     * Estimates serialized websocket envelope size for an encrypted payload.
     *
     * @param inlinePayloadBytes plaintext inline payload size in bytes
     * @return estimated websocket message bytes
     */
    private long estimateEncryptedEnvelopeWireBytes(long inlinePayloadBytes) {
        long ciphertextB64Bytes = base64EncodedLength(Math.max(0L, inlinePayloadBytes));
        return ciphertextB64Bytes + INLINE_ESTIMATED_ENVELOPE_OVERHEAD_BYTES;
    }

    /**
     * Returns the Base64 output length for a given input byte length.
     *
     * @param inputBytes byte length before Base64 encoding
     * @return encoded length in characters/bytes
     */
    private static long base64EncodedLength(long inputBytes) {
        if (inputBytes <= 0) {
            return 0L;
        }
        return ((inputBytes + 2L) / 3L) * 4L;
    }

    /**
     * Resolves websocket inbound size budget from the same system property used by
     * transport layer.
     *
     * @return max websocket inbound bytes budget used for inline decisioning
     */
    private static int resolveWsInboundMessageBytes() {
        String configured = System.getProperty("haf.ws.maxInboundBytes");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_WS_INBOUND_MESSAGE_BYTES;
        }
        try {
            int parsed = Integer.parseInt(configured.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to default.
        }
        return DEFAULT_WS_INBOUND_MESSAGE_BYTES;
    }

    /**
     * Starts the message receiver.
     */
    public void startReceiving() {
        if (receivingStarted) {
            return;
        }
        try {
            messageReceiver.start();
            receivingStarted = true;
            runOnUiThread(() -> status.set("Receiving messages…"));
        } catch (Exception e) {
            runOnUiThread(() -> status.set("Failed to start receiving: " + e.getMessage()));
            publishRuntimeIssue(
                    "messaging.receive.start.failed",
                    "Connection issue",
                    "Could not start message receiving. " + resolveErrorMessage(e, "Please retry."),
                    this::retryLastFailedOperation);
        }
    }

    /**
     * Stops the message receiver.
     */
    public void stopReceiving() {
        if (!receivingStarted) {
            return;
        }
        messageReceiver.stop();
        receivingStarted = false;
        runOnUiThread(() -> status.set("Stopped receiving messages"));
    }

    /**
     * Acknowledges all pending messages received from a specific sender.
     *
     * @param senderId sender/contact id whose pending envelopes should be
     *                 acknowledged
     */
    public void acknowledgeMessagesFrom(String senderId) {
        messageReceiver.acknowledgeEnvelopes(normalizeContactId(senderId));
    }

    /**
     * Observable list of chat messages for a specific contact.
     *
     * @param contactId contact id whose timeline should be returned
     * @return mutable observable timeline for the normalized contact id
     */
    public ObservableList<MessageVM> getMessages(String contactId) {
        String normalizedContactId = normalizeContactId(contactId);
        return messagesByContact.computeIfAbsent(normalizedContactId, ignored -> FXCollections.observableArrayList());
    }

    /**
     * Status string property.
     *
     * @return observable status text property
     */
    public StringProperty statusProperty() {
        return status;
    }

    /**
     * Registers a presence observer to receive online/offline transitions for
     * contacts.
     *
     * @param listener listener to register; ignored when {@code null}
     */
    public void addPresenceListener(PresenceListener listener) {
        if (listener != null) {
            presenceListeners.add(listener);
        }
    }

    /**
     * Unregisters a previously registered presence observer.
     *
     * @param listener listener to remove; ignored when {@code null}
     */
    public void removePresenceListener(PresenceListener listener) {
        if (listener != null) {
            presenceListeners.remove(listener);
        }
    }

    /**
     * Registers a listener for newly materialized incoming messages.
     *
     * @param listener listener to register; ignored when {@code null}
     */
    public void addIncomingMessageListener(IncomingMessageListener listener) {
        if (listener != null) {
            incomingMessageListeners.add(listener);
        }
    }

    /**
     * Unregisters a previously registered incoming-message listener.
     *
     * @param listener listener to remove; ignored when {@code null}
     */
    public void removeIncomingMessageListener(IncomingMessageListener listener) {
        if (listener != null) {
            incomingMessageListeners.remove(listener);
        }
    }

    /**
     * Registers a listener notified when an image payload is downgraded to a file
     * bubble due to unsupported/mismatched preview format.
     *
     * @param listener listener to register; ignored when {@code null}
     */
    public void addImagePreviewFallbackListener(ImagePreviewFallbackListener listener) {
        if (listener != null) {
            imagePreviewFallbackListeners.add(listener);
        }
    }

    /**
     * Unregisters a previously registered image-preview fallback listener.
     *
     * @param listener listener to remove; ignored when {@code null}
     */
    public void removeImagePreviewFallbackListener(ImagePreviewFallbackListener listener) {
        if (listener != null) {
            imagePreviewFallbackListeners.remove(listener);
        }
    }

    /**
     * Registers a listener for recoverable runtime issues that should be surfaced
     * in UI.
     *
     * @param listener runtime issue listener to register
     */
    public void addRuntimeIssueListener(Consumer<RuntimeIssue> listener) {
        if (listener != null) {
            runtimeIssueListeners.add(listener);
        }
    }

    /**
     * Unregisters a previously registered runtime-issue listener.
     *
     * @param listener runtime issue listener to remove
     */
    public void removeRuntimeIssueListener(Consumer<RuntimeIssue> listener) {
        if (listener != null) {
            runtimeIssueListeners.remove(listener);
        }
    }

    /**
     * Reconnects receiver transport and retries the last failed send operation when
     * available.
     *
     * This method runs asynchronously and is intended to be used as a popup
     * retry callback.
     */
    public void retryLastFailedOperation() {
        CompletableFuture.runAsync(() -> {
            try {
                reconnectReceiver();
                Runnable retryAction = lastFailedSendRetryAction.get();
                if (retryAction != null) {
                    retryAction.run();
                } else {
                    runOnUiThread(() -> status.set("Connection restored"));
                }
            } catch (Exception ex) {
                runOnUiThread(() -> status.set("Retry failed: " + resolveErrorMessage(ex, "Unknown error.")));
                publishRuntimeIssue(
                        "messaging.retry.failed",
                        "Retry failed",
                        "Could not restore messaging connection. " + resolveErrorMessage(ex, "Please retry."),
                        this::retryLastFailedOperation);
            }
        });
    }

    /**
     * Restores receiver transport after access-token rotation succeeds.
     *
     * This reconnect path intentionally does not resend the last failed outgoing
     * message; it only restores inbound/presence transport.
     */
    public void restoreReceiverTransportAfterSessionRefresh() {
        CompletableFuture.runAsync(() -> {
            try {
                reconnectReceiver();
                runOnUiThread(() -> status.set("Messaging connection restored"));
            } catch (Exception ex) {
                runOnUiThread(() -> status.set(
                        "Failed to restore messaging connection: " + resolveErrorMessage(ex, "Unknown error.")));
                publishRuntimeIssue(
                        "messaging.refresh.reconnect.failed",
                        "Connection issue",
                        "Session refreshed, but messaging connection could not be restored. "
                                + resolveErrorMessage(ex, "Please retry."),
                        this::restoreReceiverTransportAfterSessionRefresh);
            }
        });
    }

    /**
     * Notifies all incoming-message listeners while preventing one faulty listener
     * from blocking others.
     *
     * @param senderId sender/contact id associated with the message
     * @param message  resolved message model to deliver
     */
    private void notifyIncomingMessageListeners(String senderId, MessageVM message) {
        for (IncomingMessageListener listener : incomingMessageListeners) {
            try {
                listener.onIncomingMessage(senderId, message);
            } catch (Exception ignored) {
                // A bad listener must not break dispatching for others.
            }
        }
    }

    /**
     * Notifies listeners when an image-like payload was rendered as a generic file
     * attachment.
     *
     * @param notice fallback event payload
     */
    private void notifyImagePreviewFallbackListeners(ImagePreviewFallbackNotice notice) {
        if (notice == null) {
            return;
        }
        for (ImagePreviewFallbackListener listener : imagePreviewFallbackListeners) {
            try {
                listener.onImageFallback(notice);
            } catch (Exception ignored) {
                // A bad listener must not break dispatching for others.
            }
        }
    }

    /**
     * Notifies registered runtime-issue listeners while isolating listener
     * failures.
     *
     * @param issue issue payload to dispatch
     */
    private void notifyRuntimeIssueListeners(RuntimeIssue issue) {
        for (Consumer<RuntimeIssue> listener : runtimeIssueListeners) {
            try {
                listener.accept(issue);
            } catch (Exception ignored) {
                // A bad listener must not break dispatching for others.
            }
        }
    }

    /**
     * Publishes a recoverable runtime issue to registered listeners.
     *
     * @param dedupeKey   stable dedupe key
     * @param title       popup title
     * @param message     popup message
     * @param retryAction action to execute on retry
     */
    private void publishRuntimeIssue(String dedupeKey, String title, String message, Runnable retryAction) {
        notifyRuntimeIssueListeners(new RuntimeIssue(dedupeKey, title, message, retryAction));
    }

    /**
     * Stores retry action used to re-run the last failed send operation.
     *
     * @param retryAction send retry action
     */
    private void captureFailedSendRetryAction(Runnable retryAction) {
        lastFailedSendRetryAction.set(retryAction);
    }

    /**
     * Clears the remembered send retry action after successful operations.
     */
    private void clearFailedSendRetryAction() {
        lastFailedSendRetryAction.set(null);
    }

    /**
     * Restarts receiver transport to force reconnection.
     *
     * @throws Exception when stop/start operations fail
     */
    private void reconnectReceiver() throws Exception {
        messageReceiver.stop();
        receivingStarted = false;
        messageReceiver.start();
        receivingStarted = true;
    }

    /**
     * Extracts a user-facing message from a Throwable with fallback text.
     *
     * @param error    throwable to inspect
     * @param fallback fallback text when throwable message is empty
     * @return resolved message text
     */
    private static String resolveErrorMessage(Throwable error, String fallback) {
        if (error == null) {
            return fallback;
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }

    /**
     * Detects revoked/invalid-session errors from authenticated messaging flows.
     *
     * @param error runtime error candidate
     * @return {@code true} when caller session is no longer valid
     */
    private static boolean isRevokedSessionError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpCommunicationException communicationException) {
                int statusCode = communicationException.getStatusCode();
                if (statusCode == 401 || statusCode == 403) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("invalid session")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Detects takeover-specific invalid-session errors from authenticated messaging
     * flows.
     *
     * @param error runtime error candidate
     * @return {@code true} when session was explicitly revoked by takeover
     */
    private static boolean isSessionTakeoverError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (containsSessionTakeoverMarker(current.getMessage())) {
                return true;
            }
            if (current instanceof HttpCommunicationException communicationException
                    && containsSessionTakeoverMarker(communicationException.getResponseBody())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsSessionTakeoverMarker(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("session revoked by takeover")
                || normalized.contains("revoked by takeover");
    }

    /**
     * Detects undecryptable-envelope failures surfaced by receiver decryption
     * fallback exhaustion.
     *
     * @param error runtime error candidate
     * @return {@code true} when envelope decryption failed integrity verification
     */
    private static boolean isUndecryptableEnvelopeError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof MessageTamperedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Executes a UI mutation either directly (already on FX thread) or via
     * {@link Platform#runLater(Runnable)}.
     *
     * Falls back to direct execution when JavaFX toolkit checks are unavailable.
     *
     * @param action UI action to execute
     */
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
        } catch (Throwable _) {
            fxToolkitUnavailable = true;
            action.run();
            return;
        }

        CountDownLatch completion = new CountDownLatch(1);
        AtomicReference<Throwable> actionFailure = new AtomicReference<>();
        try {
            Platform.runLater(() -> {
                try {
                    action.run();
                } catch (Throwable throwable) {
                    actionFailure.set(throwable);
                } finally {
                    completion.countDown();
                }
            });
        } catch (Throwable _) {
            fxToolkitUnavailable = true;
            action.run();
            return;
        }

        try {
            completion.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new UiDispatchException("Interrupted while dispatching UI action", ex);
        }

        Throwable failure = actionFailure.get();
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new UiDispatchException("Error during UI action execution", failure);
        }
    }

    /**
     * Normalizes contact identifiers used as message-bucket keys.
     *
     * @param contactId raw contact id from caller or transport
     * @return trimmed id, or an empty string when input is {@code null}
     */
    private static String normalizeContactId(String contactId) {
        return contactId == null ? "" : contactId.trim();
    }

    /**
     * Checks whether a content type belongs to the image family after MIME
     * normalization.
     *
     * @param mediaType raw MIME/content type
     * @return {@code true} when the media type is an image type
     */
    private static boolean isImageMediaType(String mediaType) {
        String normalized = AttachmentConstants.normalizeMimeType(mediaType);
        return normalized != null && normalized.startsWith("image/");
    }

    /**
     * Converts a server epoch timestamp into the local JVM time-zone date-time.
     *
     * @param timestampEpochMs timestamp in epoch milliseconds
     * @return localized timestamp for message display
     */
    private static LocalDateTime toLocalTimestamp(long timestampEpochMs) {
        return Instant.ofEpochMilli(timestampEpochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /**
     * Resolves a reference-based attachment in the background while showing a
     * loading placeholder in chat.
     *
     * @param senderId         sender/contact id whose timeline receives the
     *                         attachment
     * @param reference        reference payload pointing to uploaded attachment
     *                         data
     * @param timestampEpochMs envelope timestamp in epoch milliseconds
     */
    private void receiveChunkedReferenceAsync(String senderId,
            AttachmentReferencePayload reference,
            long timestampEpochMs) {
        LocalDateTime timestamp = toLocalTimestamp(timestampEpochMs);
        String mediaType = AttachmentConstants.normalizeMimeType(reference.getMediaType());
        MessageVM loadingVm = buildLoadingAttachmentVm(
                false,
                mediaType,
                reference.getFileName(),
                reference.getSizeBytes(),
                timestamp);

        runOnUiThread(() -> {
            getMessages(senderId).add(loadingVm);
            // Notify once, when the inbound envelope is first materialized.
            notifyIncomingMessageListeners(senderId, loadingVm);
            status.set(buildAttachmentLoadingStatus(senderId, mediaType, false));
        });

        CompletableFuture.runAsync(() -> {
            try {
                DecodedMessage loaded = decodeAttachmentReference(senderId, reference, timestamp);
                runOnUiThread(() -> {
                    replaceLoadingMessage(
                            senderId,
                            loadingVm,
                            loaded.message(),
                            buildAttachmentDeliveredStatus(senderId, mediaType, false));
                    if (loaded.fallbackNotice() != null) {
                        notifyImagePreviewFallbackListeners(loaded.fallbackNotice());
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> replaceLoadingWithFailure(senderId, loadingVm, timestamp, mediaType, ex));
            }
        });
    }

    /**
     * Resolves an inline image attachment in the background after immediately
     * materializing a loading bubble.
     *
     * @param senderId         sender/contact id whose timeline receives the
     *                         attachment
     * @param payload          inline attachment payload already decrypted from the
     *                         envelope
     * @param timestampEpochMs envelope timestamp in epoch milliseconds
     */
    private void receiveInlineAttachmentAsync(String senderId,
            AttachmentInlinePayload payload,
            long timestampEpochMs) {
        LocalDateTime timestamp = toLocalTimestamp(timestampEpochMs);
        String mediaType = AttachmentConstants.normalizeMimeType(payload.getMediaType());
        MessageVM loadingVm = buildLoadingAttachmentVm(
                false,
                mediaType,
                payload.getFileName(),
                payload.getSizeBytes(),
                timestamp);

        runOnUiThread(() -> {
            getMessages(senderId).add(loadingVm);
            notifyIncomingMessageListeners(senderId, loadingVm);
            status.set(buildAttachmentLoadingStatus(senderId, mediaType, false));
        });

        CompletableFuture.runAsync(() -> {
            try {
                DecodedMessage loaded = decodeInlineAttachment(senderId, payload, timestamp);
                runOnUiThread(() -> {
                    replaceLoadingMessage(
                            senderId,
                            loadingVm,
                            loaded.message(),
                            buildAttachmentDeliveredStatus(senderId, mediaType, false));
                    if (loaded.fallbackNotice() != null) {
                        notifyImagePreviewFallbackListeners(loaded.fallbackNotice());
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> replaceLoadingWithFailure(senderId, loadingVm, timestamp, mediaType, ex));
            }
        });
    }

    /**
     * Replaces an in-place loading attachment bubble with its fully decoded
     * message.
     *
     * @param contactId      sender/contact id for the message bucket
     * @param loadingVm      placeholder message currently shown in timeline
     * @param loadedVm       final decoded attachment message
     * @param resolvedStatus status text shown after successful replacement
     */
    private void replaceLoadingMessage(String contactId,
            MessageVM loadingVm,
            MessageVM loadedVm,
            String resolvedStatus) {
        ObservableList<MessageVM> messages = getMessages(contactId);
        int index = messages.indexOf(loadingVm);
        if (index >= 0) {
            messages.set(index, loadedVm);
        } else {
            messages.add(loadedVm);
        }
        status.set(resolvedStatus);
    }

    /**
     * Removes an unresolved loading placeholder.
     *
     * @param contactId sender/contact id for the message bucket
     * @param loadingVm loading message to remove when still present
     */
    private void removeLoadingMessage(String contactId, MessageVM loadingVm) {
        getMessages(contactId).remove(loadingVm);
    }

    /**
     * Replaces a loading attachment placeholder with a failure marker when async
     * download/decrypt fails.
     *
     * @param senderId  sender/contact id for status text
     * @param loadingVm placeholder message currently shown in timeline
     * @param timestamp timestamp reused for the fallback failure bubble
     * @param mediaType normalized media type of the attachment
     * @param ex        failure that occurred while resolving the attachment
     */
    private void replaceLoadingWithFailure(String senderId,
            MessageVM loadingVm,
            LocalDateTime timestamp,
            String mediaType,
            Exception ex) {
        ObservableList<MessageVM> messages = getMessages(senderId);
        int index = messages.indexOf(loadingVm);
        String noun = attachmentNoun(mediaType);
        String failureText = isImageMediaType(mediaType) ? "[Image failed to load]" : "[File failed to load]";
        MessageVM failedVm = MessageVM.incomingText(failureText, timestamp);
        if (index >= 0) {
            messages.set(index, failedVm);
        } else {
            messages.add(failedVm);
        }
        status.set("Failed to load " + noun + ": " + ex.getMessage());
    }

    /**
     * Sends an attachment inline inside a single message payload.
     *
     * @param recipientId recipient user/contact id
     * @param fileName    original file name included in payload metadata
     * @param mediaType   normalized MIME/content type of the attachment
     * @param fileBytes   attachment bytes to embed as Base64
     * @throws Exception when serialization or send operation fails
     */
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

    /**
     * Sends an attachment through the chunked upload flow and then emits a
     * reference message envelope.
     *
     * @param recipientId recipient user/contact id
     * @param fileName    original client-side file name
     * @param mediaType   normalized MIME/content type
     * @param fileBytes   plaintext attachment bytes
     * @param policy      current messaging policy snapshot used for chunk sizing
     * @throws Exception when encryption, upload, completion, reference send, or
     *                   bind fails
     */
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

    /**
     * Decodes an inbound payload into a UI message model according to its content
     * type.
     *
     * @param plaintext        decrypted payload bytes
     * @param senderId         normalized sender/contact id
     * @param contentType      payload MIME/content type; defaults to
     *                         {@code text/plain} when {@code null}
     * @param timestampEpochMs server timestamp in epoch milliseconds
     * @return decoded message plus optional image-preview fallback notice
     * @throws Exception when attachment decoding or reference resolution fails
     */
    private DecodedMessage decodeIncoming(byte[] plaintext,
            String senderId,
            String contentType,
            long timestampEpochMs) throws Exception {
        LocalDateTime timestamp = toLocalTimestamp(timestampEpochMs);

        if (contentType == null) {
            contentType = "text/plain";
        }

        if (AttachmentConstants.CONTENT_TYPE_INLINE.equals(contentType)) {
            AttachmentInlinePayload payload = AttachmentPayloadCodec.fromInlineJson(plaintext);
            return decodeInlineAttachment(senderId, payload, timestamp);
        }

        if (AttachmentConstants.CONTENT_TYPE_REFERENCE.equals(contentType)) {
            AttachmentReferencePayload ref = AttachmentPayloadCodec.fromReferenceJson(plaintext);
            return decodeAttachmentReference(senderId, ref, timestamp);
        }

        if (contentType.startsWith("text/")) {
            String text = new String(plaintext, StandardCharsets.UTF_8);
            return new DecodedMessage(
                    new MessageVM(false, MessageType.TEXT, text, null, null, null, timestamp, false),
                    null);
        }

        if (contentType.startsWith("image/")) {
            String fileName = senderId + "-image" + extensionFor(contentType);
            return decodeAttachment(senderId, false, plaintext, contentType, fileName, timestamp);
        }

        String ext = extensionFor(contentType);
        String fileName = senderId + "-attachment" + ext;
        String fileSize = formatSize(plaintext.length);
        String localPath = writeTempFile(plaintext, "haf-file-", ext);
        return new DecodedMessage(
                new MessageVM(false, MessageType.FILE, null, localPath, fileName, fileSize, timestamp, false),
                null);
    }

    /**
     * Decodes an inline attachment payload into a renderable message model.
     *
     * @param senderId  sender/contact id owning the timeline message
     * @param payload   inline attachment metadata and Base64 data
     * @param timestamp timestamp to apply to the produced message model
     * @return decoded attachment message plus optional fallback notice
     */
    private static DecodedMessage decodeInlineAttachment(String senderId,
            AttachmentInlinePayload payload,
            LocalDateTime timestamp) {
        byte[] fileBytes = Base64.getDecoder().decode(payload.getDataB64());
        return decodeAttachment(senderId, false, fileBytes, payload.getMediaType(), payload.getFileName(),
                timestamp);
    }

    /**
     * Downloads and decrypts a referenced attachment, then converts it into a
     * timeline message model.
     *
     * @param senderId  sender/contact id owning the timeline message
     * @param ref       reference payload carrying attachment id and display
     *                  metadata
     * @param timestamp timestamp to apply to the produced message model
     * @return decoded attachment message plus optional image-preview fallback
     *         notice
     * @throws Exception when download payload is invalid or decrypt/parse
     *                   operations fail
     */
    private DecodedMessage decodeAttachmentReference(String senderId,
            AttachmentReferencePayload ref,
            LocalDateTime timestamp)
            throws Exception {
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
        return decodeAttachment(senderId, false, fileBytes, ref.getMediaType(), ref.getFileName(), timestamp);
    }

    /**
     * Returns cached messaging policy, loading it from backend once on first use.
     *
     * @return active messaging policy snapshot
     * @throws IOException when policy cannot be loaded or backend reports an error
     */
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

    /**
     * Validates attachment MIME type and size against the backend policy
     * constraints.
     *
     * @param mediaType normalized MIME/content type to validate
     * @param sizeBytes attachment size in bytes
     * @param policy    policy snapshot with limits and allowlist
     * @throws IllegalArgumentException when size exceeds maximum or MIME type is
     *                                  disallowed
     */
    private void validateAgainstPolicy(String mediaType, long sizeBytes, MessagingPolicyResponse policy) {
        if (sizeBytes > policy.getAttachmentMaxBytes()) {
            throw new IllegalArgumentException("Attachment exceeds maximum allowed size");
        }

        if (policy.getAttachmentAllowedTypes() == null || policy.getAttachmentAllowedTypes().isEmpty()) {
            throw new IllegalArgumentException("Attachment policy allowlist is empty");
        }
        if (!AttachmentConstants.isAttachmentTypeAllowedByPolicy(mediaType, policy.getAttachmentAllowedTypes())) {
            throw new IllegalArgumentException("Attachment type is not allowed: " + mediaType);
        }
    }

    /**
     * Normalizes attachment MIME type using explicit hint, OS probe, then extension
     * fallback mapping.
     *
     * @param hint     optional caller-provided MIME hint
     * @param filePath file path used for content probing and extension fallback
     * @return normalized MIME/content type suitable for policy and transport, or
     *         octet-stream when no specific MIME type can be inferred
     */
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

        return AttachmentConstants.APPLICATION_OCTET_STREAM;
    }

    /**
     * Sanitizes a user-provided attachment file name before embedding it in
     * transport payloads.
     *
     * @param fileName raw file name
     * @return safe file name
     * @throws IllegalArgumentException when name is blank or includes path
     *                                  separators
     */
    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Attachment filename is invalid");
        }
        if (fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Attachment filename must not contain path separators");
        }
        return fileName;
    }

    /**
     * Raises transport errors returned by backend API operations.
     *
     * @param error backend-provided error text
     * @throws IOException when error text is present
     */
    private static void ensureNoError(String error) throws IOException {
        if (error != null && !error.isBlank()) {
            throw new IOException(error);
        }
    }

    /**
     * Builds a loading placeholder message for attachment send/receive flows.
     *
     * @param outgoing      {@code true} for outgoing placeholders, {@code false}
     *                      for incoming
     * @param mediaType     normalized attachment media type
     * @param fileName      attachment display name candidate
     * @param fileSizeBytes attachment size in bytes
     * @param timestamp     message timestamp
     * @return loading placeholder message
     */
    private static MessageVM buildLoadingAttachmentVm(boolean outgoing,
            String mediaType,
            String fileName,
            long fileSizeBytes,
            LocalDateTime timestamp) {
        if (isImageMediaType(mediaType) && !isUnsupportedPreviewImageType(mediaType)) {
            String imageName = sanitizeImageName(fileName, mediaType);
            return outgoing
                    ? MessageVM.outgoingLoadingImage(imageName, timestamp)
                    : MessageVM.incomingLoadingImage(imageName, timestamp);
        }

        String safeFileName = sanitizeDisplayName(fileName, "File");
        String fileSize = fileSizeBytes > 0 ? formatSize(fileSizeBytes) : null;
        return outgoing
                ? MessageVM.outgoingLoadingFile(safeFileName, fileSize, timestamp)
                : MessageVM.incomingLoadingFile(safeFileName, fileSize, timestamp);
    }

    /**
     * Returns whether inline transport should still show an image placeholder
     * before final send/render completion.
     *
     * @param mediaType declared attachment media type
     * @return {@code true} for inline image attachments
     */
    private static boolean shouldShowInlineLoadingPlaceholder(String mediaType) {
        return isImageMediaType(mediaType);
    }

    /**
     * Builds attachment transfer status text.
     *
     * @param contactId contact id shown in status
     * @param mediaType normalized media type
     * @param outgoing  {@code true} when sending, {@code false} when receiving
     * @return status string
     */
    private static String buildAttachmentLoadingStatus(String contactId, String mediaType, boolean outgoing) {
        String noun = attachmentNoun(mediaType);
        if (outgoing) {
            return "Uploading " + noun + " to " + contactId + "…";
        }
        return "Receiving " + noun + " from " + contactId + "…";
    }

    /**
     * Builds attachment completion status text.
     *
     * @param contactId contact id shown in status
     * @param mediaType normalized media type
     * @param outgoing  {@code true} when sending, {@code false} when receiving
     * @return status string
     */
    private static String buildAttachmentDeliveredStatus(String contactId, String mediaType, boolean outgoing) {
        String noun = attachmentNoun(mediaType);
        if (outgoing) {
            return capitalize(noun) + " sent to " + contactId;
        }
        return capitalize(noun) + " received from " + contactId;
    }

    /**
     * Resolves attachment noun used in status/error text.
     *
     * @param mediaType normalized media type
     * @return attachment noun in lowercase
     */
    private static String attachmentNoun(String mediaType) {
        return isImageMediaType(mediaType) && !isUnsupportedPreviewImageType(mediaType) ? "image" : "file";
    }

    /**
     * Sanitizes display names used in placeholder bubbles.
     *
     * @param candidate candidate file name
     * @param fallback  fallback when candidate is blank or unsafe
     * @return safe display name
     */
    private static String sanitizeDisplayName(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        String trimmed = candidate.trim();
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            return fallback;
        }
        return trimmed;
    }

    /**
     * Capitalizes the first character of the provided text.
     *
     * @param text input text
     * @return text with first character capitalized
     */
    private static String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Converts attachment bytes and metadata into a rendered message plus optional
     * fallback notice when image preview was downgraded to file.
     *
     * @param contactId normalized contact id owning the timeline bucket
     * @param outgoing  whether message is local/outgoing
     * @param fileBytes attachment payload bytes
     * @param mediaType declared MIME/content type
     * @param fileName  display file name
     * @param timestamp message timestamp
     * @return decoded message and optional image-preview fallback event
     */
    private static DecodedMessage decodeAttachment(String contactId,
            boolean outgoing,
            byte[] fileBytes,
            String mediaType,
            String fileName,
            LocalDateTime timestamp) {
        ImagePreviewFallbackNotice notice = buildImagePreviewFallbackNotice(
                contactId,
                outgoing,
                mediaType,
                fileName,
                fileBytes);
        MessageVM message = toAttachmentVm(outgoing, fileBytes, mediaType, fileName, timestamp);
        return new DecodedMessage(message, notice);
    }

    /**
     * Converts attachment bytes and metadata into the appropriate image/file
     * message model for UI rendering.
     *
     * @param outgoing  whether message should be marked as sent by current user
     * @param fileBytes attachment payload bytes
     * @param mediaType MIME/content type used to choose image versus generic file
     *                  rendering
     * @param fileName  display file name
     * @param timestamp message timestamp
     * @return attachment message view-model
     */
    private static MessageVM toAttachmentVm(boolean outgoing,
            byte[] fileBytes,
            String mediaType,
            String fileName,
            LocalDateTime timestamp) {
        if (isImageMediaType(mediaType) && isRenderablePreviewImagePayload(mediaType, fileBytes)) {
            String localPath = writeTempFile(fileBytes, "haf-img-", imagePayloadExtension(fileBytes, mediaType));
            String imageName = sanitizeImageName(fileName, mediaType);
            return new MessageVM(outgoing, MessageType.IMAGE, localPath, null, imageName, null, timestamp, false);
        }

        String ext = fallbackAttachmentExtension(fileBytes, mediaType);
        String localPath = writeTempFile(fileBytes, "haf-file-", ext);
        String safeFileName = sanitizeDisplayName(fileName, "attachment" + ext);
        return new MessageVM(outgoing, MessageType.FILE, null, localPath, safeFileName, formatSize(fileBytes.length),
                timestamp, false);
    }

    /**
     * Builds a fallback notice when image preview is downgraded to generic file
     * rendering.
     *
     * @param contactId normalized chat contact id
     * @param outgoing  whether message is outgoing
     * @param mediaType declared media type
     * @param fileName  display file name
     * @param fileBytes attachment bytes
     * @return fallback notice, or {@code null} when image preview is renderable
     */
    private static ImagePreviewFallbackNotice buildImagePreviewFallbackNotice(String contactId,
            boolean outgoing,
            String mediaType,
            String fileName,
            byte[] fileBytes) {
        if (!isImageMediaType(mediaType) || isRenderablePreviewImagePayload(mediaType, fileBytes)) {
            return null;
        }
        String normalizedMediaType = AttachmentConstants.normalizeMimeType(mediaType);
        String detectedSignature = detectImageSignature(fileBytes);
        if (detectedSignature == null || detectedSignature.isBlank()) {
            detectedSignature = "unknown";
        }
        String safeFileName = sanitizeDisplayName(fileName, "image" + extensionFor(mediaType));
        return new ImagePreviewFallbackNotice(
                normalizeContactId(contactId),
                outgoing,
                safeFileName,
                normalizedMediaType,
                detectedSignature);
    }

    /**
     * Returns true when the declared media type is known to be unsupported by the
     * JavaFX preview pipeline.
     *
     * @param mediaType declared media type
     * @return {@code true} when preview should not attempt image rendering
     */
    private static boolean isUnsupportedPreviewImageType(String mediaType) {
        String normalized = AttachmentConstants.normalizeMimeType(mediaType);
        return "image/webp".equals(normalized);
    }

    /**
     * Returns whether attachment bytes are suitable for JavaFX image preview.
     *
     * @param mediaType declared media type
     * @param fileBytes attachment bytes
     * @return {@code true} when payload should be rendered as image
     */
    private static boolean isRenderablePreviewImagePayload(String mediaType, byte[] fileBytes) {
        if (isUnsupportedPreviewImageType(mediaType)) {
            return false;
        }
        String signature = detectImageSignature(fileBytes);
        return "png".equals(signature) || "jpeg".equals(signature) || "gif".equals(signature);
    }

    /**
     * Resolves best extension for persisted preview image temp file.
     *
     * @param fileBytes payload bytes
     * @param mediaType declared media type
     * @return extension for temp file names
     */
    private static String imagePayloadExtension(byte[] fileBytes, String mediaType) {
        String signature = detectImageSignature(fileBytes);
        return switch (signature) {
            case "png" -> ".png";
            case "jpeg" -> ".jpg";
            case "gif" -> ".gif";
            default -> extensionFor(mediaType);
        };
    }

    /**
     * Resolves extension for non-preview attachment temp file persistence.
     *
     * @param fileBytes payload bytes
     * @param mediaType declared media type
     * @return extension for persisted file attachment
     */
    private static String fallbackAttachmentExtension(byte[] fileBytes, String mediaType) {
        String signature = detectImageSignature(fileBytes);
        if ("webp".equals(signature)) {
            return ".webp";
        }
        return extensionFor(mediaType);
    }

    /**
     * Detects lightweight image signatures from raw payload bytes.
     *
     * @param fileBytes payload bytes
     * @return signature id (`png`, `jpeg`, `gif`, `webp`) or {@code null}
     */
    private static String detectImageSignature(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 12) {
            return null;
        }
        if ((fileBytes[0] & 0xFF) == 0x89
                && fileBytes[1] == 0x50
                && fileBytes[2] == 0x4E
                && fileBytes[3] == 0x47
                && fileBytes[4] == 0x0D
                && fileBytes[5] == 0x0A
                && fileBytes[6] == 0x1A
                && fileBytes[7] == 0x0A) {
            return "png";
        }
        if ((fileBytes[0] & 0xFF) == 0xFF
                && (fileBytes[1] & 0xFF) == 0xD8
                && (fileBytes[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if ((fileBytes[0] == 'G')
                && (fileBytes[1] == 'I')
                && (fileBytes[2] == 'F')
                && (fileBytes[3] == '8')
                && (fileBytes[4] == '7' || fileBytes[4] == '9')
                && (fileBytes[5] == 'a')) {
            return "gif";
        }
        if ((fileBytes[0] == 'R')
                && (fileBytes[1] == 'I')
                && (fileBytes[2] == 'F')
                && (fileBytes[3] == 'F')
                && (fileBytes[8] == 'W')
                && (fileBytes[9] == 'E')
                && (fileBytes[10] == 'B')
                && (fileBytes[11] == 'P')) {
            return "webp";
        }
        return null;
    }

    /**
     * Produces a safe image display name, defaulting to a synthetic value when the
     * provided name is unsafe.
     *
     * @param fileName  candidate file name from payload metadata
     * @param mediaType MIME type used to derive default extension
     * @return sanitized image display name
     */
    private static String sanitizeImageName(String fileName, String mediaType) {
        String extension = extensionFor(mediaType);
        if (fileName != null && !fileName.isBlank()) {
            String trimmed = fileName.trim();
            if (!trimmed.contains("/") && !trimmed.contains("\\")) {
                return trimmed;
            }
        }
        return "image" + extension;
    }

    /**
     * Persists bytes into a temporary local file and returns its URI for JavaFX
     * media/image loaders.
     *
     * @param data   bytes to write
     * @param prefix temporary-file prefix
     * @param suffix temporary-file suffix/extension
     * @return URI string to the temporary file, or {@code null} when temp file
     *         creation fails
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

    /**
     * Maps MIME/content types to file extensions used by temporary attachment
     * files.
     *
     * @param contentType normalized MIME/content type
     * @return preferred extension, or {@code .dat} for unknown types
     */
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

    /**
     * Formats attachment size in bytes into a compact human-readable string.
     *
     * @param bytes size in bytes
     * @return formatted size in B, KB or MB
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
