package com.haf.client.services;

import com.haf.client.core.ChatSession;
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

    interface PresenceChannel {
        void addPresenceListener(MessageViewModel.PresenceListener listener);

        void removePresenceListener(MessageViewModel.PresenceListener listener);
    }

    interface SessionContext {
        NetworkGateway networkGateway();

        PresenceChannel presenceChannel();

        void clearNetworkSession();

        void clearChatSession();
    }

    @FunctionalInterface
    interface TaskRunner {
        void run(String taskName, Runnable task);
    }

    private final SessionContext sessionContext;
    private final TaskRunner taskRunner;

    private PresenceChannel activePresenceChannel;
    private MessageViewModel.PresenceListener activePresenceListener;

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
        PresenceChannel channel = sessionContext.presenceChannel();
        if (channel == null) {
            return;
        }

        channel.addPresenceListener(listener);
        activePresenceChannel = channel;
        activePresenceListener = listener;
    }

    @Override
    public synchronized void unregisterPresenceListener() {
        if (activePresenceChannel != null && activePresenceListener != null) {
            activePresenceChannel.removePresenceListener(activePresenceListener);
        }
        activePresenceChannel = null;
        activePresenceListener = null;
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
        sessionContext.clearNetworkSession();
        sessionContext.clearChatSession();
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
        public PresenceChannel presenceChannel() {
            MessageViewModel messageViewModel = ChatSession.get();
            if (messageViewModel == null) {
                return null;
            }

            return new PresenceChannel() {
                @Override
                public void addPresenceListener(MessageViewModel.PresenceListener listener) {
                    messageViewModel.addPresenceListener(listener);
                }

                @Override
                public void removePresenceListener(MessageViewModel.PresenceListener listener) {
                    messageViewModel.removePresenceListener(listener);
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
    }
}
