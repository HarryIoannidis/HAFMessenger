package com.haf.client.services;

import com.haf.client.core.ChatSession;
import com.haf.client.core.CurrentUserSession;
import com.haf.client.core.NetworkSession;
import com.haf.client.viewmodels.MessageViewModel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default {@link MainSessionService} implementation backed by session singletons.
 */
public class DefaultMainSessionService implements MainSessionService {

    private static final Logger LOGGER = Logger.getLogger(DefaultMainSessionService.class.getName());
    static final int LOGOUT_TIMEOUT_SECONDS = 3;

    interface NetworkGateway {
        /**
         * Revokes backend session token/state for the current user.
         *
         * @return completion future containing server response payload
         */
        CompletableFuture<String> revokeSession();

        /**
         * Closes network transport resources.
         *
         * @throws Exception when close operation fails
         */
        void close() throws Exception;
    }

    interface SessionChannel {
        /**
         * Registers presence listener on current chat session.
         *
         * @param listener listener to register
         */
        void addPresenceListener(MessageViewModel.PresenceListener listener);

        /**
         * Unregisters presence listener from current chat session.
         *
         * @param listener listener to unregister
         */
        void removePresenceListener(MessageViewModel.PresenceListener listener);

        /**
         * Registers incoming-message listener on current chat session.
         *
         * @param listener listener to register
         */
        void addIncomingMessageListener(MessageViewModel.IncomingMessageListener listener);

        /**
         * Unregisters incoming-message listener from current chat session.
         *
         * @param listener listener to unregister
         */
        void removeIncomingMessageListener(MessageViewModel.IncomingMessageListener listener);
    }

    interface SessionContext {
        /**
         * Resolves the network gateway for current session state.
         *
         * @return network gateway, or {@code null} when not connected
         */
        NetworkGateway networkGateway();

        /**
         * Resolves message session channel for listener registration.
         *
         * @return session channel, or {@code null} when chat session is unavailable
         */
        SessionChannel sessionChannel();

        /**
         * Clears globally stored network session state.
         */
        void clearNetworkSession();

        /**
         * Clears globally stored chat session state.
         */
        void clearChatSession();

        /**
         * Clears globally stored current-user profile state.
         */
        void clearCurrentUserProfile();
    }

    @FunctionalInterface
    interface TaskRunner {
        /**
         * Runs a named background task.
         *
         * @param taskName logical task name for diagnostics/thread naming
         * @param task task logic to execute
         */
        void run(String taskName, Runnable task);
    }

    private final SessionContext sessionContext;
    private final TaskRunner taskRunner;

    private SessionChannel activeSessionChannel;
    private MessageViewModel.PresenceListener activePresenceListener;
    private MessageViewModel.IncomingMessageListener activeIncomingMessageListener;

    /**
     * Creates main-session service backed by default singleton session context.
     */
    public DefaultMainSessionService() {
        this(new DefaultSessionContext(), DefaultMainSessionService::runVirtualTask);
    }

    /**
     * Creates main-session service with injectable context/task runner.
     *
     * @param sessionContext abstraction over session singletons
     * @param taskRunner background execution strategy
     */
    DefaultMainSessionService(SessionContext sessionContext, TaskRunner taskRunner) {
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext");
        this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
    }

    /**
     * Registers a presence listener and replaces any previously registered one.
     *
     * @param listener listener to register
     */
    @Override
    public synchronized void registerPresenceListener(MessageViewModel.PresenceListener listener) {
        Objects.requireNonNull(listener, "listener");

        unregisterPresenceListener();
        SessionChannel channel = sessionContext.sessionChannel();
        if (channel == null) {
            return;
        }

        channel.addPresenceListener(listener);
        activeSessionChannel = channel;
        activePresenceListener = listener;
    }

    /**
     * Unregisters currently active presence listener, if any.
     */
    @Override
    public synchronized void unregisterPresenceListener() {
        if (activeSessionChannel != null && activePresenceListener != null) {
            activeSessionChannel.removePresenceListener(activePresenceListener);
        }
        activePresenceListener = null;
        clearChannelIfUnused();
    }

    /**
     * Registers an incoming-message listener and replaces any previously registered
     * one.
     *
     * @param listener listener to register
     */
    @Override
    public synchronized void registerIncomingMessageListener(MessageViewModel.IncomingMessageListener listener) {
        Objects.requireNonNull(listener, "listener");

        unregisterIncomingMessageListener();
        SessionChannel channel = sessionContext.sessionChannel();
        if (channel == null) {
            return;
        }

        channel.addIncomingMessageListener(listener);
        activeSessionChannel = channel;
        activeIncomingMessageListener = listener;
    }

