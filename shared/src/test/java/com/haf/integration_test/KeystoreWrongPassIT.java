package com.haf.integration_test;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.security.*;
import static org.junit.jupiter.api.Assertions.*;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.utils.RsaKeyIO;

class KeystoreWrongPassIT {
    Path tmpRoot;

    @BeforeEach
    void setup() throws Exception {
        tmpRoot = Files.createTempDirectory("haf-it-ks-wp");
        FilePerms.ensureDir700(tmpRoot);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (tmpRoot != null) {
            try (var w = Files.walk(tmpRoot)) {
                w.sorted((a,b)->b.getNameCount()-a.getNameCount()).forEach(p -> {
                    try { Files.deleteIfExists(p);} catch (Exception ignored) {}
                });
            }
        }
    }

    @Test
    void load_private_with_wrong_pass_must_fail() throws Exception {
        // 1) Δημιουργία CURRENT με σωστό pass
        String keyId = UserKeystore.todayKeyId();
        KeyPair kp = RsaKeyIO.generate(2048);
        char[] correct = "secret-pass".toCharArray();
        UserKeystore ks = new UserKeystore(tmpRoot);
        ks.saveKeypair(keyId, kp, correct);

        // 2) Απόπειρα φόρτωσης με λάθος pass -> αναμένουμε εξαίρεση (AES-GCM tag)
        char[] wrong = "wrong-pass".toCharArray();
        assertThrows(Exception.class, () -> ks.loadCurrentPrivate(wrong));
    }

}
