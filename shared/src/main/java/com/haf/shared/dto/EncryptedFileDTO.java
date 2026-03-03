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
    public String ciphertextB64;
    public String ivB64;
    public String tagB64;
    public String ephemeralPublicB64;
    public String contentType;
    public long originalSize;
}
