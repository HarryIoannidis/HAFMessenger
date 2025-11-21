package com.haf.integration_test;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.security.*;
import javax.crypto.Cipher;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.utils.RsaKeyIO;
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
                w.sorted((a,b)->b.getNameCount()-a.getNameCount()).forEach(p -> {
                    try { Files.deleteIfExists(p);} catch (Exception ignored) {}
                });
            }
        }
    }

    @Test
    void encrypt_with_public_then_decrypt_with_loaded_private() throws Exception {
        String keyId = UserKeystore.todayKeyId();
        KeyPair kp = RsaKeyIO.generate(2048);
        char[] pass = "secret-pass".toCharArray();

        UserKeystore ks = new UserKeystore(tmpRoot);
        ks.saveKeypair(keyId, kp, pass);
        Path dir = tmpRoot.resolve(keyId);
        assertTrue(Files.exists(dir.resolve("public.pem")));
        assertTrue(Files.exists(dir.resolve("private.enc")));

        PublicKey pub = ks.loadCurrentPublic();
        PrivateKey prv = ks.loadCurrentPrivate(pass);

        byte[] msg = "HAF-IT-PAYLOAD".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Cipher enc = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        enc.init(Cipher.ENCRYPT_MODE, pub);
        byte[] ct = enc.doFinal(msg);

        Cipher dec = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        dec.init(Cipher.DECRYPT_MODE, prv);
        byte[] pt = dec.doFinal(ct);

        assertArrayEquals(msg, pt);
    }

}
