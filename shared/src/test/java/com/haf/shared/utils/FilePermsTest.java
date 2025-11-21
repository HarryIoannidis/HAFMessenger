package com.haf.shared.utils;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FilePermsTest {

    Path tmpDir;

    @BeforeEach
    void setup() throws Exception {
        tmpDir = Files.createTempDirectory("haf-perms-test");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (tmpDir != null) {
            try (var w = Files.walk(tmpDir)) {
                w.sorted((a,b)->b.getNameCount()-a.getNameCount()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {}
                });
            }
        }
    }

    @Test
    void ensureDir700_sets_correct_permissions_on_unix() throws Exception {
        Path d = tmpDir.resolve("ks-root");
        FilePerms.ensureDir700(d);
        assertTrue(Files.isDirectory(d));

        String os = System.getProperty("os.name","").toLowerCase();
        if (!os.contains("win")) {
            var perms = Files.getPosixFilePermissions(d);
            assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ));
            assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
            assertEquals(3, perms.size(), "dir must be 700");
        }
    }

    @Test
    void writeFile600_creates_file_with_correct_permissions_on_unix() throws Exception {
        Path d = tmpDir.resolve("ks-root2");
        FilePerms.ensureDir700(d);

        Path f = d.resolve("test.txt");
        FilePerms.writeFile600(f, "X".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertTrue(Files.isRegularFile(f));

        String os = System.getProperty("os.name","").toLowerCase();
        if (!os.contains("win")) {
            var perms = Files.getPosixFilePermissions(f);
            assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ));
            assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            assertEquals(2, perms.size(), "file must be 600");
        }
    }

    @Test
    void idempotent_calls_do_not_weaken_permissions() throws Exception {
        Path d = tmpDir.resolve("ks-root3");
        FilePerms.ensureDir700(d);
        FilePerms.ensureDir700(d); // δεύτερη φορά

        String os = System.getProperty("os.name","").toLowerCase();
        if (!os.contains("win")) {
            var perms = Files.getPosixFilePermissions(d);
            assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
            assertEquals(3, perms.size(), "dir must remain 700");
        }

        Path f = d.resolve("test2.txt");
        FilePerms.writeFile600(f, "Y".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        FilePerms.writeFile600(f, "Y2".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); // overwrite

        if (!System.getProperty("os.name","").toLowerCase().contains("win")) {
            var permsF = Files.getPosixFilePermissions(f);
            assertEquals(2, permsF.size(), "file must remain 600 on overwrite");
        }
    }
}
