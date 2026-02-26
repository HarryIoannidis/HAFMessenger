package com.haf.client.network;

/**
 * No-op {@link MessageReceiver} used while real network integration is pending.
 * Never delivers any incoming messages; {@link #start()} and {@link #stop()}
 * are silent no-ops.
 */
public class MockMessageReceiver implements MessageReceiver {

    @Override
    public void setMessageListener(MessageListener listener) {
        // No network — listener will never be called.
    }

    @Override
    public void start() {
        // Nothing to start.
    }

    @Override
    public void stop() {
        // Nothing to stop.
    }
}
