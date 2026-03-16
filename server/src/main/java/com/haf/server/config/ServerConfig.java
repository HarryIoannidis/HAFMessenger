package com.haf.server.config;

import com.haf.server.exceptions.ConfigurationException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Centralized configuration loader for the server runtime.
 * Reads environment variables, validates them, and exposes typed accessors.
 */
public final class ServerConfig {

    private static final int DEFAULT_DB_POOL_SIZE = 20;
    private static final int DEFAULT_HTTP_PORT = 8443;
    private static final int DEFAULT_WS_PORT = 8444;
    private static final int DEFAULT_SEARCH_PAGE_SIZE = 20;
    private static final int DEFAULT_SEARCH_MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_SEARCH_MIN_QUERY_LENGTH = 3;
    private static final int DEFAULT_SEARCH_MAX_QUERY_LENGTH = 128;

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final int dbPoolSize;

    private final Path keystoreRoot;
    private final char[] keyPassword;

    private final Path tlsKeystorePath;
    private final char[] tlsKeystorePassword;

    private final int httpPort;
    private final int wsPort;
    private final String adminPublicKeyPem;
    private final int searchPageSize;
    private final int searchMaxPageSize;
    private final int searchMinQueryLength;
    private final int searchMaxQueryLength;
    private final String searchCursorSecret;

