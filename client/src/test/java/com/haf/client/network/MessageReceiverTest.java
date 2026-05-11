package com.haf.client.network;

import com.haf.client.crypto.UserKeystoreKeyProvider;
import com.haf.shared.crypto.MessageEncryptor;
import com.haf.shared.crypto.MessageSignatureService;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.keystore.UserKeystore;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.SigningKeyIO;
import com.haf.shared.websocket.RealtimeEvent;
import com.haf.shared.websocket.RealtimeEventType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageReceiverTest {

    static class RecordingRealtimeTransport implements RealtimeTransport {
        private Consumer<RealtimeEvent> eventListener = event -> {
        };
        private Consumer<Throwable> errorListener = error -> {
        };
        private final List<List<String>> deliveryReceipts = new ArrayList<>();
        private final List<List<String>> readReceipts = new ArrayList<>();
        private final List<String> typingEvents = new ArrayList<>();
        private int startCalls;
        private int closeCalls;

        @Override
        public void setEventListener(Consumer<RealtimeEvent> listener) {
            eventListener = listener == null ? event -> {
            } : listener;
        }

        @Override
        public void setErrorListener(Consumer<Throwable> listener) {
            errorListener = listener == null ? error -> {
            } : listener;
        }

        @Override
        public void start() {
            startCalls++;
        }

        @Override
        public void reconnect() {
            // Reconnect scheduling is outside these receiver unit tests.
        }

        @Override
        public MessageSender.SendResult sendMessage(EncryptedMessage encryptedMessage, String recipientKeyFingerprint) {
            return new MessageSender.SendResult("env-unused", 0L);
        }

        @Override
        public void sendDeliveryReceipt(List<String> envelopeIds, String recipientId) {
            deliveryReceipts.add(List.copyOf(envelopeIds));
        }

        @Override
        public void sendReadReceipt(List<String> envelopeIds, String recipientId) {
            readReceipts.add(List.copyOf(envelopeIds));
        }

        @Override
        public void sendTypingStart(String recipientId) {
            typingEvents.add("start:" + recipientId);
        }

        @Override
        public void sendTypingStop(String recipientId) {
            typingEvents.add("stop:" + recipientId);
        }

        @Override
        public void close() {
            closeCalls++;
        }

        void emit(RealtimeEvent event) {
            eventListener.accept(event);
        }

        void emitError(Throwable error) {
            errorListener.accept(error);
        }
    }

    Path tmpRoot;
    char[] passphrase = "test-pass".toCharArray();
    KeyPair senderKp;
    KeyPair recipientKp;
    String senderKeyId;
    String recipientKeyId;
    UserKeystore keyStore;
    UserKeystoreKeyProvider keyProvider;
    ClockProvider clockProvider;
    RecordingRealtimeTransport realtimeTransport;
    MessageReceiver messageReceiver;
    List<byte[]> receivedMessages;
    List<Throwable> receivedErrors;
    List<String> presenceUpdates;
    List<String> typingUpdates;

    @BeforeEach
    void setup() throws Exception {
        tmpRoot = Files.createTempDirectory("haf-test-receiver");
        FilePerms.ensureDir700(tmpRoot);

        keyStore = new UserKeystore(tmpRoot);
        senderKeyId = "key-sender-001";
        recipientKeyId = "key-recipient-001";
        senderKp = EccKeyIO.generate();
        recipientKp = EccKeyIO.generate();
        keyStore.saveKeypair(senderKeyId, senderKp, passphrase);
        keyStore.saveKeypair(recipientKeyId, recipientKp, passphrase);

        keyProvider = new UserKeystoreKeyProvider(tmpRoot, recipientKeyId, passphrase);
        clockProvider = new FixedClockProvider(1_000_000L);
        realtimeTransport = new RecordingRealtimeTransport();
        messageReceiver = new DefaultMessageReceiver(keyProvider, clockProvider, recipientKeyId, realtimeTransport);
        receivedMessages = new ArrayList<>();
        receivedErrors = new ArrayList<>();
        presenceUpdates = new ArrayList<>();
        typingUpdates = new ArrayList<>();

        messageReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType, long timestampEpochMs,
                    String envelopeId) {
                receivedMessages.add(plaintext);
            }

            @Override
            public void onError(Throwable error) {
                receivedErrors.add(error);
            }

            @Override
            public void onPresenceUpdate(String userId, boolean active) {
                presenceUpdates.add(userId + ":" + active);
            }

            @Override
            public void onTyping(String userId, boolean typing) {
                typingUpdates.add(userId + ":" + typing);
            }
        });
    }

    @AfterEach
    void cleanup() throws IOException {
        if (tmpRoot == null) {
            return;
        }
        try (var paths = Files.walk(tmpRoot)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup in tests.
                }
            });
        }
    }

    @Test
    void wss_new_message_decrypts_deduplicates_and_emits_receipts() throws Exception {
        byte[] payload = "hello wss".getBytes(StandardCharsets.UTF_8);
        EncryptedMessage encrypted = encryptedMessage(payload);
        RealtimeEvent event = RealtimeEvent.serverEvent(RealtimeEventType.NEW_MESSAGE);
        event.setEnvelopeId("env-1");
        event.setSenderId(senderKeyId);
        event.setRecipientId(recipientKeyId);
        event.setEncryptedMessage(encrypted);

        messageReceiver.start();
        realtimeTransport.emit(event);
        realtimeTransport.emit(event);

        assertEquals(1, realtimeTransport.startCalls);
        assertEquals(1, receivedMessages.size());
        assertArrayEquals(payload, receivedMessages.getFirst());
        assertEquals(List.of(List.of("env-1")), realtimeTransport.deliveryReceipts);

        messageReceiver.acknowledgeEnvelopes(senderKeyId);

        assertEquals(List.of(List.of("env-1")), realtimeTransport.readReceipts);
    }

    @Test
    void wss_presence_and_typing_events_are_forwarded() throws Exception {
        messageReceiver.start();
        RealtimeEvent presence = RealtimeEvent.serverEvent(RealtimeEventType.PRESENCE_UPDATE);
        presence.setSenderId("contact-1");
        presence.setActive(true);
        realtimeTransport.emit(presence);

        RealtimeEvent typing = RealtimeEvent.serverEvent(RealtimeEventType.TYPING_START);
        typing.setSenderId("contact-1");
        realtimeTransport.emit(typing);

        assertEquals(List.of("contact-1:true"), presenceUpdates);
        assertEquals(List.of("contact-1:true"), typingUpdates);
    }

    @Test
    void typing_methods_send_wss_events() {
        messageReceiver.sendTypingStart("recipient-1");
        messageReceiver.sendTypingStop("recipient-1");

        assertEquals(List.of("start:recipient-1", "stop:recipient-1"), realtimeTransport.typingEvents);
    }

    @Test
    void transport_error_is_forwarded_to_listener() throws Exception {
        IOException error = new IOException("connection closed");

        messageReceiver.start();
        realtimeTransport.emitError(error);

        assertEquals(List.of(error), receivedErrors);
    }

    @Test
    void stop_closes_realtime_transport() {
        messageReceiver.stop();

        assertEquals(1, realtimeTransport.closeCalls);
    }

    private EncryptedMessage encryptedMessage(byte[] payload) throws Exception {
        MessageEncryptor encryptor = new MessageEncryptor(
                recipientKp.getPublic(),
                senderKeyId,
                recipientKeyId,
                clockProvider);
        EncryptedMessage encrypted = encryptor.encrypt(payload, "text/plain", 3600);
        String senderSigningFingerprint = FingerprintUtil.sha256Hex(
                SigningKeyIO.publicDer(keyStore.loadSigningPublicKeyByKeyId(senderKeyId)));
        MessageSignatureService.sign(
                encrypted,
                keyStore.loadSigningPrivate(senderKeyId, passphrase),
                senderSigningFingerprint);
        return encrypted;
    }
}
