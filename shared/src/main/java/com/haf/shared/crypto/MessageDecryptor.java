package com.haf.shared.crypto;

import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.MessageValidator;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.exceptions.MessageExpiredException;
import com.haf.shared.exceptions.MessageTamperedException;
import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.util.Base64;
import java.security.PrivateKey;

public class MessageDecryptor {
    private final PrivateKey recipientPrivateKey;
    private final ClockProvider clockProvider;

    /**
     * Creates a MessageDecryptor with RSA private key of recipient.
     * @param recipientPrivateKey the recipient's private RSA key
     * @param clockProvider the clock provider for deterministic expiry checks
     */
    public MessageDecryptor(PrivateKey recipientPrivateKey, ClockProvider clockProvider) {
        this.recipientPrivateKey = recipientPrivateKey;
        this.clockProvider = clockProvider;
    }

    /**
     * Decrypts an EncryptedMessage to raw plaintext byte array.
     * Validation policies are applied by MessageValidator.
     *
     * @param m the EncryptedMessage DTO
     * @return the plaintext bytes of the message
     * @throws MessageExpiredException if the message has expired (TTL exceeded)
     * @throws MessageTamperedException if message tampering is detected (AEAD tag verification failed)
     * @throws IllegalArgumentException if validation fails
     * @throws Exception if decryption fails for other reasons
     */
    public byte[] decryptMessage(EncryptedMessage m) throws MessageExpiredException, MessageTamperedException, Exception {
        // Validate message structure and policy
        MessageValidator.validate(m);

        // Check expiry using ClockProvider
        long now = clockProvider.currentTimeMillis();
        if (now > m.timestampEpochMs + m.ttlSeconds * 1000L) {
            throw new MessageExpiredException("Message expired at " + now + 
                    ", message timestamp: " + m.timestampEpochMs + ", TTL: " + m.ttlSeconds + " seconds");
        }

        byte[] wrappedKey = Base64.getDecoder().decode(m.wrappedKeyB64);
        byte[] iv = Base64.getDecoder().decode(m.ivB64);
        byte[] ct = Base64.getDecoder().decode(m.ciphertextB64);
        byte[] tag = Base64.getDecoder().decode(m.tagB64);

        byte[] combined = new byte[ct.length + tag.length];
        System.arraycopy(ct, 0, combined, 0, ct.length);
        System.arraycopy(tag, 0, combined, ct.length, tag.length);

        SecretKey aesKey = CryptoRSA.unwrapKey(wrappedKey, recipientPrivateKey);

        byte[] aad = AadCodec.buildAAD(m);

        try {
            return CryptoService.decryptAesGcm(combined, aesKey, iv, aad);
        } catch (AEADBadTagException e) {
            // Wrap AEAD tag failure in MessageTamperedException
            throw new MessageTamperedException(e);
        }
    }
}
