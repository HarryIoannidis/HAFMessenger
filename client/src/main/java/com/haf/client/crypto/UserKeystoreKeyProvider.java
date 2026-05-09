package com.haf.client.crypto;

import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.keystore.KeystoreBootstrap;
import com.haf.shared.dto.KeyMetadata;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.KeystoreOperationException;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.SigningKeyIO;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * KeyProvider implementation that uses UserKeystore for key management.
 *
 * Sender ID is derived from the oldest CURRENT key directory in the local
 * keystore.
 * Recipient public keys are resolved by first checking the local keystore and
 * then falling back to an optional directory-service fetcher callback.
 */
public class UserKeystoreKeyProvider implements KeyProvider {
    private final UserKeystore keyStore;
    private final String senderId;
    private final char[] passphrase;
    private UnaryOperator<String> directoryServiceFetcher;
    private UnaryOperator<String> signingDirectoryServiceFetcher;

    /**
     * Creates a UserKeystoreKeyProvider with the specified keystore root and
     * passphrase.
     *
     * @param keystoreRoot the root directory of the keystore
     * @param senderId     sender identifier associated with the local keystore
     * @param passphrase   the passphrase for unlocking private keys
     * @throws IOException              when keystore I/O fails
     * @throws GeneralSecurityException when cryptographic initialization fails
     */
    public UserKeystoreKeyProvider(Path keystoreRoot, String senderId, char[] passphrase)
            throws IOException, GeneralSecurityException {
        this.keyStore = new UserKeystore(keystoreRoot);
        this.senderId = senderId;
        this.passphrase = passphrase != null ? passphrase.clone() : null;
    }

    /**
     * Creates a UserKeystoreKeyProvider using the default keystore location.
     *
     * @param senderId   sender identifier associated with the local keystore
     * @param passphrase the passphrase for unlocking private keys
     * @throws IOException              when keystore I/O fails
     * @throws GeneralSecurityException when cryptographic initialization fails
     */
    public UserKeystoreKeyProvider(String senderId, char[] passphrase) throws IOException, GeneralSecurityException {
        this(bootstrapKeystore(senderId, passphrase), senderId, passphrase);
    }

    /**
     * Runs keystore bootstrap, wrapping any checked exception in
     * {@link KeystoreOperationException}.
     */
    private static Path bootstrapKeystore(String senderId, char[] passphrase) {
        try {
            requireBootstrapPassphrase(senderId, passphrase);
            return KeystoreBootstrap.run(senderId, passphrase);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new KeystoreOperationException("Keystore bootstrap failed for user: " + senderId, e);
        }
    }

