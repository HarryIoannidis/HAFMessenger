package com.haf.client.services;

import com.haf.client.viewmodels.MessageViewModel;

import java.util.concurrent.CompletableFuture;

/**
 * Application service for session-level Main screen operations.
 */
public interface MainSessionService {

    /**
     * Registers a presence listener against the active chat session, if available.
     */
    void registerPresenceListener(MessageViewModel.PresenceListener listener);

    /**
     * Unregisters the previously registered presence listener, if any.
     */
    void unregisterPresenceListener();

    /**
     * Performs logout orchestration (best effort server revoke + local session clear).
     *
     * @return future completed when logout orchestration finishes
     */
    CompletableFuture<Void> logout();
}
