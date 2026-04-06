package com.haf.server.config;

import com.haf.server.exceptions.ConfigurationException;
import com.haf.shared.constants.AttachmentConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
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
        env.put("HAF_APP_IS_DEV", "true");
        env.put("HAF_SEARCH_CURSOR_SECRET", "test-search-cursor-secret");
        env.put("HAF_JWT_SECRET", "test-jwt-secret");

        ServerConfig config = ServerConfig.fromEnv(env);

        assertEquals("jdbc:mysql://localhost:3306/test", config.getDbUrl());
        assertEquals("testuser", config.getDbUser());
        assertEquals("testpass", config.getDbPassword());
        assertEquals(20, config.getDbPoolSize()); // default
        assertEquals(tempDir, config.getKeystoreRoot());
        assertEquals(tempDir.resolve("keystore.p12"), config.getTlsKeystorePath());
        assertTrue(config.isDevMode());
        assertEquals(20, config.getSearchPageSize());
        assertEquals(50, config.getSearchMaxPageSize());
        assertEquals(3, config.getSearchMinQueryLength());
        assertEquals("test-jwt-secret", config.getJwtSecret());
        assertEquals(900L, config.getJwtAccessTtlSeconds());
        assertEquals(2_592_000L, config.getJwtRefreshTtlSeconds());
        assertEquals(2_592_000L, config.getJwtAbsoluteTtlSeconds());
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

    @ParameterizedTest
    @ValueSource(strings = { "true", "false" })
    void load_uses_explicit_app_is_dev_flag_when_provided(String appIsDevValue) {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_APP_IS_DEV", appIsDevValue);

        ServerConfig config = ServerConfig.fromEnv(env);

        assertEquals(Boolean.parseBoolean(appIsDevValue), config.isDevMode());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", " ", "nope" })
    void load_fails_when_app_is_dev_is_missing_or_invalid(String appIsDevValue) {
        Map<String, String> env = createMinimalEnv();
        if (appIsDevValue == null) {
            env.remove("HAF_APP_IS_DEV");
        } else {
            env.put("HAF_APP_IS_DEV", appIsDevValue);
        }

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequiredConfigurationMutations")
    void load_fails_when_required_configuration_is_invalid(
            String scenario,
            Consumer<Map<String, String>> envMutator) {
        Map<String, String> env = createMinimalEnv();
        envMutator.accept(env);

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_fails_when_search_cursor_secret_missing() {
        Map<String, String> env = createMinimalEnv();
        env.remove("HAF_SEARCH_CURSOR_SECRET");

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_fails_when_jwt_secret_missing() {
        Map<String, String> env = createMinimalEnv();
        env.remove("HAF_JWT_SECRET");

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
    void load_fails_when_jwt_absolute_ttl_is_less_than_refresh_ttl() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_JWT_REFRESH_TTL_SECONDS", "1200");
        env.put("HAF_JWT_ABSOLUTE_TTL_SECONDS", "600");

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
        env.put("HAF_APP_IS_DEV", "true");
        env.put("HAF_SEARCH_CURSOR_SECRET", "test-search-cursor-secret");
        env.put("HAF_JWT_SECRET", "test-jwt-secret");
        return env;
    }

    private static Stream<Arguments> invalidRequiredConfigurationMutations() {
        return Stream.of(
                Arguments.of(
                        "missing_required_db_user",
                        (Consumer<Map<String, String>>) env -> env.remove("HAF_DB_USER")),
                Arguments.of(
                        "blank_required_db_url",
                        (Consumer<Map<String, String>>) env -> env.put("HAF_DB_URL", "   ")),
                Arguments.of(
                        "invalid_db_pool_size",
                        (Consumer<Map<String, String>>) env -> env.put("HAF_DB_POOL_SIZE", "not-a-number")));
    }
}
