package com.haf.server.config;

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

    /**
     * Loads the server configuration from environment variables or a .env file.
     */
    public static ServerConfig load() {
        io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
                .directory("src/main/resources/config")
                .filename("variables.env")
                .ignoreIfMissing()
                .load();

        Map<String, String> env = new java.util.HashMap<>(System.getenv());
        if (dotenv != null) {
            dotenv.entries().forEach(entry -> env.putIfAbsent(entry.getKey(), entry.getValue()));
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

        this.tlsKeystorePath = Path.of(require(env, "HAF_TLS_KEYSTORE_PATH"));
        this.tlsKeystorePassword = require(env, "HAF_TLS_KEYSTORE_PASS").toCharArray();

        this.httpPort = parseInt(env.get("HAF_HTTP_PORT"), DEFAULT_HTTP_PORT);
        this.wsPort = parseInt(env.get("HAF_WS_PORT"), DEFAULT_WS_PORT);
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
     * Throws an IllegalStateException if the given key is missing or blank.
     */
    private static String require(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
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
            throw new IllegalStateException("Invalid integer value: " + candidate, ex);
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
                '}';
    }
}
