package com.haf.server.ingress;

import org.java_websocket.WebSocket;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory presence registry keyed by user ID.
 *
 * Presence is connectivity-based: a user is active while they have at least one
 * live WebSocket connection registered.
 */
public final class PresenceRegistry {

    private final ConcurrentHashMap<String, Set<WebSocket>> connectionsByUser = new ConcurrentHashMap<>();
    private final Set<String> hiddenPresenceUsers = ConcurrentHashMap.newKeySet();

    /**
     * Registers a connection for a user.
     *
     * @param userId     the user ID
     * @param connection the websocket connection
     * @return true when the user transitioned from inactive to active
     */
    public boolean registerConnection(String userId, WebSocket connection) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(connection, "connection");

        Set<WebSocket> userConnections = connectionsByUser.computeIfAbsent(userId,
                ignored -> ConcurrentHashMap.newKeySet());
        userConnections.add(connection);
        return userConnections.size() == 1;
    }

    /**
     * Unregisters a connection for a user.
     *
     * @param userId     the user ID
     * @param connection the websocket connection
     * @return true when the user transitioned from active to inactive
     */
    public boolean unregisterConnection(String userId, WebSocket connection) {
        if (userId == null || connection == null) {
            return false;
        }

        Set<WebSocket> userConnections = connectionsByUser.get(userId);
        if (userConnections == null) {
            return false;
        }

        userConnections.remove(connection);
        if (userConnections.isEmpty()) {
            connectionsByUser.remove(userId, userConnections);
            return true;
        }

        return false;
    }

    /**
     * Returns whether a user is currently active.
     *
     * @param userId the user ID
     * @return true if the user has at least one live connection
     */
    public boolean isActive(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        Set<WebSocket> connections = connectionsByUser.get(userId);
        return connections != null && !connections.isEmpty();
    }

    /**
     * Updates whether a user's presence should be hidden from other users.
     *
     * @param userId user id whose visibility setting is updated
     * @param hidden {@code true} to hide presence visibility
     */
    public void setPresenceHidden(String userId, boolean hidden) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        if (hidden) {
            hiddenPresenceUsers.add(userId);
            return;
        }
        hiddenPresenceUsers.remove(userId);
    }

    /**
     * Returns whether a user's presence is configured as hidden.
     *
     * @param userId user id to evaluate
     * @return {@code true} when presence is hidden
     */
    public boolean isPresenceHidden(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return hiddenPresenceUsers.contains(userId);
    }

    /**
     * Resolves activity visibility that should be exposed to other users.
     *
     * @param userId user id to evaluate
     * @return {@code true} when user is active and not hiding presence
     */
    public boolean isVisibleActive(String userId) {
        return isActive(userId) && !isPresenceHidden(userId);
    }

    /**
     * Clears hidden-presence state for a user.
     *
     * @param userId user id to clear
     */
    public void clearPresenceHidden(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        hiddenPresenceUsers.remove(userId);
    }

    /**
     * Returns all active websocket connections for a user.
     *
     * @param userId the user ID
     * @return immutable snapshot of active connections
     */
    public Set<WebSocket> getActiveConnections(String userId) {
        if (userId == null || userId.isBlank()) {
            return Set.of();
        }
        Set<WebSocket> connections = connectionsByUser.get(userId);
        if (connections == null || connections.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(connections);
    }
}
