package com.haf.client.utils;

import com.haf.client.exceptions.ClientConfigurationException;
import java.net.URI;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRuntimeConfigTest {

    @Test
    void from_properties_defaults_to_dev_mode_when_app_is_dev_is_missing() {
        ClientRuntimeConfig config = ClientRuntimeConfig.fromProperties(new Properties());

        assertTrue(config.isDev());
        assertEquals(URI.create("https://localhost:8443"), config.serverBaseUri());
        assertEquals(URI.create("wss://localhost:8444/"), config.webSocketBaseUri());
        assertEquals(URI.create("https://localhost:8443"), config.helpCenterBaseUri());
        assertEquals(URI.create("https://localhost:8443"), config.healthCheckBaseUri());
        assertEquals(ClientRuntimeConfig.MessagingTransportMode.WEBSOCKET, config.messagingTransportMode());
    }

    @Test
    void from_properties_uses_prod_endpoints_when_app_is_dev_is_false() {
        Properties properties = new Properties();
        properties.setProperty("app.isDev", "false");
        properties.setProperty("server.url.prod", "https://prod.example.test");

        ClientRuntimeConfig config = ClientRuntimeConfig.fromProperties(properties);

        org.junit.jupiter.api.Assertions.assertFalse(config.isDev());
        assertEquals(URI.create("https://prod.example.test"), config.serverBaseUri());
        assertEquals(URI.create("wss://prod.example.test/"), config.webSocketBaseUri());
        assertEquals(URI.create("https://prod.example.test"), config.helpCenterBaseUri());
        assertEquals(URI.create("https://prod.example.test"), config.healthCheckBaseUri());
        assertEquals(ClientRuntimeConfig.MessagingTransportMode.HTTPS_POLLING, config.messagingTransportMode());
    }

    @Test
    void from_properties_derives_prod_websocket_when_set_to_auto() {
        Properties properties = new Properties();
        properties.setProperty("app.isDev", "false");
        properties.setProperty("server.url.prod", "https://6l0qr0nv-8443.euw.devtunnels.ms/");

        ClientRuntimeConfig config = ClientRuntimeConfig.fromProperties(properties);

        assertEquals(URI.create("wss://6l0qr0nv-8444.euw.devtunnels.ms/"), config.webSocketBaseUri());
    }

    @Test
    void from_properties_derives_prod_websocket_when_ws_property_is_missing() {
        Properties properties = new Properties();
        properties.setProperty("app.isDev", "false");
        properties.setProperty("server.url.prod", "https://prod.example.test:8443");

        ClientRuntimeConfig config = ClientRuntimeConfig.fromProperties(properties);

        assertEquals(URI.create("wss://prod.example.test:8444/"), config.webSocketBaseUri());
    }

    @Test
    void from_properties_requires_prod_urls_when_app_is_dev_is_false() {
        Properties properties = new Properties();
        properties.setProperty("app.isDev", "false");

        ClientConfigurationException error = org.junit.jupiter.api.Assertions.assertThrows(
                ClientConfigurationException.class,
                () -> ClientRuntimeConfig.fromProperties(properties));
        assertTrue(error.getMessage().contains("server.url.prod"));
    }

    @Test
    void from_properties_rejects_invalid_prod_uri_with_clear_error() {
        Properties properties = new Properties();
        properties.setProperty("app.isDev", "false");
        properties.setProperty("server.url.prod", "not-a-uri");

        ClientConfigurationException error = org.junit.jupiter.api.Assertions.assertThrows(
                ClientConfigurationException.class,
                () -> ClientRuntimeConfig.fromProperties(properties));
        assertTrue(error.getMessage().contains("server.url.prod"));
    }

    @Test
    void dev_mode_health_check_uses_legacy_server_url_fallback_order() {
        Properties properties = new Properties();
        properties.setProperty("app.isDev", "true");
        properties.setProperty("server.url", "https://dev-from-properties.example.test");

        ClientRuntimeConfig config = ClientRuntimeConfig.fromProperties(
                properties,
                "https://dev-from-sys.example.test",
                "https://dev-from-env.example.test");

        assertEquals(URI.create("https://dev-from-properties.example.test"), config.healthCheckBaseUri());
    }

    @Test
    void prod_mode_ignores_explicit_ws_property_and_derives_from_server_url() {
        Properties properties = new Properties();
        properties.setProperty("app.isDev", "false");
        properties.setProperty("server.url.prod", "https://prod.example.test:8443");
        properties.setProperty("server.ws.url.prod", "wss://ignored.example.test/");

        ClientRuntimeConfig config = ClientRuntimeConfig.fromProperties(properties);

        assertEquals(URI.create("wss://prod.example.test:8444/"), config.webSocketBaseUri());
    }
}
