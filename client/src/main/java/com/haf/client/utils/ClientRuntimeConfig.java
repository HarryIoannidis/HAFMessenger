package com.haf.client.utils;

import com.haf.client.exceptions.ClientConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;
import java.util.Properties;

/**
 * Resolves production client runtime endpoints from {@code client.properties}.
 */
public final class ClientRuntimeConfig {

    private static final String KEY_SERVER_URL_PROD = "server.url.prod";
    private static final String KEY_HELP_CENTER_URL_PROD = "help.center.url.prod";

    private final URI serverBaseUri;
    private final URI helpCenterBaseUri;
    private final URI healthCheckBaseUri;

    private ClientRuntimeConfig(URI serverBaseUri, URI helpCenterBaseUri, URI healthCheckBaseUri) {
        this.serverBaseUri = Objects.requireNonNull(serverBaseUri, "serverBaseUri");
        this.helpCenterBaseUri = Objects.requireNonNull(helpCenterBaseUri, "helpCenterBaseUri");
        this.healthCheckBaseUri = Objects.requireNonNull(healthCheckBaseUri, "healthCheckBaseUri");
    }

    /**
     * Loads runtime endpoint configuration from {@code /config/client.properties}.
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

        return fromProperties(properties);
    }

    /**
     * Builds runtime configuration from already loaded properties.
     *
     * @param properties source properties
     * @return resolved runtime configuration
     */
    public static ClientRuntimeConfig fromProperties(Properties properties) {
        Objects.requireNonNull(properties, "properties");

        URI serverBaseUri = parseRequiredAbsoluteUri(properties, KEY_SERVER_URL_PROD);
        ensureScheme(serverBaseUri, KEY_SERVER_URL_PROD, "https");

        URI helpCenterBaseUri = parseOptionalAbsoluteUri(properties, KEY_HELP_CENTER_URL_PROD);
        URI resolvedHelpCenterBaseUri = helpCenterBaseUri == null ? serverBaseUri : helpCenterBaseUri;
        ensureScheme(resolvedHelpCenterBaseUri, KEY_HELP_CENTER_URL_PROD, "https");

        return new ClientRuntimeConfig(
                serverBaseUri,
                resolvedHelpCenterBaseUri,
                serverBaseUri);
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

    private static URI parseRequiredAbsoluteUri(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ClientConfigurationException("Missing required '" + key + "' in client.properties.");
        }
        return parseAbsoluteUri(key, value);
    }

    private static URI parseOptionalAbsoluteUri(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseAbsoluteUri(key, value);
    }

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
                    "Invalid URI for '" + key + "': '" + rawValue + "'.",
                    e);
        }
    }

    private static void ensureScheme(URI uri, String key, String expectedScheme) {
        if (!expectedScheme.equalsIgnoreCase(uri.getScheme())) {
            throw new ClientConfigurationException(
                    "Invalid URI scheme for '" + key + "': expected '" + expectedScheme + "', got '"
                            + uri.getScheme() + "'.");
        }
    }
}
