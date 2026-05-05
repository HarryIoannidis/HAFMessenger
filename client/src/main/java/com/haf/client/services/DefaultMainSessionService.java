package com.haf.client.services;

import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.client.core.AuthSessionState;
import com.haf.client.core.ChatSession;
import com.haf.client.core.CurrentUserSession;
import com.haf.client.core.NetworkSession;
import com.haf.client.viewmodels.MessagesViewModel;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link MainSessionService} implementation backed by session
 * singletons.
 */
public class DefaultMainSessionService implements MainSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMainSessionService.class);
    static final int LOGOUT_TIMEOUT_SECONDS = 5;

    interface NetworkGateway {
        /**
         * Revokes backend session token/state for the current user.
         *
         * @return completion future containing server response payload
         */
        CompletableFuture<String> revokeSession();

        /**
         * Closes network transport resources.
         */
        void close();
    }

    interface SessionChannel {
        /**
         * Registers presence listener on current chat session.
         *
         * @param listener listener to register
         */
        void addPresenceListener(MessagesViewModel.PresenceListener listener);

        /**
         * Unregisters presence listener from current chat session.
         *
         * @param listener listener to unregister
         */
        void removePresenceListener(MessagesViewModel.PresenceListener listener);

        /**
         * Registers incoming-message listener on current chat session.
         *
         * @param listener listener to register
         */
        void addIncomingMessageListener(MessagesViewModel.IncomingMessageListener listener);

        /**
         * Unregisters incoming-message listener from current chat session.
         *
         * @param listener listener to unregister
         */
        void removeIncomingMessageListener(MessagesViewModel.IncomingMessageListener listener);

        /**
         * Stops active message-receiving transport for the current chat session.
         */
        void stopReceiving();
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
         * @param task     task logic to execute
         */
        void run(String taskName, Runnable task);
    }

    private final SessionContext sessionContext;
    private final TaskRunner taskRunner;

    private SessionChannel activeSessionChannel;
    private MessagesViewModel.PresenceListener activePresenceListener;
    private MessagesViewModel.IncomingMessageListener activeIncomingMessageListener;

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
     * @param taskRunner     background execution strategy
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
    public synchronized void registerPresenceListener(MessagesViewModel.PresenceListener listener) {
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
    public synchronized void registerIncomingMessageListener(MessagesViewModel.IncomingMessageListener listener) {
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
                } catch (Exception | LinkageError ex) {
                    completion.completeExceptionally(wrapThrowable(ex));
                }
            });
        } catch (Exception | LinkageError ex) {
            completion.completeExceptionally(wrapThrowable(ex));
        }
        return completion;
    }

    /**
     * Executes logout sequence with best-effort remote revoke and guaranteed local
     * cleanup.
     */
    private void performLogout() {
        SessionChannel channel = resolveSessionChannelSafely();
        stopSessionChannelSafely(channel);

        NetworkGateway gateway = resolveNetworkGatewaySafely();
        if (gateway != null) {
            revokeSessionSafely(gateway);
            closeGatewaySafely(gateway);
        }

        unregisterPresenceListener();
        unregisterIncomingMessageListener();
        sessionContext.clearNetworkSession();
        sessionContext.clearChatSession();
        sessionContext.clearCurrentUserProfile();
        AuthSessionState.clear();
    }

    /**
     * Resolves active session channel while protecting logout flow from class
     * loading/linkage failures.
     *
     * @return active session channel, or {@code null} when unavailable
     */
    private SessionChannel resolveSessionChannelSafely() {
        try {
            return sessionContext.sessionChannel();
        } catch (Exception | LinkageError ex) {
            LOGGER.warn("Could not resolve session channel during logout; continuing with local cleanup", ex);
            return null;
        }
    }

    /**
     * Resolves active network gateway while protecting logout flow from class
     * loading/linkage failures.
     *
     * @return active network gateway, or {@code null} when unavailable
     */
    private NetworkGateway resolveNetworkGatewaySafely() {
        try {
            return sessionContext.networkGateway();
        } catch (Exception | LinkageError ex) {
            LOGGER.warn("Could not resolve network gateway during logout; continuing with local cleanup", ex);
            return null;
        }
    }

    /**
     * Stops message receiving on active channel with best-effort failure isolation.
     *
     * @param channel active channel, or {@code null}
     */
    private void stopSessionChannelSafely(SessionChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.stopReceiving();
        } catch (Exception | LinkageError ex) {
            LOGGER.warn("Error stopping message receiver on logout; continuing cleanup", ex);
        }
    }

    /**
     * Revokes remote session token with timeout and tolerant fallback.
     *
     * @param gateway active network gateway
     */
    private void revokeSessionSafely(NetworkGateway gateway) {
        try {
            gateway.revokeSession().get(LOGOUT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Logout API call interrupted; continuing with local logout", ex);
        } catch (TimeoutException ex) {
            LOGGER.info("Logout API call timed out; continuing with local logout");
        } catch (ExecutionException ex) {
            if (isExpectedInvalidSessionLogoutFailure(ex)) {
                LOGGER.info("Logout API call returned invalid-session after revoke/takeover; continuing local logout");
            } else {
                LOGGER.warn("Logout API call failed; continuing with local logout", ex);
            }
        } catch (Exception | LinkageError ex) {
            LOGGER.warn("Logout API call failed before completion; continuing with local logout", ex);
        }
    }

    /**
     * Closes active gateway with best-effort failure isolation.
     *
     * @param gateway active network gateway
     */
    private void closeGatewaySafely(NetworkGateway gateway) {
        try {
            gateway.close();
        } catch (Exception | LinkageError ex) {
            LOGGER.warn("Error closing network gateway on logout", ex);
        }
    }

    /**
     * Normalizes throwable values into runtime exceptions suitable for completion
     * futures.
     *
     * @param throwable throwable raised by background logout execution
     * @return runtime exception wrapping original throwable
     */
    private static RuntimeException wrapThrowable(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(throwable);
    }

    /**
     * Checks whether logout failure is an expected invalid-session outcome.
     *
     * This occurs when another device has already revoked this session and local
     * cleanup is still in progress.
     *
     * @param error logout failure to inspect
     * @return {@code true} when failure represents expected 401/403 invalid session
     */
    private static boolean isExpectedInvalidSessionLogoutFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpCommunicationException communicationException) {
                int statusCode = communicationException.getStatusCode();
                return statusCode == 401 || statusCode == 403;
            }
            current = current.getCause();
        }
        return false;
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
     * @param task     task to execute
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
                 * Closes active network adapter.
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
            MessagesViewModel messageViewModel = ChatSession.get();
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
                public void addPresenceListener(MessagesViewModel.PresenceListener listener) {
                    messageViewModel.addPresenceListener(listener);
                }

                /**
                 * Removes presence listener from message view-model.
                 *
                 * @param listener listener to remove
                 */
                @Override
                public void removePresenceListener(MessagesViewModel.PresenceListener listener) {
                    messageViewModel.removePresenceListener(listener);
                }

                /**
                 * Registers incoming-message listener on message view-model.
                 *
                 * @param listener listener to register
                 */
                @Override
                public void addIncomingMessageListener(MessagesViewModel.IncomingMessageListener listener) {
                    messageViewModel.addIncomingMessageListener(listener);
                }

                /**
                 * Removes incoming-message listener from message view-model.
                 *
                 * @param listener listener to remove
                 */
                @Override
                public void removeIncomingMessageListener(MessagesViewModel.IncomingMessageListener listener) {
                    messageViewModel.removeIncomingMessageListener(listener);
                }

                /**
                 * Stops message receiving on the active message view-model.
                 */
                @Override
                public void stopReceiving() {
                    messageViewModel.stopReceiving();
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
