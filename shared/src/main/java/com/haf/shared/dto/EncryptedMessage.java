package com.haf.shared.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;

/**
 * Store an encrypted message.
 *
 * @version the version of the protocol.
 * @senderId the ID of the sender.
 * @recipientId the ID of the recipient.
 * @timestampEpochMs the timestamp of the message in epoch milliseconds.
 * @ttlSeconds the time to live of the message in seconds.
 * @algorithm the algorithm used to encrypt the message.
 * @ivB64 the initialization vector of the message in base64.
 * @wrappedKeyB64 the wrapped key of the message in base64.
 * @ciphertextB64 the ciphertext of the message in base64.
 * @tagB64 the authentication tag of the message in base64.
 * @contentType the content type of the message.
 * @contentLength the length of the message in bytes.
 * @e2e whether the message is end-to-end encrypted.
 * @aadB64 the authenticated additional data of the message in base64.
 */
public class EncryptedMessage implements Serializable {
    public String version;
    public String senderId;
    public String recipientId;
    public long timestampEpochMs;
    public long ttlSeconds;
    public String algorithm;
    public String ivB64;
    public String wrappedKeyB64;
    public String ciphertextB64;
    public String tagB64;
    public String contentType;
    public long contentLength;
    public boolean e2e = true;
    @JsonIgnore public String aadB64;
}

