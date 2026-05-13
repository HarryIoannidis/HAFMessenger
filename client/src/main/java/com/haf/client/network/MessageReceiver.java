package com.haf.client.network;

import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.exceptions.MessageDecryptionException;
import java.io.IOException;

/**
 * Defines the contract for receiving, decrypting, and acknowledging incoming
 * messages.
 */
public interface MessageReceiver {
    /**
     * Sets the message listener that will receive decrypted messages.
     *
     * @param listener the message listener
     */
    void setMessageListener(MessageListener listener);

    /**
     * Starts receiving messages from the network.
     *
     * @throws IOException if the connection cannot be established
     */
    void start() throws IOException;

    /**
     * Stops receiving messages and closes the connection.
     */
    void stop();

    /**
     * Reconnects the active transport after network loss or token refresh.
     *
     * @throws IOException when reconnection fails
     */
    default void reconnect() throws IOException {
        stop();
        start();
    }

    /**
     * Returns whether the underlying receiver transport is currently connected.
     *
     * @return {@code true} when the receiver has an active connection
     */
    default boolean isConnected() {
        return false;
    }

    /**
     * Acknowledges all pending envelopes received from a specific sender.
     * This marks the messages as delivered on the server so they are not
     * re-sent on next login.
     *
     * @param senderId the sender whose pending envelopes should be acknowledged
     */
    void acknowledgeEnvelopes(String senderId);

    /**
     * Emits a typing-start realtime event for a recipient.
     *
     * @param recipientId recipient/contact id
     */
    default void sendTypingStart(String recipientId) {
        // Optional for transports without realtime typing support.
    }

    /**
     * Emits a typing-stop realtime event for a recipient.
     *
     * @param recipientId recipient/contact id
     */
    default void sendTypingStop(String recipientId) {
        // Optional for transports without realtime typing support.
    }

    /**
     * Decrypts a detached encrypted message payload (not sourced from WS mailbox).
     * Used for attachment reference downloads.
     * 
     * @param encryptedMessage the encrypted message to decrypt
     * @return the decrypted message bytes
     * @throws MessageDecryptionException if the message cannot be decrypted
     */
    default byte[] decryptDetachedMessage(EncryptedMessage encryptedMessage) throws MessageDecryptionException {
        throw new UnsupportedOperationException("decryptDetachedMessage is not implemented");
    }

    /**
     * Listener interface for receiving decrypted messages and errors.
     */
    interface MessageListener {
        /**
         * Called when a message is successfully decrypted.
         *
         * @param plaintext        the decrypted message bytes
         * @param senderId         the sender's identifier
         * @param contentType      the MIME content type of the message
         * @param timestampEpochMs the original sent timestamp in milliseconds
         * @param envelopeId       the server envelope ID for deferred acknowledgement
         */
        void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs, String envelopeId);

        /**
         * Called when an error occurs during message processing.
         *
         * @param error the error that occurred
         */
        void onError(Throwable error);

        /**
         * Called when a user presence update is received.
         *
         * @param userId the user whose presence changed
         * @param active true when active, false when inactive
         */
        default void onPresenceUpdate(String userId, boolean active) {
            // Optional callback.
        }

        /**
         * Called when another user starts or stops typing.
         *
         * @param userId sender/contact id
         * @param typing true for typing start, false for typing stop
         */
        default void onTyping(String userId, boolean typing) {
            // Optional callback.
        }

        /**
         * Called when delivery receipts are received for outgoing envelope IDs.
         *
         * @param userId      sender of the receipt
         * @param envelopeIds affected outgoing envelope IDs
         */
        default void onMessageDelivered(String userId, java.util.List<String> envelopeIds) {
            // Optional callback.
        }

        /**
         * Called when read receipts are received for outgoing envelope IDs.
         *
         * @param userId      sender of the receipt
         * @param envelopeIds affected outgoing envelope IDs
         */
        default void onMessageRead(String userId, java.util.List<String> envelopeIds) {
            // Optional callback.
        }
    }
}
