package com.haf.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void load_success_with_all_required_vars() {
        Map<String, String> env = new HashMap<>();
        env.put("HAF_DB_URL", "jdbc:mysql://localhost:3306/test");
        env.put("HAF_DB_USER", "testuser");
        env.put("HAF_DB_PASS", "testpass");
        env.put("HAF_KEYSTORE_ROOT", tempDir.toString());
        env.put("HAF_KEY_PASS", "keypass");
        env.put("HAF_TLS_KEYSTORE_PATH", tempDir.resolve("keystore.p12").toString());
        env.put("HAF_TLS_KEYSTORE_PASS", "tlspass");

        ServerConfig config = ServerConfig.fromEnv(env);

        assertEquals("jdbc:mysql://localhost:3306/test", config.getDbUrl());
        assertEquals("testuser", config.getDbUser());
        assertEquals("testpass", config.getDbPassword());
        assertEquals(20, config.getDbPoolSize()); // default
        assertEquals(tempDir, config.getKeystoreRoot());
        assertEquals(tempDir.resolve("keystore.p12"), config.getTlsKeystorePath());
    }

    @Test
    void load_uses_custom_pool_size() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_DB_POOL_SIZE", "50");

        ServerConfig config = ServerConfig.fromEnv(env);
        assertEquals(50, config.getDbPoolSize());
    }

    @Test
    void load_uses_custom_ports() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_HTTP_PORT", "9000");
        env.put("HAF_WS_PORT", "9001");

        ServerConfig config = ServerConfig.fromEnv(env);
        assertEquals(9000, config.getHttpPort());
        assertEquals(9001, config.getWsPort());
    }

    @Test
    void load_fails_when_missing_required_var() {
        Map<String, String> env = new HashMap<>();
        env.put("HAF_DB_URL", "jdbc:mysql://localhost:3306/test");
        // Missing HAF_DB_USER

        assertThrows(IllegalStateException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_fails_when_blank_required_var() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_DB_URL", "   ");

        assertThrows(IllegalStateException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_fails_when_invalid_pool_size() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_DB_POOL_SIZE", "not-a-number");

        assertThrows(IllegalStateException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void get_key_password_returns_clone() {
        Map<String, String> env = createMinimalEnv();
        ServerConfig config = ServerConfig.fromEnv(env);

        char[] first = config.getKeyPassword();
        char[] second = config.getKeyPassword();

        assertNotSame(first, second);
        assertArrayEquals(first, second);
    }

    @Test
    void get_tls_keystore_password_returns_clone() {
        Map<String, String> env = createMinimalEnv();
        ServerConfig config = ServerConfig.fromEnv(env);

        char[] first = config.getTlsKeystorePassword();
        char[] second = config.getTlsKeystorePassword();

        assertNotSame(first, second);
        assertArrayEquals(first, second);
    }

    private Map<String, String> createMinimalEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("HAF_DB_URL", "jdbc:mysql://localhost:3306/test");
        env.put("HAF_DB_USER", "testuser");
        env.put("HAF_DB_PASS", "testpass");
        env.put("HAF_KEYSTORE_ROOT", tempDir.toString());
        env.put("HAF_KEY_PASS", "keypass");
        env.put("HAF_TLS_KEYSTORE_PATH", tempDir.resolve("keystore.p12").toString());
        env.put("HAF_TLS_KEYSTORE_PASS", "tlspass");
        return env;
    }
}

