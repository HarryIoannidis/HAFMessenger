package com.haf.client.services;

import com.haf.client.viewmodels.MessagesViewModel.IncomingMessageListener;
import com.haf.client.viewmodels.MessagesViewModel.PresenceListener;
import java.util.concurrent.CompletableFuture;

/**
 * Application service for session-level Main screen operations.
 */
public interface MainSessionService {

    /**
     * Registers a presence listener against the active chat session, if available.
     *
     * @param listener the listener
     */
    void registerPresenceListener(PresenceListener listener);

    /**
     * Unregisters the previously registered presence listener, if any.
     */
    void unregisterPresenceListener();

    /**
     * Registers an incoming-message listener against the active chat session, if
     * available.
     *
     * @param listener the listener
     */
    void registerIncomingMessageListener(IncomingMessageListener listener);

    /**
     * Unregisters the previously registered incoming-message listener, if any.
     */
    void unregisterIncomingMessageListener();

    /**
     * Performs logout orchestration (best effort server revoke + local session
     * clear).
     *
     * @return future completed when logout orchestration finishes
     */
    CompletableFuture<Void> logout();
}
