package com.haf.shared.keystore;

import com.haf.shared.exceptions.KeyNotFoundException;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Defines the contract for resolving recipient keys and sender identity.
 */
public interface KeyProvider {
    /**
     * Retrieves the public key for a given recipient ID.
     *
     * @param recipientId the recipient's identifier
     * @return the recipient's public key
     * @throws KeyNotFoundException if the key cannot be found
     */
    PublicKey getRecipientPublicKey(String recipientId) throws KeyNotFoundException;

    /**
     * Retrieves the Ed25519 signing public key for a given user ID.
     *
     * @param recipientId the user identifier
     * @return the recipient's signing public key
     * @throws KeyNotFoundException if the key cannot be found
     */
    default PublicKey getRecipientSigningPublicKey(String recipientId) throws KeyNotFoundException {
        return getRecipientPublicKey(recipientId);
    }

    /**
     * Returns the sender's own identifier.
     *
     * @return the sender ID
     */
    String getSenderId();

    /**
     * Returns sender signing private key used for Ed25519 signatures.
     *
     * @return sender signing private key
     * @throws KeyNotFoundException when signing key cannot be resolved
     */
    default PrivateKey getSenderSigningPrivateKey() throws KeyNotFoundException {
        throw new KeyNotFoundException("Sender signing private key is not available");
    }

    /**
     * Returns sender signing key fingerprint.
     *
     * @return sender signing key fingerprint
     * @throws KeyNotFoundException when signing key fingerprint cannot be resolved
     */
    default String getSenderSigningKeyFingerprint() throws KeyNotFoundException {
        throw new KeyNotFoundException("Sender signing key fingerprint is not available");
    }
}
