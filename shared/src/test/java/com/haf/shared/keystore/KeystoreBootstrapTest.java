package com.haf.shared.keystore;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KeystoreBootstrapTest {

    @BeforeEach
    void setup() throws Exception {
        // Εξαναγκάζουμε fallback: δείχνουμε preferred σε μη εγγράψιμο path για τρέχον χρήστη
        // Χωρίς να πειράξουμε το σύστημα, βάζουμε ιδιότητα σε φάκελο που δεν υπάρχει/δεν πρέπει να γραφτεί.
        System.setProperty("haf.keystore.root", "/root/___deny___");
        Path uf = KeystoreRoot.userFallback();
        if (Files.exists(uf)) {
            try (var s = Files.list(uf)) {
                s.forEach(p -> {
                    try {
                        if (Files.isDirectory(p)) Files.walk(p).sorted((a,b)->b.getNameCount()-a.getNameCount()).forEach(pp -> { try { Files.deleteIfExists(pp);} catch(Exception ignored){} });
                        else Files.deleteIfExists(p);
                    } catch(Exception ignored){}
                });
            }
        }
    }

    @AfterEach
    void clear() {
        System.clearProperty("haf.keystore.root");
    }

    @Test
    void bootstrap_creates_user_fallback_with_keys_and_permissions() throws Exception {
        Path root = KeystoreBootstrap.run(); // πρέπει να πέσει αυτόματα στο userFallback

        assertEquals(KeystoreRoot.userFallback().normalize(), root.normalize());

        assertTrue(Files.isDirectory(root));

        String os = System.getProperty("os.name","").toLowerCase();
        if (!os.contains("win")) {
            var perms = Files.getPosixFilePermissions(root);
            assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ));
            assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
            assertEquals(3, perms.size(), "dir must be 700");
        }

        try (var stream = Files.list(root)) {
            Path keyDir = stream.filter(Files::isDirectory).findFirst().orElseThrow();
            Path pub = keyDir.resolve("public.pem");
            Path prv = keyDir.resolve("private.enc");
            Path meta = keyDir.resolve("metadata.json");

            assertTrue(Files.isRegularFile(pub));
            assertTrue(Files.isRegularFile(prv));
            assertTrue(Files.isRegularFile(meta));

            if (!os.contains("win")) {
                var p1 = Files.getPosixFilePermissions(pub);
                var p2 = Files.getPosixFilePermissions(prv);
                var p3 = Files.getPosixFilePermissions(meta);
                assertEquals(2, p1.size(), "file must be 600");
                assertEquals(2, p2.size(), "file must be 600");
                assertEquals(2, p3.size(), "file must be 600");
            }
        }
    }

}
