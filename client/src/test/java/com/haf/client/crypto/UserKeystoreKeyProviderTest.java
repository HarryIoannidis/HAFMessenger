package com.haf.client.crypto;

import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.exceptions.KeystoreOperationException;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.EccKeyIO;
import org.junit.jupiter.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PublicKey;
import static org.junit.jupiter.api.Assertions.*;

class UserKeystoreKeyProviderTest {
    Path tmpRoot;
    char[] passphrase = "test-pass".toCharArray();

    @BeforeEach
    void setup() throws Exception {
        tmpRoot = Files.createTempDirectory("haf-test-keystore");
        FilePerms.ensureDir700(tmpRoot);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (tmpRoot != null) {
            try (var w = Files.walk(tmpRoot)) {
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // ignore
                    }
                });
            }
        }
    }

    @Test
    void get_sender_id_returns_keyId_from_current_key() throws Exception {
        // Create a key in keystore
        UserKeystore keyStore = new UserKeystore(tmpRoot);
        String keyId = UserKeystore.todayKeyId();
        KeyPair kp = EccKeyIO.generate();
        keyStore.saveKeypair(keyId, kp, passphrase);

        // Create KeyProvider
        UserKeystoreKeyProvider provider = new UserKeystoreKeyProvider(tmpRoot, keyId, passphrase);

        // Verify sender ID matches keyId
        assertEquals(keyId, provider.getSenderId());
    }

    @Test
    void get_recipient_public_key_loads_from_local_keystore() throws Exception {
        // Create two keys: sender and recipient
        UserKeystore keyStore = new UserKeystore(tmpRoot);
        String senderKeyId = "key-sender-001";
        String recipientKeyId = "key-recipient-001";

        KeyPair senderKp = EccKeyIO.generate();
        KeyPair recipientKp = EccKeyIO.generate();

        keyStore.saveKeypair(senderKeyId, senderKp, passphrase);
        keyStore.saveKeypair(recipientKeyId, recipientKp, passphrase);

        // Create KeyProvider (will use sender key as current)
        UserKeystoreKeyProvider provider = new UserKeystoreKeyProvider(tmpRoot, senderKeyId, passphrase);

        // Load recipient public key
        PublicKey loadedKey = provider.getRecipientPublicKey(recipientKeyId);

        assertNotNull(loadedKey);
        assertEquals(recipientKp.getPublic(), loadedKey);
    }

    @Test
    void get_recipient_public_key_throws_for_unknown_recipient() throws Exception {
        // Create only sender key
        UserKeystore keyStore = new UserKeystore(tmpRoot);
        String senderKeyId = "key-sender-001";
        KeyPair senderKp = EccKeyIO.generate();
        keyStore.saveKeypair(senderKeyId, senderKp, passphrase);

        UserKeystoreKeyProvider provider = new UserKeystoreKeyProvider(tmpRoot, senderKeyId, passphrase);

        // Try to load unknown recipient key
        assertThrows(KeyNotFoundException.class, () -> {
            provider.getRecipientPublicKey("unknown-recipient");
        });
    }

    @Test
    void get_key_store_returns_keystore_instance() throws Exception {
        UserKeystore keyStore = new UserKeystore(tmpRoot);
        String keyId = UserKeystore.todayKeyId();
        KeyPair kp = EccKeyIO.generate();
        keyStore.saveKeypair(keyId, kp, passphrase);

        UserKeystoreKeyProvider provider = new UserKeystoreKeyProvider(tmpRoot, keyId, passphrase);

        assertNotNull(provider.getKeyStore());
    }

    @Test
    void get_recipient_public_key_fetches_from_directory_service_when_missing_locally() throws Exception {
        // Create sender key
        String senderKeyId = "key-sender-001";
        KeyPair senderKp = EccKeyIO.generate();
        UserKeystore keyStore = new UserKeystore(tmpRoot);
        keyStore.saveKeypair(senderKeyId, senderKp, passphrase);

        // Generate an external recipient key (not in keystore)
        String externalRecipientId = "key-external-002";
        KeyPair externalKp = EccKeyIO.generate();
        String externalPem = EccKeyIO.publicPem(externalKp.getPublic());

        UserKeystoreKeyProvider provider = new UserKeystoreKeyProvider(tmpRoot, senderKeyId, passphrase);

        // Ensure exception is thrown without fetcher
        assertThrows(KeyNotFoundException.class, () -> {
            provider.getRecipientPublicKey(externalRecipientId);
        });

        // Set the fetcher to return the generated PEM
        provider.setDirectoryServiceFetcher(recipientId -> {
            if (externalRecipientId.equals(recipientId)) {
                return externalPem;
            }
            return null;
        });

        // Load key through provider (should use fetcher)
        PublicKey loadedKey = provider.getRecipientPublicKey(externalRecipientId);

        assertNotNull(loadedKey);
        assertArrayEquals(externalKp.getPublic().getEncoded(), loadedKey.getEncoded());
    }

    @Test
    void bootstrap_constructor_rejects_null_passphrase() {
        System.setProperty("haf.keystore.root", tmpRoot.toAbsolutePath().toString());
        try {
            assertThrows(KeystoreOperationException.class, () -> new UserKeystoreKeyProvider("user-a", null));
        } finally {
            System.clearProperty("haf.keystore.root");
        }
    }

    @Test
    void bootstrap_constructor_rejects_empty_passphrase() {
        System.setProperty("haf.keystore.root", tmpRoot.toAbsolutePath().toString());
        try {
            assertThrows(KeystoreOperationException.class, () -> new UserKeystoreKeyProvider("user-a", new char[0]));
        } finally {
            System.clearProperty("haf.keystore.root");
        }
    }
}
