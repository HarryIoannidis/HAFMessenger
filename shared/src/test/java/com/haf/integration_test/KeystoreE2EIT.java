package com.haf.integration_test;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.security.*;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FilePerms;
import javax.crypto.KeyAgreement;
import static org.junit.jupiter.api.Assertions.*;

class KeystoreE2EIT {
    Path tmpRoot;

    @BeforeEach
    void setup() throws Exception {
        tmpRoot = Files.createTempDirectory("haf-it-ks");
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
    void perform_ecdh_with_loaded_private() throws Exception {
        String keyId = UserKeystore.todayKeyId();
        KeyPair kp = EccKeyIO.generate();
        char[] pass = "secret-pass".toCharArray();

        UserKeystore ks = new UserKeystore(tmpRoot);
        ks.saveKeypair(keyId, kp, pass);
        Path dir = tmpRoot.resolve(keyId);
        assertTrue(Files.exists(dir.resolve("public.pem")));
        assertTrue(Files.exists(dir.resolve("private.enc")));

        PublicKey pub = ks.loadCurrentPublic();
        PrivateKey prv = ks.loadCurrentPrivate(pass);

        // Generate an ephemeral keypair to test agreement
        KeyPair ephemeral = EccKeyIO.generate();

        // 1. Agree on sender side
        KeyAgreement senderKA = KeyAgreement.getInstance("XDH");
        senderKA.init(ephemeral.getPrivate());
        senderKA.doPhase(pub, true);
        byte[] senderSecret = senderKA.generateSecret();

        // 2. Agree on recipient side
        KeyAgreement recipientKA = KeyAgreement.getInstance("XDH");
        recipientKA.init(prv);
        recipientKA.doPhase(ephemeral.getPublic(), true);
        byte[] recipientSecret = recipientKA.generateSecret();

        assertArrayEquals(senderSecret, recipientSecret);
    }

}
