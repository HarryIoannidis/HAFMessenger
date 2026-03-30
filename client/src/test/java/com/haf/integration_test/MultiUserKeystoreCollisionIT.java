package com.haf.integration_test;

import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FilePerms;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import static org.junit.jupiter.api.Assertions.*;

class MultiUserKeystoreCollisionIT {

    Path tmpRoot;
    char[] passphrase = "test-pass".toCharArray();

    @BeforeEach
    void setup() throws Exception {
        tmpRoot = Files.createTempDirectory("haf-collision-test");
        FilePerms.ensureDir700(tmpRoot);
        System.setProperty("haf.keystore.root", tmpRoot.toAbsolutePath().toString());
    }

    @AfterEach
    void cleanup() throws Exception {
        System.clearProperty("haf.keystore.root");
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
    void testKeystoreIsolation() throws Exception {
        String userA = "user-alice";
        String userB = "user-bob";

        // 1. Initialise User A
        UserKeystoreKeyProvider providerA = new UserKeystoreKeyProvider(userA, passphrase);
        KeyPair kpA = EccKeyIO.generate();
        String keyId = UserKeystore.todayKeyId();
        providerA.getKeyStore().saveKeypair(keyId, kpA, passphrase);

        // Verify User A can load their private key
        PrivateKey privA = providerA.getKeyStore().loadPrivate(keyId, passphrase);
        assertArrayEquals(kpA.getPrivate().getEncoded(), privA.getEncoded());

        // 2. Initialise User B (same day, same machine)
        UserKeystoreKeyProvider providerB = new UserKeystoreKeyProvider(userB, passphrase);
        KeyPair kpB = EccKeyIO.generate();
        providerB.getKeyStore().saveKeypair(keyId, kpB, passphrase);

        // Verify User B can load their private key
        PrivateKey privB = providerB.getKeyStore().loadPrivate(keyId, passphrase);
        assertArrayEquals(kpB.getPrivate().getEncoded(), privB.getEncoded());

        // 3. CRITICAL: Verify User A can still load THEIR private key (not B's)
        providerA = new UserKeystoreKeyProvider(userA, passphrase);
        PrivateKey privAAfter = providerA.getKeyStore().loadPrivate(keyId, passphrase);
        assertArrayEquals(kpA.getPrivate().getEncoded(), privAAfter.getEncoded());

        // Ensure they are actually different (sanity check)
        assertFalse(java.util.Arrays.equals(privA.getEncoded(), privB.getEncoded()));

        // Check filesystem structure
        assertTrue(Files.exists(tmpRoot.resolve("u-" + userA).resolve(keyId)));
        assertTrue(Files.exists(tmpRoot.resolve("u-" + userB).resolve(keyId)));
    }
}
