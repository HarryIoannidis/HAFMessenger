package com.haf.server.config;

import com.haf.server.exceptions.ConfigurationException;
import com.haf.shared.constants.AttachmentConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ServerConfigTest {

    private static final String ENV_FILE_OVERRIDE_PROPERTY = "haf.server.env.path";

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
        assertEquals(String.join("/", "", "ws", "v1", "realtime"), config.getWsPath());
        assertEquals("test-jwt-secret", config.getJwtSecret());
        assertEquals(900L, config.getJwtAccessTtlSeconds());
        assertEquals(2_592_000L, config.getJwtRefreshTtlSeconds());
        assertEquals(2_592_000L, config.getJwtAbsoluteTtlSeconds());
        assertEquals(900L, config.getJwtIdleTtlSeconds());
        assertEquals("haf-login-sentinel-password", config.getLoginSentinelPassword());
        assertEquals(AttachmentConstants.DEFAULT_MAX_BYTES, config.getAttachmentMaxBytes());
        assertEquals(AttachmentConstants.DEFAULT_INLINE_MAX_BYTES, config.getAttachmentInlineMaxBytes());
        assertEquals(AttachmentConstants.DEFAULT_CHUNK_BYTES, config.getAttachmentChunkBytes());
        assertEquals(AttachmentConstants.DEFAULT_UNBOUND_TTL_SECONDS, config.getAttachmentUnboundTtlSeconds());
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
    void load_uses_custom_https_port() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_HTTPS_PORT", "9000");

        ServerConfig config = ServerConfig.fromEnv(env);
        assertEquals(9000, config.getHttpsPort());
    }

    @Test
    void load_uses_custom_ws_path() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_WS_PATH", "/custom/realtime");

        ServerConfig config = ServerConfig.fromEnv(env);

        assertEquals("/custom/realtime", config.getWsPath());
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
    void load_uses_custom_jwt_idle_ttl_when_configured() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_JWT_IDLE_TTL_SECONDS", "43200");

        ServerConfig config = ServerConfig.fromEnv(env);

        assertEquals(43200L, config.getJwtIdleTtlSeconds());
    }

    @Test
    void load_uses_custom_login_sentinel_password_when_configured() {
        Map<String, String> env = createMinimalEnv();
        env.put("HAF_LOGIN_SENTINEL_PASSWORD", "custom-sentinel-password");

        ServerConfig config = ServerConfig.fromEnv(env);

        assertEquals("custom-sentinel-password", config.getLoginSentinelPassword());
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

    @Test
    void resolve_env_file_path_uses_system_property_override() throws Exception {
        Path envFile = tempDir.resolve("server.env");
        Files.writeString(envFile, "HAF_DB_USER=override-user\n");

        System.setProperty(ENV_FILE_OVERRIDE_PROPERTY, envFile.toString());
        try {
            Path resolved = ServerConfig.resolveEnvFilePath(new HashMap<>());
            assertEquals(envFile, resolved);
        } finally {
            System.clearProperty(ENV_FILE_OVERRIDE_PROPERTY);
        }
    }

    @Test
    void resolve_env_file_path_fails_when_override_is_missing() {
        Path missing = tempDir.resolve("missing.env");
        System.setProperty(ENV_FILE_OVERRIDE_PROPERTY, missing.toString());
        try {
            assertThrows(ConfigurationException.class, () -> ServerConfig.resolveEnvFilePath(new HashMap<>()));
        } finally {
            System.clearProperty(ENV_FILE_OVERRIDE_PROPERTY);
        }
    }

    @Test
    void merge_env_file_overlays_entries() throws Exception {
        Path envFile = tempDir.resolve("values.env");
        Files.writeString(
                envFile,
                """
                # comment
                HAF_DB_USER=repo-user
                HAF_DB_PASS=repo-pass
                """);

        Map<String, String> env = new HashMap<>();
        env.put("HAF_DB_USER", "process-user");
        env.put("HAF_HTTPS_PORT", "8443");

        ServerConfig.mergeEnvFile(env, envFile);

        assertEquals("repo-user", env.get("HAF_DB_USER"));
        assertEquals("repo-pass", env.get("HAF_DB_PASS"));
        assertEquals("8443", env.get("HAF_HTTPS_PORT"));
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
                        (Consumer<Map<String, String>>) env -> env.put("HAF_DB_POOL_SIZE", "not-a-number")),
                Arguments.of(
                        "trust_proxy_enabled_without_cidrs",
                        (Consumer<Map<String, String>>) env -> { env.put("HAF_TRUST_PROXY", "true"); env.remove("HAF_TRUSTED_PROXY_CIDRS"); }),
                Arguments.of(
                        "trusted_proxy_cidr_is_invalid",
                        (Consumer<Map<String, String>>) env -> { env.put("HAF_TRUST_PROXY", "true"); env.put("HAF_TRUSTED_PROXY_CIDRS", "invalid-value"); }),
                Arguments.of(
                        "ingress_executor_values_are_invalid",
                        (Consumer<Map<String, String>>) env -> env.put("HAF_INGRESS_EXECUTOR_THREADS", "0")),
                Arguments.of(
                        "ws_path_contains_query",
                        (Consumer<Map<String, String>>) env -> env.put("HAF_WS_PATH", "/custom/realtime?token=bad")),
                Arguments.of(
                        "search_cursor_secret_missing",
                        (Consumer<Map<String, String>>) env -> env.remove("HAF_SEARCH_CURSOR_SECRET")),
                Arguments.of(
                        "jwt_secret_missing",
                        (Consumer<Map<String, String>>) env -> env.remove("HAF_JWT_SECRET")),
                Arguments.of(
                        "search_page_size_exceeds_max",
                        (Consumer<Map<String, String>>) env -> { env.put("HAF_SEARCH_PAGE_SIZE", "60"); env.put("HAF_SEARCH_MAX_PAGE_SIZE", "50"); }),
                Arguments.of(
                        "inline_attachment_limit_exceeds_max",
                        (Consumer<Map<String, String>>) env -> { env.put("HAF_ATTACHMENT_MAX_BYTES", "100"); env.put("HAF_ATTACHMENT_INLINE_MAX_BYTES", "101"); }),
                Arguments.of(
                        "jwt_absolute_ttl_is_less_than_refresh_ttl",
                        (Consumer<Map<String, String>>) env -> { env.put("HAF_JWT_REFRESH_TTL_SECONDS", "1200"); env.put("HAF_JWT_ABSOLUTE_TTL_SECONDS", "600"); }),
                Arguments.of(
                        "jwt_idle_ttl_is_too_low",
                        (Consumer<Map<String, String>>) env -> env.put("HAF_JWT_IDLE_TTL_SECONDS", "30")));
    }
}
