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
 * @signatureAlgorithm the signature algorithm identifier.
 * @senderSigningKeyFingerprint the sender signing key fingerprint used for the signature.
 * @signatureB64 the signature bytes in base64.
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
    private String signatureAlgorithm;
    private String senderSigningKeyFingerprint;
    private String signatureB64;

    @JsonIgnore
    private String aadB64;

    /**
     * Creates an empty encrypted message DTO for JSON deserialization.
     */
    public EncryptedMessage() {
        // Required for JSON deserialization
    }

    /**
     * Returns the protocol version used for this message envelope.
     *
     * @return the protocol version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the protocol version used for this message envelope.
     *
     * @param version the protocol version
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Returns the sender user id.
     *
     * @return the sender user id
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * Sets the sender user id.
     *
     * @param senderId the sender user id
     */
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    /**
     * Returns the recipient user id.
     *
     * @return the recipient user id
     */
    public String getRecipientId() {
        return recipientId;
    }

    /**
     * Sets the recipient user id.
     *
     * @param recipientId the recipient user id
     */
    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    /**
     * Returns message creation timestamp in epoch milliseconds.
     *
     * @return the message timestamp in epoch milliseconds
     */
    public long getTimestampEpochMs() {
        return timestampEpochMs;
    }

    /**
     * Sets message creation timestamp in epoch milliseconds.
     *
     * @param timestampEpochMs the message timestamp in epoch milliseconds
     */
    public void setTimestampEpochMs(long timestampEpochMs) {
        this.timestampEpochMs = timestampEpochMs;
    }

    /**
     * Returns the message time-to-live in seconds.
     *
     * @return the message TTL in seconds
     */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * Sets the message time-to-live in seconds.
     *
     * @param ttlSeconds the message TTL in seconds
     */
    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Returns the declared encryption algorithm identifier.
     *
     * @return the encryption algorithm identifier
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Sets the declared encryption algorithm identifier.
     *
     * @param algorithm the encryption algorithm identifier
     */
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Returns the base64-encoded AES-GCM IV.
     *
     * @return the base64-encoded IV
     */
    public String getIvB64() {
        return ivB64;
    }

    /**
     * Sets the base64-encoded AES-GCM IV.
     *
     * @param ivB64 the base64-encoded IV
     */
    public void setIvB64(String ivB64) {
        this.ivB64 = ivB64;
    }

    /**
     * Returns the base64-encoded wrapped key / ephemeral public key value.
     *
     * @return the base64-encoded wrapped key value
     */
    public String getEphemeralPublicB64() {
        return ephemeralPublicB64;
    }

    /**
     * Sets the base64-encoded wrapped key / ephemeral public key value.
     *
     * @param ephemeralPublicB64 the base64-encoded wrapped key value
     */
    public void setEphemeralPublicB64(String ephemeralPublicB64) {
        this.ephemeralPublicB64 = ephemeralPublicB64;
    }

    /**
     * Returns the base64-encoded ciphertext.
     *
     * @return the base64-encoded ciphertext
     */
    public String getCiphertextB64() {
        return ciphertextB64;
    }

    /**
     * Sets the base64-encoded ciphertext.
     *
     * @param ciphertextB64 the base64-encoded ciphertext
     */
    public void setCiphertextB64(String ciphertextB64) {
        this.ciphertextB64 = ciphertextB64;
    }

    /**
     * Returns the base64-encoded authentication tag.
     *
     * @return the base64-encoded authentication tag
     */
    public String getTagB64() {
        return tagB64;
    }

    /**
     * Sets the base64-encoded authentication tag.
     *
     * @param tagB64 the base64-encoded authentication tag
     */
    public void setTagB64(String tagB64) {
        this.tagB64 = tagB64;
    }

    /**
     * Returns the payload MIME/content type.
     *
     * @return the payload MIME/content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Sets the payload MIME/content type.
     *
     * @param contentType the payload MIME/content type
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Returns plaintext content length in bytes.
     *
     * @return plaintext content length in bytes
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * Sets plaintext content length in bytes.
     *
     * @param contentLength plaintext content length in bytes
     */
    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    /**
     * Returns whether this message is flagged as end-to-end encrypted.
     *
     * @return {@code true} when message is end-to-end encrypted
     */
    public boolean isE2e() {
        return e2e;
    }

    /**
     * Sets whether this message is flagged as end-to-end encrypted.
     *
     * @param e2e end-to-end encryption flag
     */
    public void setE2e(boolean e2e) {
        this.e2e = e2e;
    }

    /**
     * Returns message signature algorithm identifier.
     *
     * @return signature algorithm identifier
     */
    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * Sets message signature algorithm identifier.
     *
     * @param signatureAlgorithm signature algorithm identifier
     */
    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    /**
     * Returns sender signing key fingerprint bound to this signature.
     *
     * @return signing key fingerprint
     */
    public String getSenderSigningKeyFingerprint() {
        return senderSigningKeyFingerprint;
    }

    /**
     * Sets sender signing key fingerprint bound to this signature.
     *
     * @param senderSigningKeyFingerprint signing key fingerprint
     */
    public void setSenderSigningKeyFingerprint(String senderSigningKeyFingerprint) {
        this.senderSigningKeyFingerprint = senderSigningKeyFingerprint;
    }

    /**
     * Returns base64-encoded signature bytes.
     *
     * @return signature base64
     */
    public String getSignatureB64() {
        return signatureB64;
    }

    /**
     * Sets base64-encoded signature bytes.
     *
     * @param signatureB64 signature base64
     */
    public void setSignatureB64(String signatureB64) {
        this.signatureB64 = signatureB64;
    }

    /**
     * Returns base64-encoded additional authenticated data.
     *
     * @return base64-encoded AAD value
     */
    public String getAadB64() {
        return aadB64;
    }

    /**
     * Sets base64-encoded additional authenticated data.
     *
     * @param aadB64 base64-encoded AAD value
     */
    public void setAadB64(String aadB64) {
        this.aadB64 = aadB64;
    }
}
