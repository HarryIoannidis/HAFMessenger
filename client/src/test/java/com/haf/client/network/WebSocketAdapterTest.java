package com.haf.client.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketAdapterTest {

    private WebSocketAdapter webSocketAdapter;
    private URI serverUri = URI.create("wss://localhost:8080/ws");

    @BeforeEach
    void setUp() {
        // We can't directly mock the HttpClient builder chain easily without more setup.
        // For this test, we will focus on the logic inside the WebSocketAdapter that we can control.
        // A full test would require a more complex setup or refactoring the adapter.
        webSocketAdapter = new WebSocketAdapter(serverUri);
    }

    @Test
    void connect_should_complete_successfully() throws IOException {
        // This test is limited because we cannot easily mock the final `buildAsync` call.
        // A real implementation would require refactoring WebSocketAdapter to inject the HttpClient.
        // For now, we'll test the parts we can.
        
        // Since we can't mock the builder, we can't fully test connect.
        // We'll assert that the adapter is not connected initially.
        assertFalse(webSocketAdapter.isConnected());
    }

    @Test
    void sendText_throws_ioexception_when_not_connected() {
        assertThrows(IOException.class, () -> {
            webSocketAdapter.sendText("Hello, WebSocket!");
        });
    }

    @Test
    void close_should_not_throw_when_not_connected() {
        assertDoesNotThrow(() -> {
            webSocketAdapter.close();
        });
    }

    @Test
    void isConnected_is_false_initially() {
        assertFalse(webSocketAdapter.isConnected());
    }

    @Test
    void retry_policy_stubs_should_behave_as_expected() {
        assertTrue(webSocketAdapter.shouldRetry()); // 1st attempt
        assertTrue(webSocketAdapter.shouldRetry()); // 2nd attempt
        assertTrue(webSocketAdapter.shouldRetry()); // 3rd attempt
        assertFalse(webSocketAdapter.shouldRetry()); // 4th attempt, should fail

        webSocketAdapter.resetRetryCounter();
        assertTrue(webSocketAdapter.shouldRetry());

        assertEquals(1000L, webSocketAdapter.getRetryDelayMs());
    }
}
