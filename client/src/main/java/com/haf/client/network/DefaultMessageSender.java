package com.haf.client.network;

import com.haf.shared.crypto.MessageEncryptor;
import com.haf.shared.requests.AttachmentBindRequest;
import com.haf.shared.responses.AttachmentBindResponse;
import com.haf.shared.requests.AttachmentChunkRequest;
import com.haf.shared.responses.AttachmentChunkResponse;
import com.haf.shared.requests.AttachmentCompleteRequest;
import com.haf.shared.responses.AttachmentCompleteResponse;
import com.haf.shared.responses.AttachmentDownloadResponse;
import com.haf.shared.requests.AttachmentInitRequest;
import com.haf.shared.responses.AttachmentInitResponse;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.responses.MessagingPolicyResponse;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.MessageValidationException;
import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.MessageValidator;

import java.io.IOException;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

public class DefaultMessageSender implements MessageSender {
    private final KeyProvider keyProvider;
    private final ClockProvider clockProvider;
    private final WebSocketAdapter webSocketAdapter;

    /**
     * Creates a DefaultMessageSender with the specified dependencies.
     *
     * @param keyProvider      the key provider for retrieving recipient public keys
     * @param clockProvider    the clock provider for deterministic timestamps
     * @param webSocketAdapter the WebSocket adapter for network communication
     */
    public DefaultMessageSender(KeyProvider keyProvider, ClockProvider clockProvider, WebSocketAdapter webSocketAdapter) {
        this.keyProvider = keyProvider;
        this.clockProvider = clockProvider;
        this.webSocketAdapter = webSocketAdapter;
    }

    /**
     * Sends a message to the specified recipient.
     *
     * @param payload      the plaintext bytes to send
     * @param recipientId  the recipient's identifier
     * @param contentType  the MIME content type of the payload
     * @param ttlSeconds   the time-to-live in seconds
     * @throws MessageValidationException if the message is not valid
     * @throws KeyNotFoundException       if the key is not found
     * @throws IOException                if the message cannot be sent
     */
    @Override
    public void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds)
            throws MessageValidationException, KeyNotFoundException, IOException {
        sendMessageWithResult(payload, recipientId, contentType, ttlSeconds);
    }

    @Override
    public SendResult sendMessageWithResult(byte[] payload, String recipientId, String contentType, long ttlSeconds)
            throws MessageValidationException, KeyNotFoundException, IOException {
        EncryptedMessage encryptedMessage = encryptMessage(payload, recipientId, contentType, ttlSeconds);
        return sendEncryptedMessage(encryptedMessage);
    }

    @Override
    public EncryptedMessage encryptMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds)
            throws MessageValidationException, KeyNotFoundException, IOException {

        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("RecipientId cannot be null or empty");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("ContentType cannot be null or empty");
        }

        try {
            String senderId = keyProvider.getSenderId();
            PublicKey recipientPublicKey = keyProvider.getRecipientPublicKey(recipientId);
            MessageEncryptor encryptor = new MessageEncryptor(recipientPublicKey, senderId, recipientId, clockProvider);
            EncryptedMessage encryptedMessage = encryptor.encrypt(payload, contentType, ttlSeconds);

            List<MessageValidator.ErrorCode> errors = MessageValidator.validateOrCollectErrors(encryptedMessage);
            if (!errors.isEmpty()) {
                throw new MessageValidationException(errors);
            }
            return encryptedMessage;
        } catch (KeyNotFoundException | MessageValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to encrypt message", e);
        }
    }

    @Override
    public SendResult sendEncryptedMessage(EncryptedMessage encryptedMessage) throws IOException {
        if (encryptedMessage == null) {
            throw new IllegalArgumentException("Encrypted message cannot be null");
        }

        String json = JsonCodec.toJson(encryptedMessage);
        String response;
        try {
            response = webSocketAdapter.postAuthenticated("/api/v1/messages", json).join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to send encrypted message", cause != null ? cause : ex);
        }
        Map<?, ?> map = JsonCodec.fromJson(response, Map.class);
        String envelopeId = map.get("envelopeId") == null ? null : String.valueOf(map.get("envelopeId"));

        long expiresAt = 0;
        Object expiresAtObj = map.get("expiresAt");
        if (expiresAtObj instanceof Number number) {
            expiresAt = number.longValue();
        } else if (expiresAtObj != null) {
            try {
                expiresAt = Long.parseLong(String.valueOf(expiresAtObj));
            } catch (NumberFormatException ignored) {
                expiresAt = 0;
            }
        }

        return new SendResult(envelopeId, expiresAt);
    }

    @Override
    public MessagingPolicyResponse fetchMessagingPolicy() throws IOException {
        return decodeResponse(webSocketAdapter.getAuthenticated("/api/v1/config/messaging"), MessagingPolicyResponse.class);
    }

    @Override
    public AttachmentInitResponse initAttachmentUpload(AttachmentInitRequest request) throws IOException {
        String body = JsonCodec.toJson(request);
        return decodeResponse(webSocketAdapter.postAuthenticated("/api/v1/attachments/init", body),
                AttachmentInitResponse.class);
    }

    @Override
    public AttachmentChunkResponse uploadAttachmentChunk(String attachmentId, AttachmentChunkRequest request)
            throws IOException {
        String body = JsonCodec.toJson(request);
        return decodeResponse(webSocketAdapter.postAuthenticated("/api/v1/attachments/" + attachmentId + "/chunk", body),
                AttachmentChunkResponse.class);
    }

    @Override
    public AttachmentCompleteResponse completeAttachmentUpload(String attachmentId, AttachmentCompleteRequest request)
            throws IOException {
        String body = JsonCodec.toJson(request);
        return decodeResponse(
                webSocketAdapter.postAuthenticated("/api/v1/attachments/" + attachmentId + "/complete", body),
                AttachmentCompleteResponse.class);
    }

    @Override
    public AttachmentBindResponse bindAttachmentUpload(String attachmentId, AttachmentBindRequest request)
            throws IOException {
        String body = JsonCodec.toJson(request);
        return decodeResponse(webSocketAdapter.postAuthenticated("/api/v1/attachments/" + attachmentId + "/bind", body),
                AttachmentBindResponse.class);
    }

    @Override
    public AttachmentDownloadResponse downloadAttachment(String attachmentId) throws IOException {
        return decodeResponse(webSocketAdapter.getAuthenticated("/api/v1/attachments/" + attachmentId),
                AttachmentDownloadResponse.class);
    }

    private <T> T decodeResponse(java.util.concurrent.CompletableFuture<String> future, Class<T> type) throws IOException {
        try {
            String response = future.join();
            return JsonCodec.fromJson(response, type);
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to decode server response", cause != null ? cause : ex);
        } catch (Exception ex) {
            throw new IOException("Failed to decode server response", ex);
        }
    }
}
