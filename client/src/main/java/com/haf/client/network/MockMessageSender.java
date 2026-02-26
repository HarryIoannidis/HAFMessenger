package com.haf.client.network;

/**
 * No-op {@link MessageSender} used while real network integration is pending.
 * Swallow every send call silently so the UI can be exercised before the
 * server is wired up.
 */
public class MockMessageSender implements MessageSender {

    @Override
    public void sendMessage(byte[] payload, String recipientId,
            String contentType, long ttlSeconds) {
        // Intentionally empty — message is already added locally by the ViewModel.
    }
}