    /**
     * Ensures bootstrap is always executed with an explicit non-empty passphrase.
     *
     * @param senderId   sender id bound to the keystore root
     * @param passphrase passphrase provided by login/registration flow
     */
    private static void requireBootstrapPassphrase(String senderId, char[] passphrase) {
        if (passphrase == null || passphrase.length == 0) {
            throw new KeystoreOperationException(
                    "Keystore bootstrap requires non-empty passphrase for user: " + senderId);
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
    public void setDirectoryServiceFetcher(UnaryOperator<String> fetcher) {
        this.directoryServiceFetcher = fetcher;
    }

    /**
     * Sets callback that resolves Ed25519 signing public keys from directory
     * service.
     *
     * @param fetcher function mapping user id to signing public key PEM
     */
    public void setSigningDirectoryServiceFetcher(UnaryOperator<String> fetcher) {
        this.signingDirectoryServiceFetcher = fetcher;
    }

    /**
     * Resolves a recipient public key from local keystore metadata, with optional
     * directory-service fallback.
     *
     * @param recipientId recipient identifier whose public key is required
     * @return recipient public key used for message encryption
     * @throws KeyNotFoundException when local lookup and optional directory
     *                              fallback both fail
     */
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

    /**
     * Resolves recipient signing key from local keystore or directory service.
     *
     * @param recipientId recipient identifier
     * @return recipient Ed25519 signing public key
     * @throws KeyNotFoundException when key cannot be resolved
     */
    @Override
    public PublicKey getRecipientSigningPublicKey(String recipientId) throws KeyNotFoundException {
        try {
            List<KeyMetadata> metadataList = keyStore.listMetadata();
            for (KeyMetadata meta : metadataList) {
                if (recipientId.equals(meta.keyId())) {
                    return loadRecipientSigningKey(recipientId);
                }
            }

            if (signingDirectoryServiceFetcher != null) {
                PublicKey key = fetchSigningPublicKeyFromDirectoryService(recipientId);
                if (key != null) {
                    return key;
                }
            }
            throw new KeyNotFoundException("Recipient signing key not found for: " + recipientId);
        } catch (KeyNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new KeyNotFoundException("Error looking up recipient signing key: " + recipientId, e);
        }
    }

    /**
     * Loads a recipient public key by key id from the local keystore.
     *
     * @param recipientId key id to load
     * @return loaded public key
     * @throws KeyNotFoundException when key loading fails
     */
    private PublicKey loadRecipientKey(String recipientId) throws KeyNotFoundException {
        try {
            return keyStore.loadPublicKeyByKeyId(recipientId);
        } catch (Exception e) {
            throw new KeyNotFoundException("Failed to load public key for recipient: " + recipientId, e);
        }
    }

    private PublicKey loadRecipientSigningKey(String recipientId) throws KeyNotFoundException {
        try {
            return keyStore.loadSigningPublicKeyByKeyId(recipientId);
        } catch (Exception e) {
            throw new KeyNotFoundException("Failed to load signing public key for recipient: " + recipientId, e);
        }
    }

    /**
     * Retrieves a PEM public key via external directory service and converts it to
     * {@link PublicKey}.
     *
     * @param recipientId recipient identifier to resolve remotely
     * @return resolved public key, or {@code null} when directory lookup returns no
     *         key
     * @throws KeyNotFoundException when remote fetch or PEM parsing fails
     */
    private PublicKey fetchPublicKeyFromDirectoryService(String recipientId) throws KeyNotFoundException {
        try {
            String pem = directoryServiceFetcher.apply(recipientId);
            if (pem != null) {
                return EccKeyIO.publicFromPem(pem);
            }
            return null; // Or throw if pem is expected to be non-null when fetcher is present
        } catch (Exception fetchEx) {
            throw new KeyNotFoundException(
                    "Failed to fetch public key from directory service for recipient: " + recipientId, fetchEx);
        }
    }

    private PublicKey fetchSigningPublicKeyFromDirectoryService(String recipientId) throws KeyNotFoundException {
        try {
            String pem = signingDirectoryServiceFetcher.apply(recipientId);
            if (pem != null) {
                return SigningKeyIO.publicFromPem(pem);
            }
            return null;
        } catch (Exception fetchEx) {
            throw new KeyNotFoundException(
                    "Failed to fetch signing public key from directory service for recipient: " + recipientId,
                    fetchEx);
        }
    }

    /**
     * Returns the sender id associated with the currently loaded keystore.
     *
     * @return sender identifier used in message envelopes
     */
    @Override
    public String getSenderId() {
        return senderId;
    }

    /**
     * Returns sender Ed25519 signing private key from current local key material.
     *
     * @return sender signing private key
     * @throws KeyNotFoundException when key cannot be loaded
     */
    @Override
    public PrivateKey getSenderSigningPrivateKey() throws KeyNotFoundException {
        try {
            return keyStore.loadCurrentSigningPrivate(passphrase);
        } catch (Exception e) {
            throw new KeyNotFoundException("Failed to load sender signing private key for user: " + senderId, e);
        }
    }

    /**
     * Returns sender signing key fingerprint derived from current signing public key.
     *
     * @return sender signing key fingerprint
     * @throws KeyNotFoundException when key cannot be loaded
     */
    @Override
    public String getSenderSigningKeyFingerprint() throws KeyNotFoundException {
        try {
            PublicKey signingPublic = keyStore.loadCurrentSigningPublic();
            return FingerprintUtil.sha256Hex(SigningKeyIO.publicDer(signingPublic));
        } catch (Exception e) {
            throw new KeyNotFoundException("Failed to resolve sender signing key fingerprint for user: " + senderId, e);
        }
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
