package com.haf.client.utils;

import com.haf.client.exceptions.ClientConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;
import java.util.Properties;

/**
 * Resolves mode-aware client runtime endpoints from {@code client.properties}.
 * Development mode uses localhost defaults and allows an HTTPS health-check URL
 * override from properties, system properties, or environment variables.
 * Production mode requires explicit HTTPS endpoint values in
 * {@code client.properties}; websocket URI is derived for compatibility.
 */
public final class ClientRuntimeConfig {

    /**
     * Messaging transport policy selected at runtime.
     */
    public enum MessagingTransportMode {
        WEBSOCKET,
        HTTPS_POLLING
    }

    private static final String KEY_APP_IS_DEV = "app.isDev";
    private static final String KEY_SERVER_URL = "server.url";
    private static final String KEY_SERVER_URL_PROD = "server.url.prod";
    private static final String KEY_HELP_CENTER_URL_PROD = "help.center.url.prod";

    private static final URI DEV_SERVER_BASE_URI = URI.create("https://localhost:8443");
    private static final URI DEV_WEBSOCKET_BASE_URI = URI.create("wss://localhost:8444/");

    private final boolean dev;
    private final URI serverBaseUri;
    private final URI webSocketBaseUri;
    private final URI helpCenterBaseUri;
    private final URI healthCheckBaseUri;
    private final MessagingTransportMode messagingTransportMode;

    private ClientRuntimeConfig(boolean dev,
            URI serverBaseUri,
            URI webSocketBaseUri,
            URI helpCenterBaseUri,
            URI healthCheckBaseUri,
            MessagingTransportMode messagingTransportMode) {
        this.dev = dev;
        this.serverBaseUri = Objects.requireNonNull(serverBaseUri, "serverBaseUri");
        this.webSocketBaseUri = Objects.requireNonNull(webSocketBaseUri, "webSocketBaseUri");
        this.helpCenterBaseUri = Objects.requireNonNull(helpCenterBaseUri, "helpCenterBaseUri");
        this.healthCheckBaseUri = Objects.requireNonNull(healthCheckBaseUri, "healthCheckBaseUri");
        this.messagingTransportMode = Objects.requireNonNull(messagingTransportMode, "messagingTransportMode");
    }

    /**
     * Loads runtime mode and endpoint configuration from
     * {@code /config/client.properties}.
     * In development mode, health-check URL overrides are resolved with this
     * precedence:
     * {@code server.url} property, then {@code -Dhaf.server.url}, then
     * {@code HAF_SERVER_URL}.
     *
     * @return resolved runtime configuration
     */
    public static ClientRuntimeConfig load() {
        Properties properties = new Properties();
        try (InputStream in = ClientRuntimeConfig.class.getResourceAsStream(UiConstants.CONFIG_CLIENT_PROPERTIES)) {
            if (in == null) {
                throw new ClientConfigurationException(
                        "Could not find " + UiConstants.CONFIG_CLIENT_PROPERTIES + " in resources.");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new ClientConfigurationException("Failed to load " + UiConstants.CONFIG_CLIENT_PROPERTIES, e);
        }

        return fromProperties(
                properties,
                System.getProperty("haf.server.url"),
                System.getenv("HAF_SERVER_URL"));
    }

    /**
     * Builds runtime configuration from already loaded properties.
     * This overload does not apply development-mode external overrides.
     *
     * @param properties source properties
     * @return resolved runtime configuration
     */
    public static ClientRuntimeConfig fromProperties(Properties properties) {
        return fromProperties(properties, null, null);
    }

    /**
     * Builds runtime configuration from properties with optional development-mode
     * health-check URL overrides.
     *
     * @param properties         source properties
     * @param devSystemServerUrl optional value equivalent to
     *                           {@code -Dhaf.server.url}
     * @param devEnvServerUrl    optional value equivalent to
     *                           {@code HAF_SERVER_URL}
     * @return resolved runtime configuration
     */
    static ClientRuntimeConfig fromProperties(Properties properties,
            String devSystemServerUrl,
            String devEnvServerUrl) {
        Objects.requireNonNull(properties, "properties");

        boolean isDev = parseIsDev(properties.getProperty(KEY_APP_IS_DEV));
        if (isDev) {
            URI healthBaseUri = resolveDevHealthBaseUri(properties, devSystemServerUrl, devEnvServerUrl);
            return new ClientRuntimeConfig(true,
                    DEV_SERVER_BASE_URI,
                    DEV_WEBSOCKET_BASE_URI,
                    DEV_SERVER_BASE_URI,
                    healthBaseUri,
                    MessagingTransportMode.WEBSOCKET);
        }

        URI prodServerBaseUri = parseRequiredAbsoluteUri(properties, KEY_SERVER_URL_PROD);
        ensureScheme(prodServerBaseUri, KEY_SERVER_URL_PROD, "https");

        URI prodWebSocketBaseUri = deriveProdWebSocketBaseUri(prodServerBaseUri);

        URI helpCenterBaseUri = parseOptionalAbsoluteUri(properties, KEY_HELP_CENTER_URL_PROD);
        URI resolvedHelpCenterBaseUri = helpCenterBaseUri == null ? prodServerBaseUri : helpCenterBaseUri;
        ensureScheme(resolvedHelpCenterBaseUri, KEY_HELP_CENTER_URL_PROD, "https");

        return new ClientRuntimeConfig(false,
                prodServerBaseUri,
                prodWebSocketBaseUri,
                resolvedHelpCenterBaseUri,
                prodServerBaseUri,
                MessagingTransportMode.HTTPS_POLLING);
    }

    /**
     * Returns whether the client is running in development mode.
     *
     * @return {@code true} when dev mode is enabled
     */
    public boolean isDev() {
        return dev;
    }

    /**
     * Returns the base HTTPS server URI used by REST API callers.
     *
     * @return server base URI
     */
    public URI serverBaseUri() {
        return serverBaseUri;
    }

    /**
     * Returns the base WSS URI used by WebSocket callers.
     *
     * @return websocket base URI
     */
    public URI webSocketBaseUri() {
        return webSocketBaseUri;
    }

    /**
     * Returns the help center base URI for external-link navigation.
     *
     * @return help center URI
     */
    public URI helpCenterBaseUri() {
        return helpCenterBaseUri;
    }

    /**
     * Returns the base HTTPS URI used for startup health checks.
     *
     * @return health-check base URI
     */
    public URI healthCheckBaseUri() {
        return healthCheckBaseUri;
    }

    /**
     * Returns the messaging transport mode selected for the current runtime.
     *
     * @return runtime messaging transport mode
     */
    public MessagingTransportMode messagingTransportMode() {
        return messagingTransportMode;
    }

    /**
     * Parses {@code app.isDev}. Missing/blank values default to development mode.
     *
     * @param rawValue raw property value
     * @return {@code true} when development mode is enabled
     */
    private static boolean parseIsDev(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return true;
        }
        String normalized = rawValue.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new ClientConfigurationException(
                "Invalid '" + KEY_APP_IS_DEV + "' value: '" + rawValue + "'. Expected true or false.");
    }

