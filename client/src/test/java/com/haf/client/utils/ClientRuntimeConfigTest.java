package com.haf.client.utils;

import com.haf.client.exceptions.ClientConfigurationException;
import java.net.URI;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRuntimeConfigTest {

    @Test
    void from_properties_requires_server_url_prod() {
        Properties properties = new Properties();

        ClientConfigurationException error = assertThrows(
                ClientConfigurationException.class,
                () -> ClientRuntimeConfig.fromProperties(properties));

        assertTrue(error.getMessage().contains("server.url.prod"));
    }

    @Test
    void from_properties_requires_wss_realtime_uri() {
        Properties properties = new Properties();
        properties.setProperty("server.url.prod", "https://prod.example.test");

        ClientConfigurationException error = assertThrows(
                ClientConfigurationException.class,
                () -> ClientRuntimeConfig.fromProperties(properties));

        assertTrue(error.getMessage().contains("server.ws.url.prod"));
    }

    @Test
    void from_properties_uses_prod_server_for_server_help_and_health_by_default() {
        Properties properties = new Properties();
        properties.setProperty("server.url.prod", "https://prod.example.test");
        properties.setProperty("server.ws.url.prod", "wss://realtime.example.test:9443/ws/v1/realtime");

        ClientRuntimeConfig config = ClientRuntimeConfig.fromProperties(properties);

        assertEquals(URI.create("https://prod.example.test"), config.serverBaseUri());
        assertEquals(URI.create("wss://realtime.example.test:9443/ws/v1/realtime"), config.realtimeWebSocketUri());
        assertEquals(URI.create("https://prod.example.test"), config.helpCenterBaseUri());
        assertEquals(URI.create("https://prod.example.test"), config.healthCheckBaseUri());
    }

    @Test
    void from_properties_uses_explicit_wss_realtime_uri_when_present() {
        Properties properties = new Properties();
        properties.setProperty("server.url.prod", "https://prod.example.test");
        properties.setProperty("server.ws.url.prod", "wss://realtime.example.test:9443/ws/v1/realtime");

        ClientRuntimeConfig config = ClientRuntimeConfig.fromProperties(properties);

        assertEquals(URI.create("wss://realtime.example.test:9443/ws/v1/realtime"),
                config.realtimeWebSocketUri());
    }

    @Test
    void from_properties_uses_help_center_when_present() {
        Properties properties = new Properties();
        properties.setProperty("server.url.prod", "https://prod.example.test");
        properties.setProperty("server.ws.url.prod", "wss://realtime.example.test:9443/ws/v1/realtime");
        properties.setProperty("help.center.url.prod", "https://help.example.test");

        ClientRuntimeConfig config = ClientRuntimeConfig.fromProperties(properties);

        assertEquals(URI.create("https://prod.example.test"), config.serverBaseUri());
        assertEquals(URI.create("https://help.example.test"), config.helpCenterBaseUri());
    }

    @Test
    void from_properties_rejects_non_https_server_uri() {
        Properties properties = new Properties();
        properties.setProperty("server.url.prod", "http://prod.example.test");
        properties.setProperty("server.ws.url.prod", "wss://realtime.example.test:9443/ws/v1/realtime");

        ClientConfigurationException error = assertThrows(
                ClientConfigurationException.class,
                () -> ClientRuntimeConfig.fromProperties(properties));

        assertTrue(error.getMessage().contains("server.url.prod"));
    }

    @Test
    void from_properties_rejects_invalid_help_center_uri() {
        Properties properties = new Properties();
        properties.setProperty("server.url.prod", "https://prod.example.test");
        properties.setProperty("server.ws.url.prod", "wss://realtime.example.test:9443/ws/v1/realtime");
        properties.setProperty("help.center.url.prod", "not-a-uri");

        ClientConfigurationException error = assertThrows(
                ClientConfigurationException.class,
                () -> ClientRuntimeConfig.fromProperties(properties));

        assertTrue(error.getMessage().contains("help.center.url.prod"));
    }

    @Test
    void from_properties_rejects_non_wss_realtime_uri() {
        Properties properties = new Properties();
        properties.setProperty("server.url.prod", "https://prod.example.test");
        properties.setProperty("server.ws.url.prod", "https://prod.example.test/ws/v1/realtime");

        ClientConfigurationException error = assertThrows(
                ClientConfigurationException.class,
                () -> ClientRuntimeConfig.fromProperties(properties));

        assertTrue(error.getMessage().contains("server.ws.url.prod"));
    }
}
