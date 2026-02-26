package com.haf.client.core;

import com.haf.client.viewmodels.MessageViewModel;

/**
 * Application-wide singleton that bridges the login flow to the chat screen.
 *
 * 
 * The {@code LoginController} (or equivalent) calls {@link #set} once a
 * live {@link MessageViewModel} is ready. {@code ChatController} then reads it
 * via {@link #get} in its {@code initialize()} method.
 */
public final class ChatSession {

    private static MessageViewModel instance;

    private ChatSession() {
    }

    /**
     * Stores the active {@link MessageViewModel} for the current session.
     *
     * @param vm the view-model to store; may be {@code null} to clear the session
     */
    public static void set(MessageViewModel vm) {
        instance = vm;
    }

    /**
     * Returns the active {@link MessageViewModel}, or {@code null} if the user
     * has not logged in yet.
     *
     * @return the current view-model, or {@code null}
     */
    public static MessageViewModel get() {
        return instance;
    }

    /** Clears the stored session (e.g. on logout). */
    public static void clear() {
        instance = null;
    }
}
