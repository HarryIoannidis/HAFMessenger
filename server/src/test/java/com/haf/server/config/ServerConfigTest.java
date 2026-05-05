package com.haf.server.config;

import com.haf.server.exceptions.ConfigurationException;
import com.haf.shared.constants.AttachmentConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
        env.put("HAF_SEARCH_CURSOR_SECRET", "test-search-cursor-secret");
        env.put("HAF_JWT_SECRET", "test-jwt-secret");

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
        assertFalse(config.isTrustProxy());
        assertTrue(config.getTrustedProxyCidrs().isEmpty());
        assertEquals(64, config.getIngressExecutorThreads());
        assertEquals(1024, config.getIngressExecutorQueueCapacity());
        assertEquals("test-jwt-secret", config.getJwtSecret());
        assertEquals(900L, config.getJwtAccessTtlSeconds());
        assertEquals(2_592_000L, config.getJwtRefreshTtlSeconds());
        assertEquals(2_592_000L, config.getJwtAbsoluteTtlSeconds());
        assertEquals(900L, config.getJwtIdleTtlSeconds());
        assertEquals(AttachmentConstants.DEFAULT_MAX_BYTES, config.getAttachmentMaxBytes());
        assertEquals(AttachmentConstants.DEFAULT_INLINE_MAX_BYTES, config.getAttachmentInlineMaxBytes());
        assertEquals(AttachmentConstants.DEFAULT_CHUNK_BYTES, config.getAttachmentChunkBytes());
        assertEquals(AttachmentConstants.DEFAULT_UNBOUND_TTL_SECONDS, config.getAttachmentUnboundTtlSeconds());
        assertEquals(java.util.List.of(AttachmentConstants.MIME_TYPE_WILDCARD), config.getAttachmentAllowedTypes());
    }

    @Test
    void load_uses_custom_pool_size() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_DB_POOL_SIZE", "50");

        ServerConfig config = ServerConfig.fromEnv(env);
        assertEquals(50, config.getDbPoolSize());
    }

    @Test
    void load_uses_custom_proxy_and_ingress_executor_settings() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_TRUST_PROXY", "true");
        env.put("HAF_TRUSTED_PROXY_CIDRS", "10.0.0.0/8,192.168.1.10");
        env.put("HAF_INGRESS_EXECUTOR_THREADS", "32");
        env.put("HAF_INGRESS_EXECUTOR_QUEUE_CAPACITY", "2048");

        ServerConfig config = ServerConfig.fromEnv(env);

        assertTrue(config.isTrustProxy());
        assertEquals(2, config.getTrustedProxyCidrs().size());
        assertEquals("10.0.0.0/8", config.getTrustedProxyCidrs().get(0));
        assertEquals(32, config.getIngressExecutorThreads());
        assertEquals(2048, config.getIngressExecutorQueueCapacity());
    }

    @Test
    void load_fails_when_trust_proxy_enabled_without_cidrs() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_TRUST_PROXY", "true");
        env.remove("HAF_TRUSTED_PROXY_CIDRS");

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_fails_when_trusted_proxy_cidr_is_invalid() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_TRUST_PROXY", "true");
        env.put("HAF_TRUSTED_PROXY_CIDRS", "invalid-value");

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_fails_when_ingress_executor_values_are_invalid() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_INGRESS_EXECUTOR_THREADS", "0");

        assertThrows(ConfigurationException.class, () -> ServerConfig.fromEnv(env));
    }

    @Test
    void load_uses_custom_http_port() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_HTTP_PORT", "9000");

        ServerConfig config = ServerConfig.fromEnv(env);
        assertEquals(9000, config.getHttpPort());
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
    void load_uses_custom_jwt_idle_ttl_when_configured() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_JWT_IDLE_TTL_SECONDS", "43200");

        ServerConfig config = ServerConfig.fromEnv(env);

        assertEquals(43200L, config.getJwtIdleTtlSeconds());
    }

    @Test
    void load_accepts_attachment_policy_wildcards_and_deduplicates() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_ATTACHMENT_ALLOWED_TYPES", "image/*, image/png, */*, image/*");

        ServerConfig config = ServerConfig.fromEnv(env);

        assertEquals(java.util.List.of("image/*", "image/png", "*/*"), config.getAttachmentAllowedTypes());
    }

    @Test
    void load_fails_when_jwt_idle_ttl_is_too_low() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_JWT_IDLE_TTL_SECONDS", "30");

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
