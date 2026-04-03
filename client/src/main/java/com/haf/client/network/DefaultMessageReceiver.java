package com.haf.client.network;

import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.client.utils.ClientRuntimeConfig;
import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.crypto.MessageDecryptor;
import com.haf.shared.dto.KeyMetadata;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.dto.UserSearchResultDTO;
import com.haf.shared.exceptions.KeystoreOperationException;
import com.haf.shared.exceptions.MessageExpiredException;
import com.haf.shared.exceptions.MessageTamperedException;
import com.haf.shared.exceptions.MessageValidationException;
import com.haf.shared.responses.ContactsResponse;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.MessageValidator;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles inbound encrypted messages, decryption, and acknowledgement flow.
 */
public class DefaultMessageReceiver implements MessageReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMessageReceiver.class);
    private static final int HTTP_POLL_BATCH_LIMIT = 100;
    private static final long HTTP_POLL_INTERVAL_MS = 2000L;
    private static final int HTTP_POLL_ERROR_NOTIFY_THRESHOLD = 3;

    private final KeyProvider keyProvider;
    private final ClockProvider clockProvider;
    private final WebSocketAdapter webSocketAdapter;
    private final String localRecipientId;
    private final ClientRuntimeConfig.MessagingTransportMode transportMode;
    private MessageListener messageListener;

    // Tracks envelopeIds already processed to avoid duplicate delivery on
    // reconnect.
    private final Set<String> seenEnvelopeIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Pending ACKs: senderId → list of envelope IDs awaiting acknowledgement.
    // Envelopes are only ACKed when the user opens the corresponding chat.
    private final ConcurrentHashMap<String, List<String>> pendingAcks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> knownPresenceByUser = new ConcurrentHashMap<>();
    private final Object pollingLock = new Object();
    private ScheduledExecutorService pollingExecutor;
    private volatile boolean httpPollingActive;
    private final AtomicInteger pollFailureCount = new AtomicInteger();

    /**
     * Creates a DefaultMessageReceiver with the specified dependencies.
     *
     * @param keyProvider      the key provider (must be UserKeystoreKeyProvider to
     *                         access keystore)
     * @param clockProvider    the clock provider for deterministic expiry checks
     * @param webSocketAdapter the WebSocket adapter for network communication
     * @param localRecipientId the local recipient's ID (must match message
     *                         recipientId)
     */
    public DefaultMessageReceiver(KeyProvider keyProvider, ClockProvider clockProvider,
            WebSocketAdapter webSocketAdapter, String localRecipientId) {
        this(keyProvider, clockProvider, webSocketAdapter, localRecipientId,
                ClientRuntimeConfig.MessagingTransportMode.WEBSOCKET);
    }

    /**
     * Creates a DefaultMessageReceiver with an explicit transport mode.
     *
     * @param keyProvider      key provider
     * @param clockProvider    clock provider
     * @param webSocketAdapter adapter exposing authenticated HTTPS and optional WSS
     * @param localRecipientId local recipient id
     * @param transportMode    runtime transport mode
     */
    public DefaultMessageReceiver(KeyProvider keyProvider, ClockProvider clockProvider,
            WebSocketAdapter webSocketAdapter, String localRecipientId,
            ClientRuntimeConfig.MessagingTransportMode transportMode) {
        this.keyProvider = keyProvider;
        this.clockProvider = clockProvider;
        this.webSocketAdapter = webSocketAdapter;
        this.localRecipientId = localRecipientId;
        this.transportMode = transportMode == null
                ? ClientRuntimeConfig.MessagingTransportMode.WEBSOCKET
                : transportMode;
    }

    /**
     * Sets the message listener for incoming messages.
     *
     * @param listener the message listener
     */
    @Override
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * Starts the message receiver.
     *
     * @throws IOException if the connection cannot be established
     */
    @Override
    public void start() throws IOException {
        if (messageListener == null) {
            throw new IllegalStateException("MessageListener must be set before starting");
        }
        stopHttpPolling();
        if (transportMode == ClientRuntimeConfig.MessagingTransportMode.HTTPS_POLLING) {
            startHttpPolling();
            return;
        }
        try {
            // Register message handler with WebSocketAdapter.
            webSocketAdapter.connect(
                    this::handleIncomingMessage,
                    this::handleError);
            pollFailureCount.set(0);
        } catch (IOException connectError) {
            LOGGER.info("WebSocket receiver unavailable ({}). Falling back to HTTP mailbox polling.",
                    connectError.getMessage());
            startHttpPolling();
        }
    }

    /**
     * Stops the message receiver.
     */
    @Override
    public void stop() {
        stopHttpPolling();
        webSocketAdapter.close();
    }

    /**
     * Starts background HTTP polling fallback when WebSocket transport cannot be
     * established.
     */
    private void startHttpPolling() {
        synchronized (pollingLock) {
            if (httpPollingActive) {
                return;
            }
            httpPollingActive = true;
            pollFailureCount.set(0);
            pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "message-http-poller");
                thread.setDaemon(true);
                return thread;
            });
            pollingExecutor.scheduleWithFixedDelay(
                    this::pollMailboxSafely,
                    0L,
                    HTTP_POLL_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops the HTTP polling fallback worker, if active.
     */
    private void stopHttpPolling() {
        synchronized (pollingLock) {
            httpPollingActive = false;
            pollFailureCount.set(0);
            if (pollingExecutor != null) {
                pollingExecutor.shutdownNow();
                pollingExecutor = null;
            }
        }
    }

    /**
     * Executes one polling cycle with guarded error handling.
     */
    private void pollMailboxSafely() {
        if (!httpPollingActive) {
            return;
        }
        try {
            pollCycleOnce();
            pollFailureCount.set(0);
        } catch (Exception pollError) {
            int failures = pollFailureCount.incrementAndGet();
            LOGGER.debug("HTTP mailbox poll failed: {}", pollError.getMessage());
            if (failures == HTTP_POLL_ERROR_NOTIFY_THRESHOLD) {
                notifyError(new IOException("HTTP mailbox polling failed. " + pollError.getMessage(), pollError));
            }
        }
    }

    /**
     * Executes one full HTTPS polling cycle (messages + presence snapshot).
     *
     * @throws IOException when polling request fails
     */
    private void pollCycleOnce() throws IOException {
        IOException failure = null;
        try {
            pollMailboxMessagesOnce();
        } catch (IOException e) {
            failure = e;
        }

        try {
            pollContactsPresenceOnce();
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Fetches pending mailbox envelopes over authenticated HTTP and processes them
     * through the same envelope pipeline used by WebSocket delivery.
     *
     * @throws IOException when polling request fails
     */
    private void pollMailboxMessagesOnce() throws IOException {
        String responseJson = requestWithIoMapping(
                webSocketAdapter.getAuthenticated("/api/v1/messages?limit=" + HTTP_POLL_BATCH_LIMIT),
                "Failed to poll mailbox");
        java.util.Map<?, ?> response = JsonCodec.fromJson(responseJson, java.util.Map.class);
        Object messagesNode = response.get("messages");
        if (!(messagesNode instanceof List<?> messages) || messages.isEmpty()) {
            return;
        }

        for (Object messageNode : messages) {
            if (messageNode == null) {
                continue;
            }
            handleIncomingMessage(JsonCodec.toJson(messageNode));
        }
    }

    /**
     * Fetches contact snapshot over HTTPS and emits presence transitions only when
     * state changed since the previous snapshot.
     *
     * @throws IOException when polling request fails
     */
    private void pollContactsPresenceOnce() throws IOException {
        String responseJson = requestWithIoMapping(
                webSocketAdapter.getAuthenticated("/api/v1/contacts"),
                "Failed to poll contacts");
        ContactsResponse response = JsonCodec.fromJson(responseJson, ContactsResponse.class);
        if (response == null || response.getContacts() == null) {
            return;
        }

        java.util.Set<String> seenUsers = new java.util.HashSet<>();
        for (UserSearchResultDTO contact : response.getContacts()) {
            if (contact == null || contact.getUserId() == null || contact.getUserId().isBlank()) {
                continue;
            }
            String userId = contact.getUserId().trim();
            boolean active = contact.isActive();
            seenUsers.add(userId);

            Boolean previous = knownPresenceByUser.put(userId, active);
            if ((previous == null || previous.booleanValue() != active) && messageListener != null) {
                messageListener.onPresenceUpdate(userId, active);
            }
        }

        knownPresenceByUser.keySet().removeIf(userId -> !seenUsers.contains(userId));
    }

    /**
     * Handles incoming JSON message from WebSocket.
     *
     * @param json the JSON string containing EncryptedMessage
     */
    private void handleIncomingMessage(String json) {
        try {
            parseAndProcessEnvelope(json);
        } catch (MessageTamperedException e) {
            acknowledgeTamperedEnvelope(json);
            LOGGER.warn("Undecryptable envelope; acknowledging to prevent endless retries.");
            notifyError(e);
        } catch (KeystoreOperationException e) {
            LOGGER.warn("Keystore decryption failed: {}", e.getMessage());
            notifyError(new IOException("Keystore decryption failed. Incorrect passphrase or corrupted data.", e));
        } catch (MessageValidationException | MessageExpiredException e) {
            LOGGER.debug("Rejected incoming envelope: {}", e.getMessage());
            notifyError(e);
        } catch (Exception e) {
            LOGGER.warn("Failed to process inbound message: {}", e.getMessage());
            notifyError(new IOException("Failed to process message: " + e.getMessage(), e));
        }
    }

    /**
     * Parses the JSON envelope and dispatches the message for processing.
     *
     * @param json the raw JSON string from the WebSocket
     * @return the envelope ID (may be {@code null})
     * @throws Exception when parsing, validation, or decryption fails
     */
    private String parseAndProcessEnvelope(String json) throws Exception {
        java.util.Map<?, ?> envelope = JsonCodec.fromJson(json, java.util.Map.class);
        Object type = envelope.get("type");
        if ("presence".equals(type)) {
            handlePresenceEvent(envelope);
            return null;
        }
        if (!"message".equals(type)) {
            return null;
        }

        // Deduplicate: skip messages we have already processed in this session.
        Object envId = envelope.get("envelopeId");
        String envelopeId = envId != null ? String.valueOf(envId) : null;
        if (envelopeId != null && !seenEnvelopeIds.add(envelopeId)) {
            return envelopeId;
        }

        Object payloadObj = envelope.get("payload");
        if (payloadObj == null) {
            return envelopeId;
        }

        EncryptedMessage encryptedMessage = JsonCodec.fromJson(JsonCodec.toJson(payloadObj),
                EncryptedMessage.class);

        processEncryptedMessage(encryptedMessage, envelopeId);
        return envelopeId;
    }

    /**
     * Forwards an error to the message listener if one is registered.
     *
     * @param error the error to report
     */
    private void notifyError(Throwable error) {
        if (messageListener != null) {
            messageListener.onError(error);
        }
    }

    /**
     * Processes presence envelopes and forwards updates to the registered listener.
     *
     * @param envelope parsed envelope map containing presence fields
     */
    private void handlePresenceEvent(java.util.Map<?, ?> envelope) {
        Object userIdRaw = envelope.get("userId");
        Object activeRaw = envelope.get("active");
        if (userIdRaw == null || activeRaw == null || messageListener == null) {
            return;
        }

        String userId = String.valueOf(userIdRaw);
        boolean active = toBoolean(activeRaw);
        messageListener.onPresenceUpdate(userId, active);
    }

    /**
     * Coerces arbitrary envelope values into boolean flags.
     *
     * @param value raw presence value from parsed JSON
     * @return boolean interpretation of the given value
     */
    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Validates, decrypts, and dispatches a received encrypted envelope.
     *
     * @param encryptedMessage incoming encrypted payload
     * @param envelopeId       server envelope id used for deferred ACK handling
     * @throws Exception when validation or decryption fails
     */
    private void processEncryptedMessage(EncryptedMessage encryptedMessage, String envelopeId) throws Exception {
        // Validate with MessageValidator
        List<MessageValidator.ErrorCode> errors = MessageValidator.validateOrCollectErrors(encryptedMessage);
        if (!errors.isEmpty()) {
            throw new MessageValidationException(errors);
        }

        // Check recipient ID matches local recipient
        validateRecipient(encryptedMessage);

        // Check expiry using ClockProvider (before decrypt)
        validateExpiry(encryptedMessage);

        // Decrypt message (with fallback to older local keys when available)
        byte[] plaintext = decryptWithFallbackKeys(encryptedMessage, envelopeId);

        // Store envelope ID for deferred ACK (keyed by sender)
        String senderId = encryptedMessage.getSenderId();
        if (envelopeId != null) {
            pendingAcks.computeIfAbsent(senderId, k -> new ArrayList<>()).add(envelopeId);
        }

        // Deliver to MessageListener
        if (messageListener != null) {
            messageListener.onMessage(plaintext,
                    senderId,
                    encryptedMessage.getContentType(),
                    encryptedMessage.getTimestampEpochMs(),
                    envelopeId);
        }
    }

    /**
     * Attempts message decryption with current private key and falls back to older
     * local keys when AEAD verification fails.
     *
     * @param encryptedMessage encrypted message to decrypt
     * @param envelopeId       envelope id for diagnostic logging
     * @return decrypted plaintext bytes
     * @throws Exception when all decryption attempts fail
     */
    private byte[] decryptWithFallbackKeys(EncryptedMessage encryptedMessage, String envelopeId) throws Exception {
        PrivateKey privateKey = loadLocalPrivateKey();
        MessageDecryptor decryptor = new MessageDecryptor(privateKey, clockProvider);
        try {
            return decryptor.decryptMessage(encryptedMessage);
        } catch (MessageTamperedException firstFailure) {
            if (!(keyProvider instanceof UserKeystoreKeyProvider userKeyProvider)) {
                throw firstFailure;
            }

            List<KeyMetadata> metadataList = loadSortedKeyMetadata(userKeyProvider);
            int fallbackAttempts = 0;
            for (KeyMetadata metadata : metadataList) {
                if (metadata == null || metadata.keyId() == null || metadata.keyId().isBlank()) {
                    continue;
                }
                try {
                    fallbackAttempts++;
                    PrivateKey candidate = userKeyProvider.getKeyStore()
                            .loadPrivate(metadata.keyId(), userKeyProvider.getPassphrase());
                    MessageDecryptor candidateDecryptor = new MessageDecryptor(candidate, clockProvider);
                    byte[] plaintext = candidateDecryptor.decryptMessage(encryptedMessage);
                    LOGGER.warn(
                            "Envelope {} decrypted with fallback key {} ({}). Current key likely does not match message keying material.",
                            new Object[] { envelopeId, metadata.keyId(), metadata.status() });
                    return plaintext;
                } catch (MessageTamperedException ignored) {
                    // Keep trying other local keys.
                } catch (Exception keyLoadOrDecryptError) {
                    LOGGER.debug(
                            "Skipping fallback key {} while decrypting envelope {}: {}",
                            new Object[] { metadata.keyId(), envelopeId, keyLoadOrDecryptError.getMessage() });
                }
            }

            LOGGER.warn(
                    "AEAD verification failed for envelope {}. Tried current key plus {} fallback keys. sender={}, recipient={}, timestamp={}, ttl={}",
                    new Object[] {
                            envelopeId,
                            fallbackAttempts,
                            encryptedMessage.getSenderId(),
                            encryptedMessage.getRecipientId(),
                            encryptedMessage.getTimestampEpochMs(),
                            encryptedMessage.getTtlSeconds()
                    });
            throw firstFailure;
        }
    }

    /**
     * Loads key metadata and sorts it so fallback attempts prefer current/recent
     * keys first.
     *
     * @param userKeyProvider key provider exposing keystore metadata
     * @return sorted metadata list, or empty list when metadata cannot be loaded
     */
    private List<KeyMetadata> loadSortedKeyMetadata(UserKeystoreKeyProvider userKeyProvider) {
        try {
            List<KeyMetadata> metadataList = new ArrayList<>(userKeyProvider.getKeyStore().listMetadata());
            metadataList.sort(Comparator
                    .comparing((KeyMetadata m) -> !"CURRENT".equalsIgnoreCase(m.status()))
                    .thenComparing(Comparator.comparingLong(KeyMetadata::createdAtEpochSec).reversed()));
            return metadataList;
        } catch (Exception e) {
            LOGGER.debug("Unable to enumerate fallback keys: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Verifies that a message has not expired based on timestamp + TTL.
     *
     * @param msg incoming encrypted message metadata
     * @throws MessageExpiredException when message expiry time is in the past
     */
    private void validateExpiry(EncryptedMessage msg) throws MessageExpiredException {
        long now = clockProvider.currentTimeMillis();
        long expiryTime = msg.getTimestampEpochMs() + msg.getTtlSeconds() * 1000L;
        if (now > expiryTime) {
            throw new MessageExpiredException("Message expired at " + now);
        }
    }

    /**
     * Decrypts a detached encrypted message without requiring websocket envelope
     * context.
     *
     * @param encryptedMessage encrypted payload to decrypt
     * @return decrypted plaintext bytes
     * @throws Exception when validation/decryption fails
     */
    @Override
    public byte[] decryptDetachedMessage(EncryptedMessage encryptedMessage)
            throws com.haf.shared.exceptions.MessageDecryptionException {
        if (encryptedMessage == null) {
            throw new IllegalArgumentException("encryptedMessage is required");
        }
        try {
            validateRecipient(encryptedMessage);
            validateExpiry(encryptedMessage);
            return decryptWithFallbackKeys(encryptedMessage, null);
        } catch (com.haf.shared.exceptions.MessageDecryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new com.haf.shared.exceptions.MessageDecryptionException("Failed to decrypt detached message", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void acknowledgeEnvelopes(String senderId) {
        if (senderId == null) {
            return;
        }
        if (!webSocketAdapter.isConnected() && !httpPollingActive) {
            LOGGER.debug("Deferring ACK for sender {} while websocket is disconnected", senderId);
            return;
        }
        List<String> ids = pendingAcks.remove(senderId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            sendAck(ids);
        } catch (IOException e) {
            if (isDisconnectedError(e)) {
                LOGGER.debug("Deferring ACK for sender {} until websocket reconnects", senderId);
            } else {
                LOGGER.warn("Failed to send ACK for sender {}", senderId, e);
            }
            // Put them back so we can try again later
            pendingAcks.computeIfAbsent(senderId, k -> new ArrayList<>()).addAll(ids);
        }
    }

    /**
     * Detects send failures caused by websocket disconnect races.
     *
     * @param error send failure to inspect
     * @return {@code true} when the exception chain indicates disconnected
     *         transport
     */
    private static boolean isDisconnectedError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("not connected")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Validates that the message is addressed to the local recipient.
     * Notifies the listener and returns early if the ID does not match.
     *
     * @param encryptedMessage the message to validate
     * @throws MessageValidationException if the recipient ID does not match
     */
    private void validateRecipient(EncryptedMessage encryptedMessage)
            throws MessageValidationException {
        try {
            MessageValidator.validateRecipientOrThrow(localRecipientId, encryptedMessage);
        } catch (IllegalArgumentException e) {
            throw new MessageValidationException("Recipient ID mismatch: " + e.getMessage(), List.of());
        }
    }

    /**
     * Handles errors from WebSocket.
     *
     * @param error the error that occurred
     */
    private void handleError(Throwable error) {
        LOGGER.warn("WebSocket receiver error: {}", error.getMessage());
        if (messageListener != null) {
            messageListener.onError(error);
        }
    }

    /**
     * Loads the local private key from the keystore.
     *
     * @return the private key
     * @throws Exception if the key cannot be loaded
     */
    private PrivateKey loadLocalPrivateKey() throws Exception {
        if (keyProvider instanceof UserKeystoreKeyProvider userKeyProvider) {
            char[] passphrase = userKeyProvider.getPassphrase();
            return userKeyProvider.getKeyStore().loadCurrentPrivate(passphrase);
        }
        throw new IllegalStateException("KeyProvider must be UserKeystoreKeyProvider to load private keys");
    }

    /**
     * Sends an acknowledgement for all provided envelope IDs.
     *
     * @param envelopeIds envelope ids to acknowledge
     * @throws IOException when websocket send fails
     */
    private void sendAck(List<String> envelopeIds) throws IOException {
        if (envelopeIds == null || envelopeIds.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("{\"envelopeIds\":[");
        for (int i = 0; i < envelopeIds.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('\"').append(envelopeIds.get(i)).append('\"');
        }
        sb.append("]}");
        if (webSocketAdapter.isConnected()) {
            webSocketAdapter.sendText(sb.toString());
            return;
        }

        if (!httpPollingActive) {
            throw new IOException("Messaging transport is not connected");
        }
        requestWithIoMapping(
                webSocketAdapter.postAuthenticated("/api/v1/messages/ack", sb.toString()),
                "Failed to acknowledge mailbox envelopes");
    }

    /**
     * Acknowledges tampered envelopes immediately so server-side retry queues do
     * not
     * replay them endlessly.
     *
     * @param rawEnvelopeJson raw websocket envelope JSON
     */
    private void acknowledgeTamperedEnvelope(String rawEnvelopeJson) {
        String envelopeId = extractEnvelopeId(rawEnvelopeJson);
        if (envelopeId == null || envelopeId.isBlank()) {
            return;
        }
        try {
            sendAck(List.of(envelopeId));
        } catch (IOException ackError) {
            LOGGER.warn("Failed to acknowledge undecryptable envelope {}", envelopeId, ackError);
        }
    }

    /**
     * Extracts the envelope id from raw websocket envelope JSON.
     *
     * @param rawEnvelopeJson websocket message payload
     * @return envelope id or {@code null} when not available
     */
    private String extractEnvelopeId(String rawEnvelopeJson) {
        if (rawEnvelopeJson == null || rawEnvelopeJson.isBlank()) {
            return null;
        }
        try {
            java.util.Map<?, ?> envelope = JsonCodec.fromJson(rawEnvelopeJson, java.util.Map.class);
            Object envelopeId = envelope.get("envelopeId");
            return envelopeId == null ? null : String.valueOf(envelopeId);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Resolves async HTTP helper futures while normalizing nested exceptions into
     * IOExceptions.
     *
     * @param future  asynchronous HTTP future
     * @param message fallback error message
     * @return completed future body
     * @throws IOException when request fails
     */
    private String requestWithIoMapping(CompletableFuture<String> future, String message) throws IOException {
        try {
            return future.join();
        } catch (CompletionException completionException) {
            Throwable cause = completionException.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException(message, cause != null ? cause : completionException);
        } catch (Exception exception) {
            throw new IOException(message, exception);
        }
    }
}
