package com.haf.client.network;

import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.crypto.MessageEncryptor;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.MessageValidationException;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.MessageValidator;
import java.io.IOException;
import java.security.PublicKey;
import java.util.List;

public class DefaultMessageSender implements MessageSender {
    private final KeyProvider keyProvider;
    private final ClockProvider clockProvider;
    private final WebSocketAdapter webSocketAdapter;

    /**
     * Creates a DefaultMessageSender with the specified dependencies.
     *
     * @param keyProvider the key provider for retrieving recipient public keys
     * @param clockProvider the clock provider for deterministic timestamps
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
     * @param payload the plaintext bytes to send
     * @param recipientId the recipient's identifier
     * @param contentType the MIME content type of the payload
     * @param ttlSeconds the time-to-live in seconds
     * @throws MessageValidationException if the message is not valid
     * @throws KeyNotFoundException if the key is not found
     * @throws IOException if the message cannot be sent
     */
    @Override
    public void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds) 
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
            // 1. Resolve sender ID from KeyProvider
            String senderId = keyProvider.getSenderId();

            // 2. Get recipient public key from KeyProvider
            PublicKey recipientPublicKey = keyProvider.getRecipientPublicKey(recipientId);

            // 3. Create MessageEncryptor with recipient key, sender ID, recipient ID
            MessageEncryptor encryptor = new MessageEncryptor(recipientPublicKey, senderId, recipientId, clockProvider);

            // 4. Encrypt payload to get EncryptedMessage
            EncryptedMessage encryptedMessage = encryptor.encrypt(payload, contentType, ttlSeconds);

            // 5. Validate with MessageValidator
            List<MessageValidator.ErrorCode> errors = MessageValidator.validateOrCollectErrors(encryptedMessage);
            if (!errors.isEmpty()) {
                throw new MessageValidationException(errors);
            }

            // 6. Serialize to JSON with JsonCodec
            String json = JsonCodec.toJson(encryptedMessage);

            // 7. Send via WebSocketAdapter
            if (!webSocketAdapter.isConnected()) {
                throw new IOException("WebSocket is not connected");
            }
            webSocketAdapter.sendText(json);

        } catch (KeyNotFoundException | MessageValidationException | IOException e) {
            // Re-throw these exceptions as-is
            throw e;
        } catch (Exception e) {
            // Wrap other exceptions
            throw new IOException("Failed to send message", e);
        }
    }
}

