package com.haf.client.crypto;

import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.keystore.KeystoreBootstrap;
import com.haf.shared.dto.KeyMetadata;
import com.haf.shared.exceptions.KeyNotFoundException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * KeyProvider implementation that uses UserKeystore for key management.
 * <p>
 * Sender ID is derived from the oldest CURRENT key directory in the local
 * keystore.
 * Recipient public keys are resolved by first checking the local keystore and
 * then
 * falling back to an optional directory-service fetcher callback.
 */
public class UserKeystoreKeyProvider implements KeyProvider {
    private final UserKeystore keyStore;
    private final String senderId;
    private final char[] passphrase;
    private UnaryOperator<String> directoryServiceFetcher;

    /**
     * Creates a UserKeystoreKeyProvider with the specified keystore root and
     * passphrase.
     *
     * @param keystoreRoot the root directory of the keystore
     * @param passphrase   the passphrase for unlocking private keys
     * @throws Exception if keystore initialization fails
     */
    public UserKeystoreKeyProvider(Path keystoreRoot, String senderId, char[] passphrase) throws Exception {
        this.keyStore = new UserKeystore(keystoreRoot);
        this.senderId = senderId;
        this.passphrase = passphrase != null ? passphrase.clone() : null;
    }

    /**
     * Creates a UserKeystoreKeyProvider using the default keystore location.
     *
     * @param passphrase the passphrase for unlocking private keys
     * @throws Exception if keystore initialization fails
     */
    public UserKeystoreKeyProvider(String senderId, char[] passphrase) throws Exception {
        this(KeystoreBootstrap.run(senderId, passphrase), senderId, passphrase);
    }

    /**
     * Derives the sender ID from the current key's metadata.
     *
     * @return the sender ID (currently the keyId)
     * @throws Exception if keystore is invalid or empty
     */

    /**
     * Sets a callback to fetch public keys from a directory service (e.g., the
     * server).
     * The function should take a recipient ID and return the PEM-encoded public
     * key.
     *
     * @param fetcher the function to fetch the public key
     */
    public void setDirectoryServiceFetcher(UnaryOperator<String> fetcher) {
        this.directoryServiceFetcher = fetcher;
    }

    @Override
    public PublicKey getRecipientPublicKey(String recipientId) throws KeyNotFoundException {
        // Try to load recipient key from local keystore first,
        // then fall back to the directory service if configured.

        try {
            // Check if recipientId matches a keyId in local keystore
            List<KeyMetadata> metadataList = keyStore.listMetadata();

            for (KeyMetadata meta : metadataList) {
                if (recipientId.equals(meta.keyId())) {
                    // Found matching keyId, load public key
                    return loadRecipientKey(recipientId);
                }
            }

            // Key not found in local keystore
            if (directoryServiceFetcher != null) {
                PublicKey key = fetchPublicKeyFromDirectoryService(recipientId);
                if (key != null) {
                    return key;
                }
            }

            throw new KeyNotFoundException(
                    "Recipient key not found for: " + recipientId);
        } catch (KeyNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new KeyNotFoundException("Error looking up recipient key: " + recipientId, e);
        }
    }

    private PublicKey loadRecipientKey(String recipientId) throws KeyNotFoundException {
        try {
            return keyStore.loadPublicKeyByKeyId(recipientId);
        } catch (Exception e) {
            throw new KeyNotFoundException("Failed to load public key for recipient: " + recipientId, e);
        }
    }

    private PublicKey fetchPublicKeyFromDirectoryService(String recipientId) throws KeyNotFoundException {
        try {
            String pem = directoryServiceFetcher.apply(recipientId);
            if (pem != null) {
                return com.haf.shared.utils.EccKeyIO.publicFromPem(pem);
            }
            return null; // Or throw if pem is expected to be non-null when fetcher is present
        } catch (Exception fetchEx) {
            throw new KeyNotFoundException(
                    "Failed to fetch public key from directory service for recipient: " + recipientId, fetchEx);
        }
    }

    @Override
    public String getSenderId() {
        return senderId;
    }

    /**
     * Gets the UserKeystore instance (for loading private keys in MessageReceiver).
     *
     * @return the UserKeystore instance
     */
    public UserKeystore getKeyStore() {
        return keyStore;
    }

    /**
     * Gets the passphrase for unlocking private keys.
     *
     * @return the passphrase (cloned for security)
     */
    public char[] getPassphrase() {
        return passphrase != null ? passphrase.clone() : null;
    }
}
