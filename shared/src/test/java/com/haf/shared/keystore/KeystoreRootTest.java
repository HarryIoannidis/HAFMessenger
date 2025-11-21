package com.haf.shared.keystore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class KeystoreRootTest {

    @AfterEach
    void clear() {
        System.clearProperty("haf.keystore.root");
    }

    @Test
    void preferred_honors_jvm_property() {
        System.setProperty("haf.keystore.root", "/tmp/haf-ks");
        assertEquals(Path.of("/tmp/haf-ks"), KeystoreRoot.preferred());
    }

    @Test
    void preferred_os_default_paths() {
        String os = System.getProperty("os.name","").toLowerCase();
        Path p = KeystoreRoot.preferred();
        if (os.contains("win")) {
            String s = p.toString().toLowerCase();
            assertTrue(s.contains("programdata") && s.contains("haf") && s.contains("keystore"));
        } else {
            assertEquals(Path.of("/var/lib/haf/keystore"), p);
        }
    }

    @Test
    void user_fallback_paths() {
        String os = System.getProperty("os.name","").toLowerCase();
        Path p = KeystoreRoot.userFallback();
        if (os.contains("win")) {
            String s = p.toString().toLowerCase();
            // είτε %LOCALAPPDATA%\HAF\keystore είτε %USERPROFILE%\AppData\Local\HAF\keystore
            assertTrue(s.contains("appdata") && s.contains("local") && s.contains("haf") && s.contains("keystore"));
        } else {
            String s = p.toString();
            assertTrue(s.contains(System.getProperty("user.home")));
            assertTrue(s.endsWith("/.local/share/haf/keystore") || s.contains("/.local/share/haf/keystore"));
        }
    }
}
