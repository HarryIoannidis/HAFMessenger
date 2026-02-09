package com.haf.client.network;

import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.MessageValidationException;
import java.io.IOException;


public interface MessageSender {

    /**
     * Sends an encrypted message to a recipient.
     *
     * @param payload the plaintext bytes to send
     * @param recipientId the recipient's identifier
     * @param contentType the MIME content type of the payload
     * @param ttlSeconds the time-to-live in seconds
     * @throws MessageValidationException if message validation fails
     * @throws KeyNotFoundException if the recipient's public key cannot be found
     * @throws IOException if network communication fails
     */
    void sendMessage(byte[] payload, String recipientId, String contentType, long ttlSeconds) 
            throws MessageValidationException, KeyNotFoundException, IOException;
}

