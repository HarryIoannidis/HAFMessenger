package com.haf.integration_test;

import com.haf.shared.keystore.UserKeystore;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.security.*;
import static org.junit.jupiter.api.Assertions.*;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.EccKeyIO;

class KeystoreTamperIT {
    Path tmpRoot;

    @BeforeEach
    void setup() throws Exception {
        tmpRoot = Files.createTempDirectory("haf-it-ks-tamper");
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
    void tampered_private_enc_must_fail_to_open() throws Exception {
        String keyId = UserKeystore.todayKeyId();
        KeyPair kp = EccKeyIO.generate();
        char[] pass = "secret-pass".toCharArray();
        UserKeystore ks = new UserKeystore(tmpRoot);
        ks.saveKeypair(keyId, kp, pass);
        Path dir = tmpRoot.resolve(keyId);

        Path enc = dir.resolve("private.enc");
        byte[] data = Files.readAllBytes(enc);
        data[data.length / 2] ^= 0x01; // αλλοίωση 1 byte στη μέση
        Files.write(enc, data);

        assertThrows(Exception.class, () -> ks.loadCurrentPrivate(pass));
    }

}
