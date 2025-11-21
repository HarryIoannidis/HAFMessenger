package com.haf.client.crypto;

import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.keystore.KeystoreBootstrap;
import com.haf.shared.dto.KeyMetadata;
import com.haf.shared.exceptions.KeyNotFoundException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.List;

/**
 * KeyProvider implementation that uses UserKeystore for key management.
 * 
 * For Phase 4, this is a placeholder implementation:
 * - Sender ID: Derived from current key's keyId in metadata
 * - Recipient public key lookup: Attempts to load from local keystore if keyId matches.
 *   Full directory service integration will be in Phase 5.
 */
public class UserKeystoreKeyProvider implements KeyProvider {
    private final UserKeystore keyStore;
    private final String senderId;
    private final char[] passphrase;

    /**
     * Creates a UserKeystoreKeyProvider with the specified keystore root and passphrase.
     * @param keystoreRoot the root directory of the keystore
     * @param passphrase the passphrase for unlocking private keys
     * @throws Exception if keystore initialization fails
     */
    public UserKeystoreKeyProvider(Path keystoreRoot, char[] passphrase) throws Exception {
        this.keyStore = new UserKeystore(keystoreRoot);
        this.passphrase = passphrase != null ? passphrase.clone() : null;
        
        // Derive sender ID from current key's metadata
        // For Phase 4, we use the keyId as the sender identifier
        // In Phase 5, this might come from a user profile or directory service
        this.senderId = deriveSenderId();
    }

    /**
     * Creates a UserKeystoreKeyProvider using the default keystore location.
     * @param passphrase the passphrase for unlocking private keys
     * @throws Exception if keystore initialization fails
     */
    public UserKeystoreKeyProvider(char[] passphrase) throws Exception {
        this(KeystoreBootstrap.run(), passphrase);
    }

    /**
     * Derives the sender ID from the current key's metadata.
     * @return the sender ID (currently the keyId)
     * @throws Exception if keystore is invalid or empty
     */
    private String deriveSenderId() throws Exception {
        // Prefer the oldest CURRENT key directory as the local identity.
        // This avoids accidentally selecting a newly imported contact key that may also be marked CURRENT.
        try {
            java.nio.file.Path dir = keyStore.selectOldestKeyDirPreferCurrent();
            return dir.getFileName().toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive sender ID from keystore", e);
        }
    }

    @Override
    public PublicKey getRecipientPublicKey(String recipientId) throws KeyNotFoundException {
        // Phase 4 placeholder: Try to load recipient key from local keystore
        // This assumes recipientId is a keyId that exists in the local keystore
        // In Phase 5, this will query a directory service
        
        try {
            // Check if recipientId matches a keyId in local keystore
            List<KeyMetadata> metadataList = keyStore.listMetadata();
            
            for (KeyMetadata meta : metadataList) {
                if (recipientId.equals(meta.keyId())) {
                    // Found matching keyId, load public key
                    try {
                        return keyStore.loadPublicKeyByKeyId(recipientId);
                    } catch (Exception e) {
                        throw new KeyNotFoundException("Failed to load public key for recipient: " + recipientId, e);
                    }
                }
            }
            
            // Key not found in local keystore
            throw new KeyNotFoundException("Recipient key not found in local keystore: " + recipientId + 
                    " (directory service integration will be available in Phase 5)");
        } catch (KeyNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new KeyNotFoundException("Error looking up recipient key: " + recipientId, e);
        }
    }

    @Override
    public String getSenderId() {
        return senderId;
    }

    /**
     * Gets the UserKeystore instance (for loading private keys in MessageReceiver).
     * @return the UserKeystore instance
     */
    public UserKeystore getKeyStore() {
        return keyStore;
    }

    /**
     * Gets the passphrase for unlocking private keys.
     * @return the passphrase (cloned for security)
     */
    public char[] getPassphrase() {
        return passphrase != null ? passphrase.clone() : null;
    }
}

