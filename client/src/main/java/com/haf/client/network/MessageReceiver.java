package com.haf.client.network;

import java.io.IOException;

public interface MessageReceiver {
    /**
     * Sets the message listener that will receive decrypted messages.
     * @param listener the message listener
     */
    void setMessageListener(MessageListener listener);

    /**
     * Starts receiving messages from the network.
     * @throws IOException if the connection cannot be established
     */
    void start() throws IOException;

    /**
     * Stops receiving messages and closes the connection.
     */
    void stop();

    /**
     * Listener interface for receiving decrypted messages and errors.
     */
    interface MessageListener {
        /**
         * Called when a message is successfully decrypted.
         * @param plaintext the decrypted message bytes
         * @param senderId the sender's identifier
         * @param contentType the MIME content type of the message
         */
        void onMessage(byte[] plaintext, String senderId, String contentType);

        /**
         * Called when an error occurs during message processing.
         * @param error the error that occurred
         */
        void onError(Throwable error);
    }
}