    /**
     * Loads the server configuration from environment variables or a .env file.
     */
    public static ServerConfig load() {
        Map<String, String> env = new java.util.HashMap<>(System.getenv());

        java.io.File envFile = new java.io.File("server/src/main/resources/config/variables.env");
        if (!envFile.exists()) {
            envFile = new java.io.File("src/main/resources/config/variables.env");
        }

        if (envFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        int eqIndex = line.indexOf('=');
                        if (eqIndex > 0) {
                            String key = line.substring(0, eqIndex).trim();
                            String value = line.substring(eqIndex + 1).trim();
                            env.put(key, value);
                        }
                    }
                }
            } catch (java.io.IOException e) {
                // Ignore
            }
        }

        return new ServerConfig(env);
    }

    /**
     * Creates a new ServerConfig.
     *
     * @param env the environment variables to read from.
     */
    private ServerConfig(Map<String, String> env) {
        this.dbUrl = require(env, "HAF_DB_URL");
        this.dbUser = require(env, "HAF_DB_USER");
        this.dbPassword = require(env, "HAF_DB_PASS");
        this.dbPoolSize = parseInt(env.get("HAF_DB_POOL_SIZE"), DEFAULT_DB_POOL_SIZE);

        this.keystoreRoot = env.get("HAF_KEYSTORE_ROOT") != null ? Path.of(env.get("HAF_KEYSTORE_ROOT")) : null;
        this.keyPassword = require(env, "HAF_KEY_PASS").toCharArray();

        Path tls = Path.of(require(env, "HAF_TLS_KEYSTORE_PATH"));
        if (!java.nio.file.Files.exists(tls) && tls.toString().startsWith("server/")) {
            tls = Path.of(tls.toString().substring(7));
        } else if (!java.nio.file.Files.exists(tls) && !tls.toString().startsWith("server/")) {
            Path altTls = Path.of("server").resolve(tls);
            if (java.nio.file.Files.exists(altTls))
                tls = altTls;
        }
        this.tlsKeystorePath = tls;
        this.tlsKeystorePassword = require(env, "HAF_TLS_KEYSTORE_PASS").toCharArray();

        this.httpPort = parseInt(env.get("HAF_HTTP_PORT"), DEFAULT_HTTP_PORT);
        this.wsPort = parseInt(env.get("HAF_WS_PORT"), DEFAULT_WS_PORT);
        this.adminPublicKeyPem = env.getOrDefault("HAF_ADMIN_PUBLIC_KEY", null);

        this.searchPageSize = parseInt(env.get("HAF_SEARCH_PAGE_SIZE"), DEFAULT_SEARCH_PAGE_SIZE);
        this.searchMaxPageSize = parseInt(env.get("HAF_SEARCH_MAX_PAGE_SIZE"), DEFAULT_SEARCH_MAX_PAGE_SIZE);
        this.searchMinQueryLength = parseInt(env.get("HAF_SEARCH_MIN_QUERY_LENGTH"), DEFAULT_SEARCH_MIN_QUERY_LENGTH);
        this.searchMaxQueryLength = parseInt(env.get("HAF_SEARCH_MAX_QUERY_LENGTH"), DEFAULT_SEARCH_MAX_QUERY_LENGTH);
        this.searchCursorSecret = require(env, "HAF_SEARCH_CURSOR_SECRET");

        validateSearchConfig();
    }

    /**
     * Package-private for testing
     * Creates a ServerConfig from the given environment variables.
     *
     * @param env the environment variables to read from.
     */
    static ServerConfig fromEnv(Map<String, String> env) {
        return new ServerConfig(env);
    }

    /**
     * Returns the database URL.
     */
    public String getDbUrl() {
        return dbUrl;
    }

    /**
     * Returns the database user.
     */
    public String getDbUser() {
        return dbUser;
    }

    /**
     * Returns the database password.
     */
    public String getDbPassword() {
        return dbPassword;
    }

    /**
     * Returns the database pool size.
     */
    public int getDbPoolSize() {
        return dbPoolSize;
    }

    /**
     * Returns the path to the keystore root.
     */
    public Path getKeystoreRoot() {
        return keystoreRoot;
    }

    /**
     * Returns the password for the key in the keystore.
     */
    public char[] getKeyPassword() {
        return keyPassword.clone();
    }

    /**
     * Returns the path to the TLS keystore.
     */
    public Path getTlsKeystorePath() {
        return tlsKeystorePath;
    }

    /**
     * Returns the password for the TLS keystore.
     */
    public char[] getTlsKeystorePassword() {
        return tlsKeystorePassword.clone();
    }

    /**
     * Returns the HTTP port.
     */
    public int getHttpPort() {
        return httpPort;
    }

    /**
     * Returns the WebSocket port.
     */
    public int getWsPort() {
        return wsPort;
    }

    /**
     * Returns the Admin's X25519 Public Key PEM string, or {@code null} if not
     * configured.
     * Clients fetch this key to E2E-encrypt registration photos for admin review.
     */
    public String getAdminPublicKeyPem() {
        return adminPublicKeyPem;
    }

    /**
     * Returns the default server-side search page size.
     */
    public int getSearchPageSize() {
        return searchPageSize;
    }

    /**
     * Returns the maximum allowed search page size.
     */
    public int getSearchMaxPageSize() {
        return searchMaxPageSize;
    }

    /**
     * Returns the minimum query length for user search.
     */
    public int getSearchMinQueryLength() {
        return searchMinQueryLength;
    }

    /**
     * Returns the maximum query length for user search.
     */
    public int getSearchMaxQueryLength() {
        return searchMaxQueryLength;
    }

    /**
     * Returns the shared secret used to sign search pagination cursors.
     */
    public String getSearchCursorSecret() {
        return searchCursorSecret;
    }

    /**
     * Throws a ConfigurationException if the given key is missing or blank.
     */
    private static String require(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing required environment variable: " + key);
        }
        return value;
    }

    /**
     * Parses an integer value from the given candidate string.
     *
     * @param candidate    the candidate string to parse.
     * @param defaultValue the default value to return if the candidate is blank or
     *                     invalid.
     * @return the parsed integer value.
     */
    private static int parseInt(String candidate, int defaultValue) {
        if (candidate == null || candidate.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(candidate);
        } catch (NumberFormatException ex) {
            throw new ConfigurationException("Invalid integer value: " + candidate, ex);
        }
    }

    private void validateSearchConfig() {
        if (searchPageSize < 1) {
            throw new ConfigurationException("HAF_SEARCH_PAGE_SIZE must be >= 1");
        }
        if (searchMaxPageSize < 1) {
            throw new ConfigurationException("HAF_SEARCH_MAX_PAGE_SIZE must be >= 1");
        }
        if (searchPageSize > searchMaxPageSize) {
            throw new ConfigurationException("HAF_SEARCH_PAGE_SIZE must be <= HAF_SEARCH_MAX_PAGE_SIZE");
        }
        if (searchMinQueryLength < 1) {
            throw new ConfigurationException("HAF_SEARCH_MIN_QUERY_LENGTH must be >= 1");
        }
        if (searchMaxQueryLength < searchMinQueryLength) {
            throw new ConfigurationException("HAF_SEARCH_MAX_QUERY_LENGTH must be >= HAF_SEARCH_MIN_QUERY_LENGTH");
        }
    }

    /**
     * Returns a string representation of this object.
     */
    @Override
    public String toString() {
        return "ServerConfig{" +
                "dbUrl='" + dbUrl + '\'' +
                ", dbUser='" + dbUser + '\'' +
                ", dbPoolSize=" + dbPoolSize +
                ", httpPort=" + httpPort +
                ", wsPort=" + wsPort +
                ", keystoreRoot=" + keystoreRoot +
                ", tlsKeystorePath=" + tlsKeystorePath +
                ", searchPageSize=" + searchPageSize +
                ", searchMaxPageSize=" + searchMaxPageSize +
                ", searchMinQueryLength=" + searchMinQueryLength +
                ", searchMaxQueryLength=" + searchMaxQueryLength +
                '}';
    }
}
