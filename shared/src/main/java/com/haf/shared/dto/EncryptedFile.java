package com.haf.shared.dto;

import java.io.Serializable;

/**
 * Holds the result of client-side AES-256-GCM file encryption.
 *
 * All byte arrays are encoded as URL-safe Base64 strings for JSON transport.
 * The server stores these fields opaquely in {@code file_uploads} without ever
 * possessing the AES session key (true end-to-end encryption).
 *
 * Field meanings:
 * - {@code ciphertextB64}: AES-GCM ciphertext (Base64)
 * - {@code ivB64}: 12-byte GCM IV (Base64)
 * - {@code tagB64}: 16-byte GCM authentication tag (Base64)
 * - {@code ephemeralPublicB64}: sender's ephemeral X25519 public key
 *   (Base64/DER)
 * - {@code contentType}: MIME type of the original file
 * - {@code originalSize}: size in bytes of the plaintext file
 */
public class EncryptedFileDTO implements Serializable {
    private String ciphertextB64;
    private String ivB64;
    private String tagB64;
    private String ephemeralPublicB64;
    private String contentType;
    private long originalSize;

    /**
     * Creates an empty encrypted file DTO for JSON deserialization.
     */
    public EncryptedFileDTO() {
        // Required for JSON deserialization
    }

    /**
     * Returns base64-encoded ciphertext bytes.
     *
     * @return base64-encoded ciphertext
     */
    public String getCiphertextB64() {
        return ciphertextB64;
    }

    /**
     * Sets base64-encoded ciphertext bytes.
     *
     * @param ciphertextB64 base64-encoded ciphertext
     */
    public void setCiphertextB64(String ciphertextB64) {
        this.ciphertextB64 = ciphertextB64;
    }

    /**
     * Returns base64-encoded AES-GCM IV.
     *
     * @return base64-encoded IV
     */
    public String getIvB64() {
        return ivB64;
    }

    /**
     * Sets base64-encoded AES-GCM IV.
     *
     * @param ivB64 base64-encoded IV
     */
    public void setIvB64(String ivB64) {
        this.ivB64 = ivB64;
    }

    /**
     * Returns base64-encoded AES-GCM tag.
     *
     * @return base64-encoded tag
     */
    public String getTagB64() {
        return tagB64;
    }

    /**
     * Sets base64-encoded AES-GCM tag.
     *
     * @param tagB64 base64-encoded tag
     */
    public void setTagB64(String tagB64) {
        this.tagB64 = tagB64;
    }

    /**
     * Returns base64-encoded ephemeral public key material.
     *
     * @return base64-encoded ephemeral public key
     */
    public String getEphemeralPublicB64() {
        return ephemeralPublicB64;
    }

    /**
     * Sets base64-encoded ephemeral public key material.
     *
     * @param ephemeralPublicB64 base64-encoded ephemeral public key
     */
    public void setEphemeralPublicB64(String ephemeralPublicB64) {
        this.ephemeralPublicB64 = ephemeralPublicB64;
    }

    /**
     * Returns original file MIME/content type.
     *
     * @return original file MIME/content type
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Sets original file MIME/content type.
     *
     * @param contentType original file MIME/content type
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * Returns original plaintext file size in bytes.
     *
     * @return original plaintext file size in bytes
     */
    public long getOriginalSize() {
        return originalSize;
    }

    /**
     * Sets original plaintext file size in bytes.
     *
     * @param originalSize original plaintext file size in bytes
     */
    public void setOriginalSize(long originalSize) {
        this.originalSize = originalSize;
    }
}
