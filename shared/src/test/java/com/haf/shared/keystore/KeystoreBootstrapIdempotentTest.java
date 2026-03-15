package com.haf.shared.keystore;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KeystoreBootstrapIdempotentTest {
    private String originalUserHome;
    private Path tempHome;

    @BeforeEach
    void setup() throws Exception {
        originalUserHome = System.getProperty("user.home");
        tempHome = Files.createTempDirectory("haf-keystore-home-idempotent");
        System.setProperty("user.home", tempHome.toString());
        System.setProperty("haf.keystore.root", "/root/___deny___");
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
    void run_is_idempotent_when_keys_exist() throws Exception {
        Path root1 = KeystoreBootstrap.run();
        long count1;
        try (var s = Files.list(root1)) {
            count1 = s.filter(Files::isDirectory).count();
        }

        Path root2 = KeystoreBootstrap.run();
        long count2;
        try (var s = Files.list(root2)) {
            count2 = s.filter(Files::isDirectory).count();
        }

        assertEquals(root1.normalize(), root2.normalize());
        assertEquals(count1, count2, "second run must not create extra key directories");
    }
}
