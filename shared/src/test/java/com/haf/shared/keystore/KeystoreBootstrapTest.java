package com.haf.shared.keystore;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KeystoreBootstrapTest {
    private String originalUserHome;
    private Path tempHome;

    @BeforeEach
    void setup() throws Exception {
        originalUserHome = System.getProperty("user.home");
        tempHome = Files.createTempDirectory("haf-keystore-home");
        System.setProperty("user.home", tempHome.toString());

        // Check OS
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("win")) {
            // On Windows, use a path with invalid characters (< > : " / \ | ? *) to force an exception.
            // "C:\Invalid:Path" will throw InvalidPathException or IOException.
            System.setProperty("haf.keystore.root", "C:\\Windows\\System32\\config\\RegBack\\Protected");
        } else {
            // Linux/Mac - /root is usually protected
            System.setProperty("haf.keystore.root", "/root/___deny___");
        }

        // Fallback path resolves under tempHome, so no global cleanup is needed.
    }

    @AfterEach
    void clear() throws Exception {
        System.clearProperty("haf.keystore.root");
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
        }
        if (tempHome != null && Files.exists(tempHome)) {
            try (var walk = Files.walk(tempHome)) {
                walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> {
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
    void bootstrap_creates_user_fallback_with_keys_and_permissions() throws Exception {
        System.out.println("Loaded from: " + KeystoreRoot.class.getProtectionDomain().getCodeSource().getLocation());
        Path root = KeystoreBootstrap.run();

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
