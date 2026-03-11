package com.haf.shared.crypto;

import com.haf.shared.exceptions.CryptoOperationException;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.utils.ClockProvider;
import java.security.PublicKey;
import java.util.Base64;
import javax.crypto.SecretKey;

/**
 * Encrypts messages for transmission using AES-256-GCM and X25519 (ECDH) key
 * wrapping.
 */
public class MessageEncryptor {
    private final PublicKey recipientPublicKey;
    private final String senderId;
    private final String recipientId;
    private final ClockProvider clockProvider;

    /**
     * Creates a MessageEncryptor with ECC public key of recipient and metadata.
     *
     * @param recipientPublicKey the recipient's public X25519 key
     * @param senderId           the sender's ID
     * @param recipientId        the recipient's ID
     * @param clockProvider      the clock provider for deterministic timestamps
     */
    public MessageEncryptor(PublicKey recipientPublicKey, String senderId, String recipientId,
            ClockProvider clockProvider) {
        this.recipientPublicKey = recipientPublicKey;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.clockProvider = clockProvider;
    }

    /**
     * Encrypts a payload and creates an EncryptedMessage DTO.
     *
     * @param payload     the plaintext bytes to encrypt
     * @param contentType the content type (MIME type) of the payload
     * @param ttlSeconds  the time-to-live in seconds (must be between
     *                    MIN_TTL_SECONDS and MAX_TTL_SECONDS)
     * @return the complete EncryptedMessage DTO
     * @throws IllegalArgumentException if parameters are invalid
     * @throws Exception                if encryption fails
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

        // 1. Generate an ephemeral X25519 keypair for this specific message
        java.security.KeyPair ephemeralPair = com.haf.shared.utils.EccKeyIO.generate();

        // 2. Derive the 256-bit AES session key using ECDH + SHA-256
        SecretKey aesKey = CryptoECC.generateAndDeriveAesKey(ephemeralPair.getPrivate(), recipientPublicKey);

        // 3. Generate AES-GCM IV
        byte[] iv = CryptoService.generateIv();
        if (iv == null || iv.length != MessageHeader.IV_BYTES) {
            throw new CryptoOperationException("IV is null or invalid length");
        }

        // 4. Capture the ephemeral public key to send to the recipient
        byte[] ephemeralPublicDer = com.haf.shared.utils.EccKeyIO.publicDer(ephemeralPair.getPublic());

        EncryptedMessage m = new EncryptedMessage();
        m.setVersion(MessageHeader.VERSION);
        m.setAlgorithm(MessageHeader.ALGO_AEAD);
        m.setSenderId(senderId);
        m.setRecipientId(recipientId);
        m.setTimestampEpochMs(clockProvider.currentTimeMillis());
        m.setTtlSeconds(ttlSeconds);
        m.setContentType(contentType);
        m.setContentLength(payload.length);

        m.setIvB64(Base64.getEncoder().encodeToString(iv));
        m.setEphemeralPublicB64(Base64.getEncoder().encodeToString(ephemeralPublicDer));

        // Build AAD before encryption (AAD is built from DTO fields)
        byte[] aad = AadCodec.buildAAD(m);

        byte[] combined = CryptoService.encryptAesGcm(payload, aesKey, iv, aad);
        if (combined == null || combined.length == 0) {
            throw new CryptoOperationException("Ciphertext is null or empty");
        }

        final int TAG_LEN = MessageHeader.GCM_TAG_BYTES;
        if (combined.length <= TAG_LEN) {
            throw new CryptoOperationException("Combined output too short");
        }

        int ctLen = combined.length - TAG_LEN;
        byte[] ct = java.util.Arrays.copyOfRange(combined, 0, ctLen);
        byte[] tag = java.util.Arrays.copyOfRange(combined, ctLen, combined.length);

        m.setCiphertextB64(Base64.getEncoder().encodeToString(ct));
        m.setTagB64(Base64.getEncoder().encodeToString(tag));
        m.setE2e(true);

        return m;
    }
}
