package com.haf.shared.dto;

import java.io.Serializable;

/**
 * Holds the result of client-side AES-256-GCM file encryption.
 *
 * <p>
 * All byte arrays are encoded as URL-safe Base64 strings for JSON transport.
 * The server stores these fields opaquely in {@code file_uploads} without ever
 * possessing the AES session key (true end-to-end encryption).
 * </p>
 *
 * <ul>
 * <li>{@code ciphertextB64} – AES-GCM ciphertext (Base64)</li>
 * <li>{@code ivB64} – 12-byte GCM IV (Base64)</li>
 * <li>{@code tagB64} – 16-byte GCM authentication tag (Base64)</li>
 * <li>{@code ephemeralPublicB64}– sender's ephemeral X25519 public key
 * (Base64/DER)</li>
 * <li>{@code contentType} – MIME type of the original file</li>
 * <li>{@code originalSize} – size in bytes of the plaintext file</li>
 * </ul>
 */
public class EncryptedFileDTO implements Serializable {
    private String ciphertextB64;
    private String ivB64;
    private String tagB64;
    private String ephemeralPublicB64;
    private String contentType;
    private long originalSize;

    public EncryptedFileDTO() {
        // Required for JSON deserialization
    }

    public String getCiphertextB64() {
        return ciphertextB64;
    }

    public void setCiphertextB64(String ciphertextB64) {
        this.ciphertextB64 = ciphertextB64;
    }

    public String getIvB64() {
        return ivB64;
    }

    public void setIvB64(String ivB64) {
        this.ivB64 = ivB64;
    }

    public String getTagB64() {
        return tagB64;
    }

    public void setTagB64(String tagB64) {
        this.tagB64 = tagB64;
    }

    public String getEphemeralPublicB64() {
        return ephemeralPublicB64;
    }

    public void setEphemeralPublicB64(String ephemeralPublicB64) {
        this.ephemeralPublicB64 = ephemeralPublicB64;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getOriginalSize() {
        return originalSize;
    }

    public void setOriginalSize(long originalSize) {
        this.originalSize = originalSize;
    }
}
