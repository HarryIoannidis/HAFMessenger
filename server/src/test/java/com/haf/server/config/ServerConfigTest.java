package com.haf.server.config;

import com.haf.server.exceptions.ConfigurationException;
import com.haf.shared.constants.AttachmentConstants;
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
        env.put("HAF_SEARCH_CURSOR_SECRET", "test-search-cursor-secret");

        ServerConfig config = ServerConfig.fromEnv(env);

        assertEquals("jdbc:mysql://localhost:3306/test", config.getDbUrl());
        assertEquals("testuser", config.getDbUser());
        assertEquals("testpass", config.getDbPassword());
        assertEquals(20, config.getDbPoolSize()); // default
        assertEquals(tempDir, config.getKeystoreRoot());
        assertEquals(tempDir.resolve("keystore.p12"), config.getTlsKeystorePath());
        assertEquals(20, config.getSearchPageSize());
        assertEquals(50, config.getSearchMaxPageSize());
        assertEquals(3, config.getSearchMinQueryLength());
        assertEquals(AttachmentConstants.DEFAULT_MAX_BYTES, config.getAttachmentMaxBytes());
        assertEquals(AttachmentConstants.DEFAULT_INLINE_MAX_BYTES, config.getAttachmentInlineMaxBytes());
        assertEquals(AttachmentConstants.DEFAULT_CHUNK_BYTES, config.getAttachmentChunkBytes());
        assertEquals(AttachmentConstants.DEFAULT_UNBOUND_TTL_SECONDS, config.getAttachmentUnboundTtlSeconds());
        assertTrue(config.getAttachmentAllowedTypes().contains("application/pdf"));
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

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_fails_when_blank_required_var() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_DB_URL", "   ");

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_fails_when_invalid_pool_size() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_DB_POOL_SIZE", "not-a-number");

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_fails_when_search_cursor_secret_missing() {
        Map<String, String> env = createMinimalEnv();
        env.remove("HAF_SEARCH_CURSOR_SECRET");

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_fails_when_search_page_size_exceeds_max() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_SEARCH_PAGE_SIZE", "60");
        env.put("HAF_SEARCH_MAX_PAGE_SIZE", "50");

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_fails_when_inline_attachment_limit_exceeds_max() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_ATTACHMENT_MAX_BYTES", "100");
        env.put("HAF_ATTACHMENT_INLINE_MAX_BYTES", "101");

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
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
        env.put("HAF_SEARCH_CURSOR_SECRET", "test-search-cursor-secret");
        return env;
    }
}
