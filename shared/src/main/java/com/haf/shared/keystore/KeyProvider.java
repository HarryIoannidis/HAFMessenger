package com.haf.shared.keystore;

import com.haf.shared.exceptions.KeyNotFoundException;
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
     * Returns the sender's own identifier.
     *
     * @return the sender ID
     */
    String getSenderId();
}
