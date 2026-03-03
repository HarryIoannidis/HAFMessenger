package com.haf.client.network;

import com.haf.shared.keystore.KeyProvider;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.exceptions.KeyNotFoundException;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.FixedClockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import static org.junit.jupiter.api.Assertions.*;

class MessageSenderTest {

    static class MockKeyProvider implements KeyProvider {
        private final String senderId;
        private final PublicKey publicKey;

        MockKeyProvider(String senderId, PublicKey publicKey) {
            this.senderId = senderId;
            this.publicKey = publicKey;
        }

        @Override
        public PublicKey getRecipientPublicKey(String recipientId) throws KeyNotFoundException {
            if ("recipient-123".equals(recipientId)) {
                return publicKey;
            }
            throw new KeyNotFoundException("Recipient not found: " + recipientId);
        }

        @Override
        public String getSenderId() {
            return senderId;
        }
    }

    static class MockWebSocketAdapter extends WebSocketAdapter {
        private String lastSentMessage;
        private boolean connected = false;

        MockWebSocketAdapter() {
            super(URI.create("ws://localhost:8080"));
        }

        @Override
        public void connect(java.util.function.Consumer<String> onMessage,
                java.util.function.Consumer<Throwable> onError) throws IOException {
            connected = true;
        }

        @Override
        public void sendText(String message) throws IOException {
            if (!connected) {
                throw new IOException("Not connected");
            }
            lastSentMessage = message;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void close() {
            connected = false;
        }

        String getLastSentMessage() {
            return lastSentMessage;
        }
    }

    private KeyProvider keyProvider;
    private ClockProvider clockProvider;
    private MockWebSocketAdapter webSocketAdapter;
    private MessageSender messageSender;

    @BeforeEach
    void setup() throws Exception {
        KeyPair kp = com.haf.shared.utils.EccKeyIO.generate();
        keyProvider = new MockKeyProvider("sender-123", kp.getPublic());
        clockProvider = new FixedClockProvider(1000000L);
        webSocketAdapter = new MockWebSocketAdapter();
        messageSender = new DefaultMessageSender(keyProvider, clockProvider, webSocketAdapter);

        // Connect WebSocket
        webSocketAdapter.connect(msg -> {
        }, err -> {
        });
    }

    @Test
    void send_message_encrypts_and_sends() throws Exception {
        byte[] payload = "Hello, World!".getBytes(StandardCharsets.UTF_8);

        messageSender.sendMessage(payload, "recipient-123", "text/plain", 3600);

        assertNotNull(webSocketAdapter.getLastSentMessage());
        // Verify it's valid JSON
        com.haf.shared.utils.JsonCodec.fromJson(webSocketAdapter.getLastSentMessage(), EncryptedMessage.class);
    }

    @Test
    void send_message_throws_on_unknown_recipient() {
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);

        assertThrows(KeyNotFoundException.class, () -> {
            messageSender.sendMessage(payload, "unknown-recipient", "text/plain", 3600);
        });
    }

    @Test
    void send_message_validates_message() {
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);

        // This should work (validation happens after encryption)
        assertDoesNotThrow(() -> {
            messageSender.sendMessage(payload, "recipient-123", "text/plain", 3600);
        });
    }

    @Test
    void send_message_throws_on_null_payload() {
        assertThrows(IllegalArgumentException.class, () -> {
            messageSender.sendMessage(null, "recipient-123", "text/plain", 3600);
        });
    }

    @Test
    void send_message_throws_when_not_connected() throws Exception {
        webSocketAdapter.close();
        byte[] payload = "Hello".getBytes(StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> {
            messageSender.sendMessage(payload, "recipient-123", "text/plain", 3600);
        });
    }
}