    /**
     * Unregisters currently active incoming-message listener, if any.
     */
    @Override
    public synchronized void unregisterIncomingMessageListener() {
        if (activeSessionChannel != null && activeIncomingMessageListener != null) {
            activeSessionChannel.removeIncomingMessageListener(activeIncomingMessageListener);
        }
        activeIncomingMessageListener = null;
        clearChannelIfUnused();
    }

    /**
     * Performs full logout flow asynchronously: revoke backend session, close
     * transport, unregister listeners, and clear local session state.
     *
     * @return completion future that resolves when logout flow finishes
     */
    @Override
    public CompletableFuture<Void> logout() {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            taskRunner.run("logout-thread", () -> {
                try {
                    performLogout();
                    completion.complete(null);
                } catch (Throwable throwable) {
                    completion.completeExceptionally(throwable);
                }
            });
        } catch (Throwable throwable) {
            completion.completeExceptionally(throwable);
        }
        return completion;
    }

    /**
     * Executes logout sequence with best-effort remote revoke and guaranteed local
     * cleanup.
     */
    private void performLogout() {
        NetworkGateway gateway = sessionContext.networkGateway();
        if (gateway != null) {
            try {
                gateway.revokeSession().get(LOGOUT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Logout API call failed; continuing with local logout", ex);
            }

            try {
                gateway.close();
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error closing WebSocket on logout", ex);
            }
        }

        unregisterPresenceListener();
        unregisterIncomingMessageListener();
        sessionContext.clearNetworkSession();
        sessionContext.clearChatSession();
        sessionContext.clearCurrentUserProfile();
    }

    /**
     * Clears cached session channel when no listeners remain attached.
     */
    private void clearChannelIfUnused() {
        if (activePresenceListener == null && activeIncomingMessageListener == null) {
            activeSessionChannel = null;
        }
    }

    /**
     * Runs a named task on a virtual thread.
     *
     * @param taskName logical task name
     * @param task task to execute
     */
    private static void runVirtualTask(String taskName, Runnable task) {
        Thread.ofVirtual().name(taskName).start(task);
    }

    private static final class DefaultSessionContext implements SessionContext {

        /**
         * Creates a network gateway wrapper over the active {@link NetworkSession}.
         *
         * @return network gateway, or {@code null} when no network session is active
         */
        @Override
        public NetworkGateway networkGateway() {
            var adapter = NetworkSession.get();
            if (adapter == null) {
                return null;
            }

            return new NetworkGateway() {
                /**
                 * Calls logout endpoint on behalf of the active session.
                 *
                 * @return future carrying logout response body
                 */
                @Override
                public CompletableFuture<String> revokeSession() {
                    return adapter.postAuthenticated("/api/v1/logout", "{}");
                }

                /**
                 * Closes active websocket/network adapter.
                 */
                @Override
                public void close() {
                    adapter.close();
                }
            };
        }

        /**
         * Creates a session channel wrapper over the active chat message view-model.
         *
         * @return session channel, or {@code null} when chat session is inactive
         */
        @Override
        public SessionChannel sessionChannel() {
            MessageViewModel messageViewModel = ChatSession.get();
            if (messageViewModel == null) {
                return null;
            }

            return new SessionChannel() {
                /**
                 * Registers presence listener on message view-model.
                 *
                 * @param listener listener to register
                 */
                @Override
                public void addPresenceListener(MessageViewModel.PresenceListener listener) {
                    messageViewModel.addPresenceListener(listener);
                }

                /**
                 * Removes presence listener from message view-model.
                 *
                 * @param listener listener to remove
                 */
                @Override
                public void removePresenceListener(MessageViewModel.PresenceListener listener) {
                    messageViewModel.removePresenceListener(listener);
                }

                /**
                 * Registers incoming-message listener on message view-model.
                 *
                 * @param listener listener to register
                 */
                @Override
                public void addIncomingMessageListener(MessageViewModel.IncomingMessageListener listener) {
                    messageViewModel.addIncomingMessageListener(listener);
                }

                /**
                 * Removes incoming-message listener from message view-model.
                 *
                 * @param listener listener to remove
                 */
                @Override
                public void removeIncomingMessageListener(MessageViewModel.IncomingMessageListener listener) {
                    messageViewModel.removeIncomingMessageListener(listener);
                }
            };
        }

        /**
         * Clears globally stored network session singleton.
         */
        @Override
        public void clearNetworkSession() {
            NetworkSession.clear();
        }

        /**
         * Clears globally stored chat session singleton.
         */
        @Override
        public void clearChatSession() {
            ChatSession.clear();
        }

        /**
         * Clears globally stored current-user profile singleton.
         */
        @Override
        public void clearCurrentUserProfile() {
            CurrentUserSession.clear();
        }
    }
}
