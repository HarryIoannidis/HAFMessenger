package com.haf.client.network;

import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.shared.crypto.MessageDecryptor;
import com.haf.shared.crypto.MessageSignatureService;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.dto.KeyMetadata;
import com.haf.shared.exceptions.KeystoreOperationException;
import com.haf.shared.exceptions.MessageExpiredException;
import com.haf.shared.exceptions.MessageTamperedException;
import com.haf.shared.exceptions.MessageValidationException;
import com.haf.shared.exceptions.MessageDecryptionException;
import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.MessageValidator;
import com.haf.shared.utils.SigningKeyIO;
import com.haf.shared.websocket.RealtimeEvent;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles inbound WSS encrypted messages, decryption, and read/delivery
 * receipt flow.
 */
public class DefaultMessageReceiver implements MessageReceiver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMessageReceiver.class);

    private final KeyProvider keyProvider;
    private final ClockProvider clockProvider;
    private final RealtimeTransport realtimeTransport;
    private final String localRecipientId;
    private MessageListener messageListener;

    private final Set<String> seenEnvelopeIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentHashMap<String, List<String>> pendingReadReceipts = new ConcurrentHashMap<>();

    /**
     * Creates a WSS-only message receiver.
     *
     * @param keyProvider       local key provider
     * @param clockProvider     clock provider for expiry checks
     * @param localRecipientId  local user id
     * @param realtimeTransport authenticated WSS transport
     */
    public DefaultMessageReceiver(
            KeyProvider keyProvider,
            ClockProvider clockProvider,
            String localRecipientId,
            RealtimeTransport realtimeTransport) {
        this.keyProvider = Objects.requireNonNull(keyProvider, "keyProvider");
        this.clockProvider = Objects.requireNonNull(clockProvider, "clockProvider");
        this.localRecipientId = Objects.requireNonNull(localRecipientId, "localRecipientId");
        this.realtimeTransport = Objects.requireNonNull(realtimeTransport, "realtimeTransport");
    }

    /**
     * Sets the listener for incoming messages and real-time events.
     *
     * @param listener the listener to receive message callbacks
     */
    @Override
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * Connects the underlying real-time transport and begins listening for events.
     *
     * @throws IOException           if a network error occurs during connection
     * @throws IllegalStateException if no MessageListener has been set
     */
    @Override
    public void start() throws IOException {
        if (messageListener == null) {
            throw new IllegalStateException("MessageListener must be set before starting");
        }
        realtimeTransport.setEventListener(this::handleRealtimeEvent);
        realtimeTransport.setErrorListener(this::notifyError);
        realtimeTransport.start();
    }

    /**
     * Disconnects the real-time transport and stops receiving events.
     */
    @Override
    public void stop() {
        realtimeTransport.close();
    }

    /**
     * Forces a reconnection of the underlying real-time transport.
     *
     * @throws IOException if the reconnection fails
     */
    @Override
    public void reconnect() throws IOException {
        realtimeTransport.setEventListener(this::handleRealtimeEvent);
        realtimeTransport.setErrorListener(this::notifyError);
        realtimeTransport.reconnect();
    }

    /**
     * Primary event handler for routing incoming WebSocket events to the listener.
     *
     * @param event the parsed real-time event
     */
    private void handleRealtimeEvent(RealtimeEvent event) {
        if (event == null || event.eventType() == null) {
            return;
        }
        MessageListener listener = this.messageListener;
        if (listener == null) {
            switch (event.eventType()) {
                case NEW_MESSAGE -> handleIncomingRealtimeMessage(event);
                case ERROR ->
                    notifyError(new IOException(event.getError() == null ? "Realtime error" : event.getError()));
                default -> {
                    // No client action is required for server acknowledgements here.
                }
            }
            return;
        }
        switch (event.eventType()) {
            case NEW_MESSAGE -> handleIncomingRealtimeMessage(event);
            case PRESENCE_UPDATE -> listener.onPresenceUpdate(event.getSenderId(), event.isActive());
            case MESSAGE_DELIVERED -> listener.onMessageDelivered(event.getSenderId(), normalizeEnvelopeIds(event));
            case MESSAGE_READ -> listener.onMessageRead(event.getSenderId(), normalizeEnvelopeIds(event));
            case TYPING_START -> listener.onTyping(event.getSenderId(), true);
            case TYPING_STOP -> listener.onTyping(event.getSenderId(), false);
            case ERROR -> notifyError(new IOException(event.getError() == null ? "Realtime error" : event.getError()));
            default -> {
                // No client action is required for server acknowledgements here.
            }
        }
    }

    /**
     * Routes an incoming encrypted message envelope through parsing, decryption,
     * and validation.
     *
     * @param event the event containing the encrypted payload
     */
    private void handleIncomingRealtimeMessage(RealtimeEvent event) {
        try {
            parseAndProcessRealtimeEnvelope(event);
        } catch (MessageTamperedException e) {
            acknowledgeTamperedEnvelope(event.getEnvelopeId(), event.getSenderId());
            LOGGER.warn("Undecryptable realtime envelope; acknowledging to prevent endless retries.");
            notifyError(e);
        } catch (KeystoreOperationException e) {
            LOGGER.warn("Keystore decryption failed: {}", e.getMessage());
            notifyError(new IOException("Keystore decryption failed. Incorrect passphrase or corrupted data.", e));
        } catch (MessageValidationException | MessageExpiredException e) {
            LOGGER.debug("Rejected realtime envelope: {}", e.getMessage());
            notifyError(e);
        } catch (Exception e) {
            LOGGER.warn("Failed to process realtime message: {}", e.getMessage());
            notifyError(new IOException("Failed to process message: " + e.getMessage(), e));
        }
    }

    /**
     * Deduplicates, extracts, and initiates processing of an incoming message
     * envelope.
     *
     * @param event the real-time event wrapper
     * @return the deduplicated envelope ID, or null if unidentifiable
     * @throws Exception if processing fails at any stage
     */
    private String parseAndProcessRealtimeEnvelope(RealtimeEvent event) throws Exception {
        String envelopeId = event.getEnvelopeId();
        if (envelopeId != null && !seenEnvelopeIds.add(envelopeId)) {
            return envelopeId;
        }
        EncryptedMessage encryptedMessage = event.getEncryptedMessage();
        if (encryptedMessage == null) {
            return envelopeId;
        }
        processEncryptedMessage(encryptedMessage, envelopeId);
        return envelopeId;
    }

    /**
     * Propagates a fatal or unhandled error to the registered message listener.
     *
     * @param error the throwable error
     */
    private void notifyError(Throwable error) {
        if (messageListener != null) {
            messageListener.onError(error);
        }
    }

    /**
     * Full decryption and verification pipeline for an incoming encrypted message.
     *
     * @param encryptedMessage the validated encrypted payload
     * @param envelopeId       the tracking ID of the payload's envelope
     * @throws Exception if validation, signature checks, or decryption fails
     */
    private void processEncryptedMessage(EncryptedMessage encryptedMessage, String envelopeId) throws Exception {
        List<MessageValidator.ErrorCode> errors = MessageValidator.validateOrCollectErrors(encryptedMessage);
        if (!errors.isEmpty()) {
            throw new MessageValidationException(errors);
        }

        validateRecipient(encryptedMessage);
        validateExpiry(encryptedMessage);
        verifySignature(encryptedMessage);
        byte[] plaintext = decryptWithFallbackKeys(encryptedMessage, envelopeId);

        String senderId = encryptedMessage.getSenderId();
        if (envelopeId != null) {
            pendingReadReceipts.computeIfAbsent(senderId, ignored -> new ArrayList<>()).add(envelopeId);
            sendDeliveryReceipt(envelopeId, senderId);
        }

        if (messageListener != null) {
            messageListener.onMessage(plaintext,
                    senderId,
                    encryptedMessage.getContentType(),
                    encryptedMessage.getTimestampEpochMs(),
                    envelopeId);
        }
    }

    /**
     * Attempts decryption using the current private key, falling back to older keys
     * if tampered.
     *
     * @param encryptedMessage the target payload
     * @param envelopeId       the tracking ID for logging
     * @return the decrypted plaintext bytes
     * @throws Exception if all decryption attempts fail
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
                    // Try the next known local key.
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
     * Loads fallback key identities sorted by descending chronological creation
     * order.
     *
     * @param userKeyProvider the keystore provider
     * @return an ordered list of fallback key metadata
     */
    private List<KeyMetadata> loadSortedKeyMetadata(UserKeystoreKeyProvider userKeyProvider) {
        try {
            List<KeyMetadata> metadataList = new ArrayList<>(userKeyProvider.getKeyStore().listMetadata());
            metadataList.sort(Comparator
                    .comparing((KeyMetadata metadata) -> !"CURRENT".equalsIgnoreCase(metadata.status()))
                    .thenComparing(Comparator.comparingLong(KeyMetadata::createdAtEpochSec).reversed()));
            return metadataList;
        } catch (Exception e) {
            LOGGER.debug("Unable to enumerate fallback keys: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Ensures the incoming message has not exceeded its designated time-to-live.
     *
     * @param message the target encrypted message
     * @throws MessageExpiredException if the TTL has passed
     */
    private void validateExpiry(EncryptedMessage message) throws MessageExpiredException {
        long now = clockProvider.currentTimeMillis();
        long expiryTime = message.getTimestampEpochMs() + message.getTtlSeconds() * 1000L;
        if (now > expiryTime) {
            throw new MessageExpiredException("Message expired at " + now);
        }
    }

    /**
     * Decrypts an isolated encrypted message that is not part of the active
     * real-time stream.
     *
     * @param encryptedMessage the standalone message payload
     * @return the decrypted plaintext bytes
     * @throws MessageDecryptionException if structural
     *                                    validation,
     *                                    signature, or
     *                                    decryption fails
     */
    @Override
    public byte[] decryptDetachedMessage(EncryptedMessage encryptedMessage)
            throws MessageDecryptionException {
        if (encryptedMessage == null) {
            throw new IllegalArgumentException("encryptedMessage is required");
        }
        try {
            validateRecipient(encryptedMessage);
            validateExpiry(encryptedMessage);
            verifySignature(encryptedMessage);
            return decryptWithFallbackKeys(encryptedMessage, null);
        } catch (MessageDecryptionException e) {
            throw e;
        } catch (Exception e) {
            throw new MessageDecryptionException("Failed to decrypt detached message", e);
        }
    }

    /**
     * Authenticates the sender by verifying the message's cryptographic signature.
     *
     * @param encryptedMessage the inbound payload
     * @throws Exception if the signature is invalid or sender material is
     *                   unidentifiable
     */
    private void verifySignature(EncryptedMessage encryptedMessage) throws Exception {
        String senderId = encryptedMessage.getSenderId();
        if (senderId == null || senderId.isBlank()) {
            throw new MessageTamperedException("Missing sender identity");
        }

        PublicKey senderSigningPublic = keyProvider.getRecipientSigningPublicKey(senderId);
        String calculatedFingerprint = FingerprintUtil.sha256Hex(SigningKeyIO.publicDer(senderSigningPublic));
        String providedFingerprint = encryptedMessage.getSenderSigningKeyFingerprint();
        if (providedFingerprint == null
                || providedFingerprint.isBlank()
                || !calculatedFingerprint.equalsIgnoreCase(providedFingerprint.trim())) {
            throw new MessageTamperedException("Sender signing key fingerprint mismatch");
        }
        if (!MessageSignatureService.verify(encryptedMessage, senderSigningPublic)) {
            throw new MessageTamperedException("Invalid message signature");
        }
    }

    /**
     * Flushes pending read receipts back to a specific sender over the transport.
     *
     * @param senderId the target sender ID
     */
    @Override
    public void acknowledgeEnvelopes(String senderId) {
        if (senderId == null) {
            return;
        }
        List<String> ids = pendingReadReceipts.remove(senderId);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            realtimeTransport.sendReadReceipt(ids, senderId);
        } catch (IOException e) {
            LOGGER.warn("Failed to send read receipt for sender {}", senderId, e);
            pendingReadReceipts.computeIfAbsent(senderId, ignored -> new ArrayList<>()).addAll(ids);
        }
    }

    /**
     * Emits a typing-start realtime event for a recipient via the realtime
     * transport.
     *
     * @param recipientId the ID of the recipient
     */
    @Override
    public void sendTypingStart(String recipientId) {
        try {
            realtimeTransport.sendTypingStart(recipientId);
        } catch (IOException e) {
            LOGGER.debug("Failed to send typing-start event: {}", e.getMessage());
        }
    }

    /**
     * Emits a typing-stop realtime event for a recipient via the realtime
     * transport.
     *
     * @param recipientId the ID of the recipient
     */
    @Override
    public void sendTypingStop(String recipientId) {
        try {
            realtimeTransport.sendTypingStop(recipientId);
        } catch (IOException e) {
            LOGGER.debug("Failed to send typing-stop event: {}", e.getMessage());
        }
    }

    /**
     * Validates that the message is intended for the local user.
     *
     * @param encryptedMessage the incoming payload
     * @throws MessageValidationException if the recipient ID does not match the
     *                                    local ID
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
     * Extracts the active private key from the local keystore for primary
     * decryption.
     *
     * @return the current private key
     * @throws Exception if the key cannot be extracted or unlocked
     */
    private PrivateKey loadLocalPrivateKey() throws Exception {
        if (keyProvider instanceof UserKeystoreKeyProvider userKeyProvider) {
            char[] passphrase = userKeyProvider.getPassphrase();
            return userKeyProvider.getKeyStore().loadCurrentPrivate(passphrase);
        }
        throw new IllegalStateException("KeyProvider must be UserKeystoreKeyProvider to load private keys");
    }

    /**
     * Sends a delivery receipt for a specific envelope.
     *
     * @param envelopeId the envelope ID
     * @param senderId   the sender ID
     */
    private void sendDeliveryReceipt(String envelopeId, String senderId) {
        if (envelopeId == null || envelopeId.isBlank()) {
            return;
        }
        try {
            realtimeTransport.sendDeliveryReceipt(List.of(envelopeId), senderId);
        } catch (IOException e) {
            LOGGER.debug("Failed to send delivery receipt for envelope {}: {}", envelopeId, e.getMessage());
        }
    }

    /**
     * Acknowledges a tampered envelope by sending a delivery receipt to prevent
     * endless replays.
     *
     * @param envelopeId the envelope ID
     * @param senderId   the sender ID
     */
    private void acknowledgeTamperedEnvelope(String envelopeId, String senderId) {
        if (envelopeId == null || envelopeId.isBlank()) {
            return;
        }
        sendDeliveryReceipt(envelopeId, senderId);
    }

    /**
     * Normalizes envelope IDs from a realtime event.
     *
     * @param event the realtime event
     * @return a list of normalized envelope IDs
     */
    private static List<String> normalizeEnvelopeIds(RealtimeEvent event) {
        if (event == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        if (event.getEnvelopeId() != null && !event.getEnvelopeId().isBlank()) {
            ids.add(event.getEnvelopeId().trim());
        }
        if (event.getEnvelopeIds() != null) {
            for (String id : event.getEnvelopeIds()) {
                if (id != null && !id.isBlank()) {
                    ids.add(id.trim());
                }
            }
        }
        return ids.stream().distinct().toList();
    }
}
