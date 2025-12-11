package com.haf.client.crypto;

import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.RsaKeyIO;
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
        KeyPair kp = RsaKeyIO.generate(2048);
        keyStore.saveKeypair(keyId, kp, passphrase);

        // Create KeyProvider
        UserKeystoreKeyProvider provider = new UserKeystoreKeyProvider(tmpRoot, passphrase);

        // Verify sender ID matches keyId
        assertEquals(keyId, provider.getSenderId());
    }

    @Test
    void get_recipient_public_key_loads_from_local_keystore() throws Exception {
        // Create two keys: sender and recipient
        UserKeystore keyStore = new UserKeystore(tmpRoot);
        String senderKeyId = "key-sender-001";
        String recipientKeyId = "key-recipient-001";

        KeyPair senderKp = RsaKeyIO.generate(2048);
        KeyPair recipientKp = RsaKeyIO.generate(2048);

        keyStore.saveKeypair(senderKeyId, senderKp, passphrase);
        keyStore.saveKeypair(recipientKeyId, recipientKp, passphrase);

        // Create KeyProvider (will use sender key as current)
        UserKeystoreKeyProvider provider = new UserKeystoreKeyProvider(tmpRoot, passphrase);

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
        KeyPair senderKp = RsaKeyIO.generate(2048);
        keyStore.saveKeypair(senderKeyId, senderKp, passphrase);

        UserKeystoreKeyProvider provider = new UserKeystoreKeyProvider(tmpRoot, passphrase);

        // Try to load unknown recipient key
        assertThrows(KeyNotFoundException.class, () -> {
            provider.getRecipientPublicKey("unknown-recipient");
        });
    }

    @Test
    void get_key_store_returns_keystore_instance() throws Exception {
        UserKeystore keyStore = new UserKeystore(tmpRoot);
        String keyId = UserKeystore.todayKeyId();
        KeyPair kp = RsaKeyIO.generate(2048);
        keyStore.saveKeypair(keyId, kp, passphrase);

        UserKeystoreKeyProvider provider = new UserKeystoreKeyProvider(tmpRoot, passphrase);

        assertNotNull(provider.getKeyStore());
    }
}