    /**
     * Resolves development-mode health-check base URI using configured precedence.
     *
     * @param properties         source properties
     * @param devSystemServerUrl optional system-property override
     * @param devEnvServerUrl    optional environment-variable override
     * @return resolved development health-check base URI
     */
    private static URI resolveDevHealthBaseUri(Properties properties, String devSystemServerUrl,
            String devEnvServerUrl) {
        URI fromProperties = parseOptionalAbsoluteUri(properties, KEY_SERVER_URL);
        if (fromProperties != null) {
            return fromProperties;
        }
        if (devSystemServerUrl != null && !devSystemServerUrl.isBlank()) {
            return parseAbsoluteUri("haf.server.url", devSystemServerUrl);
        }
        if (devEnvServerUrl != null && !devEnvServerUrl.isBlank()) {
            return parseAbsoluteUri("HAF_SERVER_URL", devEnvServerUrl);
        }
        return DEV_SERVER_BASE_URI;
    }

    /**
     * Derives a production websocket base URI from the production HTTPS server URI.
     *
     * This keeps compatibility for components that still require a websocket URI
     * object, even though production messaging receive mode uses HTTPS polling.
     *
     * For forwarded tunnel hosts such as {@code <id>-8443...}, this method swaps
     * the host segment to {@code <id>-8444...}. For explicit HTTPS port 8443, this
     * method maps it to WSS port 8444.
     *
     * @param prodServerBaseUri production HTTPS server URI
     * @return derived WSS websocket URI
     */
    private static URI deriveProdWebSocketBaseUri(URI prodServerBaseUri) {
        String host = prodServerBaseUri.getHost();
        if (host == null || host.isBlank()) {
            throw new ClientConfigurationException(
                    "Cannot derive production websocket URI from '" + KEY_SERVER_URL_PROD + "'.");
        }

        String derivedHost = host.replaceFirst("-8443\\.", "-8444.");
        int derivedPort = prodServerBaseUri.getPort();
        if (derivedPort == 8443) {
            derivedPort = 8444;
        }

        try {
            return new URI("wss",
                    prodServerBaseUri.getUserInfo(),
                    derivedHost,
                    derivedPort,
                    "/",
                    null,
                    null);
        } catch (Exception e) {
            throw new ClientConfigurationException(
                    "Cannot derive production websocket URI from '" + KEY_SERVER_URL_PROD + "'.",
                    e);
        }
    }

    /**
     * Parses a required absolute URI property.
     *
     * @param properties source properties
     * @param key        property key
     * @return parsed absolute URI
     */
    private static URI parseRequiredAbsoluteUri(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ClientConfigurationException("Missing required '" + key + "' in client.properties.");
        }
        return parseAbsoluteUri(key, value);
    }

    /**
     * Parses an optional absolute URI property.
     *
     * @param properties source properties
     * @param key        property key
     * @return parsed absolute URI, or {@code null} when blank/missing
     */
    private static URI parseOptionalAbsoluteUri(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseAbsoluteUri(key, value);
    }

    /**
     * Parses and validates an absolute URI string with required scheme and host.
     *
     * @param key      configuration key used in validation errors
     * @param rawValue raw URI value
     * @return parsed absolute URI
     */
    private static URI parseAbsoluteUri(String key, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("URI must include scheme and host");
            }
            return uri;
        } catch (Exception e) {
            throw new ClientConfigurationException(
                    "Invalid URI for '" + key + "': '" + rawValue + "'.", e);
        }
    }

    /**
     * Ensures that a URI uses the expected scheme.
     *
     * @param uri            URI to validate
     * @param key            configuration key used in validation errors
     * @param expectedScheme required scheme (for example, {@code https} or
     *                       {@code wss})
     */
    private static void ensureScheme(URI uri, String key, String expectedScheme) {
        if (!expectedScheme.equalsIgnoreCase(uri.getScheme())) {
            throw new ClientConfigurationException(
                    "Invalid URI scheme for '" + key + "': expected '" + expectedScheme + "', got '"
                            + uri.getScheme() + "'.");
        }
    }
}
