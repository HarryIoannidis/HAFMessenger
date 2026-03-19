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
        CompletableFuture<String> revokeSession();

        void close() throws Exception;
    }

    interface SessionChannel {
        void addPresenceListener(MessageViewModel.PresenceListener listener);

        void removePresenceListener(MessageViewModel.PresenceListener listener);

        void addIncomingMessageListener(MessageViewModel.IncomingMessageListener listener);

        void removeIncomingMessageListener(MessageViewModel.IncomingMessageListener listener);
    }

    interface SessionContext {
        NetworkGateway networkGateway();

        SessionChannel sessionChannel();

        void clearNetworkSession();

        void clearChatSession();

        void clearCurrentUserProfile();
    }

    @FunctionalInterface
    interface TaskRunner {
        void run(String taskName, Runnable task);
    }

    private final SessionContext sessionContext;
    private final TaskRunner taskRunner;

    private SessionChannel activeSessionChannel;
    private MessageViewModel.PresenceListener activePresenceListener;
    private MessageViewModel.IncomingMessageListener activeIncomingMessageListener;

    public DefaultMainSessionService() {
        this(new DefaultSessionContext(), DefaultMainSessionService::runVirtualTask);
    }

    DefaultMainSessionService(SessionContext sessionContext, TaskRunner taskRunner) {
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext");
        this.taskRunner = Objects.requireNonNull(taskRunner, "taskRunner");
    }

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

    @Override
    public synchronized void unregisterPresenceListener() {
        if (activeSessionChannel != null && activePresenceListener != null) {
            activeSessionChannel.removePresenceListener(activePresenceListener);
        }
        activePresenceListener = null;
        clearChannelIfUnused();
    }

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

    @Override
    public synchronized void unregisterIncomingMessageListener() {
        if (activeSessionChannel != null && activeIncomingMessageListener != null) {
            activeSessionChannel.removeIncomingMessageListener(activeIncomingMessageListener);
        }
        activeIncomingMessageListener = null;
        clearChannelIfUnused();
    }

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

    private void clearChannelIfUnused() {
        if (activePresenceListener == null && activeIncomingMessageListener == null) {
            activeSessionChannel = null;
        }
    }

    private static void runVirtualTask(String taskName, Runnable task) {
        Thread.ofVirtual().name(taskName).start(task);
    }

    private static final class DefaultSessionContext implements SessionContext {

        @Override
        public NetworkGateway networkGateway() {
            var adapter = NetworkSession.get();
            if (adapter == null) {
                return null;
            }

            return new NetworkGateway() {
                @Override
                public CompletableFuture<String> revokeSession() {
                    return adapter.postAuthenticated("/api/v1/logout", "{}");
                }

                @Override
                public void close() {
                    adapter.close();
                }
            };
        }

        @Override
        public SessionChannel sessionChannel() {
            MessageViewModel messageViewModel = ChatSession.get();
            if (messageViewModel == null) {
                return null;
            }

            return new SessionChannel() {
                @Override
                public void addPresenceListener(MessageViewModel.PresenceListener listener) {
                    messageViewModel.addPresenceListener(listener);
                }

                @Override
                public void removePresenceListener(MessageViewModel.PresenceListener listener) {
                    messageViewModel.removePresenceListener(listener);
                }

                @Override
                public void addIncomingMessageListener(MessageViewModel.IncomingMessageListener listener) {
                    messageViewModel.addIncomingMessageListener(listener);
                }

                @Override
                public void removeIncomingMessageListener(MessageViewModel.IncomingMessageListener listener) {
                    messageViewModel.removeIncomingMessageListener(listener);
                }
            };
        }

        @Override
        public void clearNetworkSession() {
            NetworkSession.clear();
        }

        @Override
        public void clearChatSession() {
            ChatSession.clear();
        }

        @Override
        public void clearCurrentUserProfile() {
            CurrentUserSession.clear();
        }
    }
}
