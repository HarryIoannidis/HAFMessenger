package com.haf.client.core;

import com.haf.client.viewmodels.MessagesViewModel;

/**
 * Application-wide singleton that bridges the login flow to the chat screen.
 *
 * 
 * The {@code LoginController} (or equivalent) calls {@link #set} once a
 * live {@link MessagesViewModel} is ready. {@code ChatController} then reads it
 * via {@link #get} in its {@code initialize()} method.
 */
public final class ChatSession {

    private static MessagesViewModel instance;

    /**
     * Prevents instantiation of this static session holder.
     */
    private ChatSession() {
    }

    /**
     * Stores the active {@link MessagesViewModel} for the current session.
     *
     * @param vm the view-model to store; may be {@code null} to clear the session
     */
    public static void set(MessagesViewModel vm) {
        instance = vm;
    }

    /**
     * Returns the active {@link MessagesViewModel}, or {@code null} if the user
     * has not logged in yet.
     *
     * @return the current view-model, or {@code null}
     */
    public static MessagesViewModel get() {
        return instance;
    }

    /** Clears the stored session (e.g. on logout). */
    public static void clear() {
        instance = null;
    }
}
