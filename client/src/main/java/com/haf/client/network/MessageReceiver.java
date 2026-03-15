package com.haf.client.network;

import java.io.IOException;

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
     * Acknowledges all pending envelopes received from a specific sender.
     * This marks the messages as delivered on the server so they are not
     * re-sent on next login.
     *
     * @param senderId the sender whose pending envelopes should be acknowledged
     */
    void acknowledgeEnvelopes(String senderId);

    /**
     * Listener interface for receiving decrypted messages and errors.
     */
    interface MessageListener {
        /**
         * Called when a message is successfully decrypted.
         *
         * @param plaintext the decrypted message bytes
         * @param senderId the sender's identifier
         * @param contentType the MIME content type of the message
         * @param timestampEpochMs the original sent timestamp in milliseconds
         * @param envelopeId the server envelope ID for deferred acknowledgement
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
    }
}
