package com.haf.integration_test;

import com.haf.shared.keystore.UserKeystore;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import static org.junit.jupiter.api.Assertions.*;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.RsaKeyIO;
import java.util.Set;

class KeystorePermsE2EIT {
    Path tmpRoot;

    @BeforeEach
    void setup() throws Exception {
        tmpRoot = Files.createTempDirectory("haf-it-ks-perms");
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
    void save_keypair_sets_700_600_on_unix() throws Exception {
        String os = System.getProperty("os.name","").toLowerCase();
        String keyId = UserKeystore.todayKeyId();
        KeyPair kp = RsaKeyIO.generate(2048);
        char[] pass = "secret-pass".toCharArray();

        UserKeystore ks = new UserKeystore(tmpRoot);
        ks.saveKeypair(keyId, kp, pass);
        Path dir = tmpRoot.resolve(keyId);
        assertTrue(Files.isDirectory(dir));
        Path pub = dir.resolve("public.pem");
        Path enc = dir.resolve("private.enc");
        Path meta = dir.resolve("metadata.json");
        assertTrue(Files.exists(pub));
        assertTrue(Files.exists(enc));
        assertTrue(Files.exists(meta));

        if (!os.contains("win")) {
            Set<PosixFilePermission> d = Files.getPosixFilePermissions(dir);
            assertEquals(3, d.size(), "dir must be 700");
            assertTrue(d.contains(PosixFilePermission.OWNER_READ));
            assertTrue(d.contains(PosixFilePermission.OWNER_WRITE));
            assertTrue(d.contains(PosixFilePermission.OWNER_EXECUTE));

            Set<PosixFilePermission> p = Files.getPosixFilePermissions(pub);
            Set<PosixFilePermission> e = Files.getPosixFilePermissions(enc);
            Set<PosixFilePermission> m = Files.getPosixFilePermissions(meta);
            assertEquals(2, p.size(), "public.pem must be 600");
            assertEquals(2, e.size(), "private.enc must be 600");
            assertEquals(2, m.size(), "metadata.json must be 600");
        }
    }

}
