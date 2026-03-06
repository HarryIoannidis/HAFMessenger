package com.haf.client.crypto;

import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.keystore.KeystoreBootstrap;
import com.haf.shared.dto.KeyMetadata;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.KeystoreOperationException;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.List;

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
    private java.util.function.Function<String, String> directoryServiceFetcher;

    /**
     * Creates a UserKeystoreKeyProvider with the specified keystore root and
     * passphrase.
     *
     * @param keystoreRoot the root directory of the keystore
     * @param passphrase   the passphrase for unlocking private keys
     * @throws Exception if keystore initialization fails
     */
    public UserKeystoreKeyProvider(Path keystoreRoot, char[] passphrase) throws Exception {
        this.keyStore = new UserKeystore(keystoreRoot);
        this.passphrase = passphrase != null ? passphrase.clone() : null;

        // Derive sender ID from the oldest CURRENT key directory name
        this.senderId = deriveSenderId();
    }

    /**
     * Creates a UserKeystoreKeyProvider using the default keystore location.
     *
     * @param passphrase the passphrase for unlocking private keys
     * @throws Exception if keystore initialization fails
     */
    public UserKeystoreKeyProvider(char[] passphrase) throws Exception {
        this(KeystoreBootstrap.run(), passphrase);
    }

    /**
     * Derives the sender ID from the current key's metadata.
     *
     * @return the sender ID (currently the keyId)
     * @throws Exception if keystore is invalid or empty
     */
    private String deriveSenderId() throws Exception {
        // Prefer the oldest CURRENT key directory as the local identity.
        // This avoids accidentally selecting a newly imported contact key that may also
        // be marked CURRENT.
        try {
            java.nio.file.Path dir = keyStore.selectOldestKeyDirPreferCurrent();
            return dir.getFileName().toString();
        } catch (Exception e) {
            throw new KeystoreOperationException("Failed to derive sender ID from keystore", e);
        }
    }

    /**
     * Sets a callback to fetch public keys from a directory service (e.g., the
     * server).
     * The function should take a recipient ID and return the PEM-encoded public
     * key.
     *
     * @param fetcher the function to fetch the public key
     */
    public void setDirectoryServiceFetcher(java.util.function.Function<String, String> fetcher) {
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
                try {
                    String pem = directoryServiceFetcher.apply(recipientId);
                    if (pem != null) {
                        return com.haf.shared.utils.EccKeyIO.publicFromPem(pem);
                    }
                } catch (Exception fetchEx) {
                    throw new KeyNotFoundException(
                            "Failed to fetch public key from directory service for recipient: " + recipientId, fetchEx);
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
