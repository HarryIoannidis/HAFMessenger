package com.haf.client.core;

import com.haf.client.network.WebSocketAdapter;

/**
 * Stores the authenticated {@link WebSocketAdapter} so that controllers other
 * than {@code LoginController} can make HTTP calls against the server.
 *
 * Set once at login time via {@link #set(WebSocketAdapter)} and cleared on
 * logout / disconnect.
 */
public final class NetworkSession {

    private static WebSocketAdapter instance;

    /**
     * Prevents instantiation of this static session holder.
     */
    private NetworkSession() {
    }

    /**
     * Stores the adapter for the current session.
     *
     * @param adapter authenticated adapter to store for the current session
     */
    public static void set(WebSocketAdapter adapter) {
        instance = adapter;
    }

    /**
     * Returns the current adapter, or {@code null} if not yet logged in.
     *
     * @return current session adapter, or {@code null} when no session is active
     */
    public static WebSocketAdapter get() {
        return instance;
    }

    /**
     * Clears the stored adapter (e.g. on logout).
     */
    public static void clear() {
        instance = null;
    }
}
