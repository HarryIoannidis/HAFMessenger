package com.haf.client.network;

import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.crypto.MessageDecryptor;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.exceptions.MessageExpiredException;
import com.haf.shared.exceptions.MessageTamperedException;
import com.haf.shared.exceptions.MessageValidationException;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.MessageValidator;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.List;

public class DefaultMessageReceiver implements MessageReceiver {
    private final KeyProvider keyProvider;
    private final ClockProvider clockProvider;
    private final WebSocketAdapter webSocketAdapter;
    private final String localRecipientId;
    private MessageListener messageListener;

    /**
     * Creates a DefaultMessageReceiver with the specified dependencies.
     * @param keyProvider the key provider (must be UserKeystoreKeyProvider to access keystore)
     * @param clockProvider the clock provider for deterministic expiry checks
     * @param webSocketAdapter the WebSocket adapter for network communication
     * @param localRecipientId the local recipient's ID (must match message recipientId)
     */
    public DefaultMessageReceiver(KeyProvider keyProvider, ClockProvider clockProvider, 
                                  WebSocketAdapter webSocketAdapter, String localRecipientId) {
        this.keyProvider = keyProvider;
        this.clockProvider = clockProvider;
        this.webSocketAdapter = webSocketAdapter;
        this.localRecipientId = localRecipientId;
    }

    /**
     * Sets the message listener for incoming messages.
     * @param listener the message listener
     */
    @Override
    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * Starts the message receiver.
     * @throws IOException if the connection cannot be established
     */
    @Override
    public void start() throws IOException {
        if (messageListener == null) {
            throw new IllegalStateException("MessageListener must be set before starting");
        }

        // Register message handler with WebSocketAdapter
        webSocketAdapter.connect(
            this::handleIncomingMessage,
            this::handleError
        );
    }

    /**
     * Stops the message receiver.
     */
    @Override
    public void stop() {
        webSocketAdapter.close();
    }

    /**
     * Handles incoming JSON message from WebSocket.
     * @param json the JSON string containing EncryptedMessage
     */
    private void handleIncomingMessage(String json) {
        try {
            // 1. Deserialize with JsonCodec
            EncryptedMessage encryptedMessage = JsonCodec.fromJson(json, EncryptedMessage.class);

            // 2. Validate with MessageValidator
            List<MessageValidator.ErrorCode> errors = MessageValidator.validateOrCollectErrors(encryptedMessage);
            if (!errors.isEmpty()) {
                if (messageListener != null) {
                    messageListener.onError(new MessageValidationException(errors));
                }
                return;
            }

            // 3. Check recipient ID matches local recipient
            try {
                MessageValidator.validateRecipientOrThrow(localRecipientId, encryptedMessage);
            } catch (IllegalArgumentException e) {
                if (messageListener != null) {
                    messageListener.onError(new MessageValidationException("Recipient ID mismatch: " + e.getMessage(), List.of()));
                }
                return;
            }

            // 4. Check expiry using ClockProvider (before decrypt)
            long now = clockProvider.currentTimeMillis();
            if (now > encryptedMessage.timestampEpochMs + encryptedMessage.ttlSeconds * 1000L) {
                if (messageListener != null) {
                    messageListener.onError(new MessageExpiredException("Message expired at " + now));
                }
                return;
            }

            // 5. Load local private key from UserKeystore
            PrivateKey privateKey = loadLocalPrivateKey();

            // 6. Create MessageDecryptor with private key and ClockProvider
            MessageDecryptor decryptor = new MessageDecryptor(privateKey, clockProvider);

            // 7. Decrypt to get plaintext bytes
            byte[] plaintext = decryptor.decryptMessage(encryptedMessage);

            // 8. Extract senderId, contentType from EncryptedMessage
            String senderId = encryptedMessage.senderId;
            String contentType = encryptedMessage.contentType;

            // 9. Deliver to MessageListener
            if (messageListener != null) {
                messageListener.onMessage(plaintext, senderId, contentType);
            }

        } catch (MessageValidationException e) {
            if (messageListener != null) {
                messageListener.onError(e);
            }
        } catch (MessageExpiredException e) {
            if (messageListener != null) {
                messageListener.onError(e);
            }
        } catch (MessageTamperedException e) {
            if (messageListener != null) {
                messageListener.onError(e);
            }
        } catch (Exception e) {
            if (messageListener != null) {
                messageListener.onError(new IOException("Failed to process message", e));
            }
        }
    }

    /**
     * Handles errors from WebSocket.
     * @param error the error that occurred
     */
    private void handleError(Throwable error) {
        if (messageListener != null) {
            messageListener.onError(error);
        }
    }

    /**
     * Loads the local private key from the keystore.
     * @return the private key
     * @throws Exception if the key cannot be loaded
     */
    private PrivateKey loadLocalPrivateKey() throws Exception {
        if (keyProvider instanceof UserKeystoreKeyProvider) {
            UserKeystoreKeyProvider userKeyProvider = (UserKeystoreKeyProvider) keyProvider;
            char[] passphrase = userKeyProvider.getPassphrase();
            return userKeyProvider.getKeyStore().loadCurrentPrivate(passphrase);
        } else {
            throw new IllegalStateException("KeyProvider must be UserKeystoreKeyProvider to load private keys");
        }
    }
}

