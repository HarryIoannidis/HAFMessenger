package com.haf.client.network;

import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.websocket.RealtimeEvent;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Client-side contract for the WSS realtime transport.
 */
public interface RealtimeTransport extends AutoCloseable {

    void setEventListener(Consumer<RealtimeEvent> listener);

    void setErrorListener(Consumer<Throwable> listener);

    void start() throws IOException;

    void reconnect() throws IOException;

    /**
     * Returns whether the realtime socket is currently active and usable.
     *
     * @return {@code true} when the transport is connected
     */
    default boolean isConnected() {
        return false;
    }

    MessageSender.SendResult sendMessage(EncryptedMessage encryptedMessage, String recipientKeyFingerprint)
            throws IOException;

    void sendDeliveryReceipt(List<String> envelopeIds, String recipientId) throws IOException;

    void sendReadReceipt(List<String> envelopeIds, String recipientId) throws IOException;

    void sendTypingStart(String recipientId) throws IOException;

    void sendTypingStop(String recipientId) throws IOException;

    @Override
    void close();
}
