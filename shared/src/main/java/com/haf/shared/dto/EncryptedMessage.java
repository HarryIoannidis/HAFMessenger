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
 * @ephemeralPublicB64 the wrapped key of the message in base64.
 * @ciphertextB64 the ciphertext of the message in base64.
 * @tagB64 the authentication tag of the message in base64.
 * @contentType the content type of the message.
 * @contentLength the length of the message in bytes.
 * @e2e whether the message is end-to-end encrypted.
 * @aadB64 the authenticated additional data of the message in base64.
 */
public class EncryptedMessage implements Serializable {
    private String version;
    private String senderId;
    private String recipientId;
    private long timestampEpochMs;
    private long ttlSeconds;
    private String algorithm;
    private String ivB64;
    private String ephemeralPublicB64;
    private String ciphertextB64;
    private String tagB64;
    private String contentType;
    private long contentLength;
    private boolean e2e = true;

    @JsonIgnore
    private String aadB64;

    public EncryptedMessage() {
        // Required for JSON deserialization
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public long getTimestampEpochMs() {
        return timestampEpochMs;
    }

    public void setTimestampEpochMs(long timestampEpochMs) {
        this.timestampEpochMs = timestampEpochMs;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getIvB64() {
        return ivB64;
    }

    public void setIvB64(String ivB64) {
        this.ivB64 = ivB64;
    }

    public String getEphemeralPublicB64() {
        return ephemeralPublicB64;
    }

    public void setEphemeralPublicB64(String ephemeralPublicB64) {
        this.ephemeralPublicB64 = ephemeralPublicB64;
    }

    public String getCiphertextB64() {
        return ciphertextB64;
    }

    public void setCiphertextB64(String ciphertextB64) {
        this.ciphertextB64 = ciphertextB64;
    }

    public String getTagB64() {
        return tagB64;
    }

    public void setTagB64(String tagB64) {
        this.tagB64 = tagB64;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public boolean isE2e() {
        return e2e;
    }

    public void setE2e(boolean e2e) {
        this.e2e = e2e;
    }

    public String getAadB64() {
        return aadB64;
    }

    public void setAadB64(String aadB64) {
        this.aadB64 = aadB64;
    }
}
