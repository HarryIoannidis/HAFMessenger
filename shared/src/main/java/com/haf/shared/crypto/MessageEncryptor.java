package com.haf.shared.crypto;

import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.utils.ClockProvider;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.SecretKey;

/**
 * Encrypts messages for transmission using AES-256-GCM and RSA-OAEP key wrapping.
 */
public class MessageEncryptor {
    private final PublicKey recipientPublicKey;
    private final String senderId;
    private final String recipientId;
    private final ClockProvider clockProvider;

    /**
     * Creates a MessageEncryptor with RSA public key of recipient and metadata.
     *
     * @param recipientPublicKey the recipient's public RSA key
     * @param senderId the sender's ID
     * @param recipientId the recipient's ID
     * @param clockProvider the clock provider for deterministic timestamps
     */
    public MessageEncryptor(PublicKey recipientPublicKey, String senderId, String recipientId, ClockProvider clockProvider) {
        this.recipientPublicKey = recipientPublicKey;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.clockProvider = clockProvider;
    }

    /**
     * Encrypts a payload and creates an EncryptedMessage DTO.
     *
     * @param payload the plaintext bytes to encrypt
     * @param contentType the content type (MIME type) of the payload
     * @param ttlSeconds the time-to-live in seconds (must be between MIN_TTL_SECONDS and MAX_TTL_SECONDS)
     * @return the complete EncryptedMessage DTO
     * @throws IllegalArgumentException if parameters are invalid
     * @throws Exception if encryption fails
     */
    public EncryptedMessage encrypt(byte[] payload, String contentType, long ttlSeconds) throws Exception {
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("ContentType cannot be null or empty");
        }
        if (ttlSeconds < MessageHeader.MIN_TTL_SECONDS || ttlSeconds > MessageHeader.MAX_TTL_SECONDS) {
            throw new IllegalArgumentException("TTL must be between " + MessageHeader.MIN_TTL_SECONDS + 
                    " and " + MessageHeader.MAX_TTL_SECONDS + " seconds");
        }

        SecretKey aesKey = CryptoService.generateAesKey();
        byte[] iv = CryptoService.generateIv();

        if (iv == null || iv.length != MessageHeader.IV_BYTES) {
            throw new IllegalStateException("IV is null or invalid length");
        }

        byte[] wrappedKey = CryptoRSA.wrapKey(aesKey, recipientPublicKey);
        if (wrappedKey == null || wrappedKey.length == 0) {
            throw new IllegalStateException("Wrapped AES key is null or empty");
        }

        EncryptedMessage m = new EncryptedMessage();
        m.version = MessageHeader.VERSION;
        m.algorithm = MessageHeader.ALGO_AEAD;
        m.senderId = senderId;
        m.recipientId = recipientId;
        m.timestampEpochMs = clockProvider.currentTimeMillis();
        m.ttlSeconds = ttlSeconds;
        m.contentType = contentType;
        m.contentLength = payload.length;

        m.ivB64 = Base64.getEncoder().encodeToString(iv);
        m.wrappedKeyB64 = Base64.getEncoder().encodeToString(wrappedKey);

        // Build AAD before encryption (AAD is built from DTO fields)
        byte[] aad = AadCodec.buildAAD(m);

        byte[] combined = CryptoService.encryptAesGcm(payload, aesKey, iv, aad);
        if (combined == null || combined.length == 0) {
            throw new IllegalStateException("Ciphertext is null or empty");
        }

        final int TAG_LEN = MessageHeader.GCM_TAG_BYTES;
        if (combined.length <= TAG_LEN) {
            throw new IllegalStateException("Combined output too short");
        }

        int ctLen = combined.length - TAG_LEN;
        byte[] ct = java.util.Arrays.copyOfRange(combined, 0, ctLen);
        byte[] tag = java.util.Arrays.copyOfRange(combined, ctLen, combined.length);

        m.ciphertextB64 = Base64.getEncoder().encodeToString(ct);
        m.tagB64 = Base64.getEncoder().encodeToString(tag);
        m.e2e = true;

        return m;
    }
}
