package com.haf.client.services;

import com.haf.client.viewmodels.MessagesViewModel;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMainSessionServiceTest {

    @Test
    void logout_success_path_revokes_closes_and_clears_sessions() {
        StubNetworkGateway networkGateway = new StubNetworkGateway();
        networkGateway.revokeResult = CompletableFuture.completedFuture("{}");
        StubSessionChannel sessionChannel = new StubSessionChannel();
        StubSessionContext context = new StubSessionContext(networkGateway, sessionChannel);

        DefaultMainSessionService service = new DefaultMainSessionService(context, (name, task) -> task.run());
        service.registerPresenceListener((userId, active) -> {
        });
        service.registerIncomingMessageListener((senderId, message) -> {
        });

        service.logout().join();

        assertEquals(1, networkGateway.revokeCalls.get());
        assertEquals(1, networkGateway.closeCalls.get());
        assertEquals(1, sessionChannel.presenceAddCalls.get());
        assertEquals(1, sessionChannel.presenceRemoveCalls.get());
        assertEquals(1, sessionChannel.incomingAddCalls.get());
        assertEquals(1, sessionChannel.incomingRemoveCalls.get());
        assertEquals(1, sessionChannel.stopReceivingCalls.get());
        assertTrue(context.networkCleared);
        assertTrue(context.chatCleared);
        assertTrue(context.currentUserProfileCleared);
    }

    @Test
    void logout_server_failure_still_closes_and_clears_sessions() {
        StubNetworkGateway networkGateway = new StubNetworkGateway();
        networkGateway.revokeResult = CompletableFuture.failedFuture(new RuntimeException("server down"));
        StubSessionContext context = new StubSessionContext(networkGateway, new StubSessionChannel());

        DefaultMainSessionService service = new DefaultMainSessionService(context, (name, task) -> task.run());

        service.logout().join();

        assertEquals(1, networkGateway.revokeCalls.get());
        assertEquals(1, networkGateway.closeCalls.get());
        assertTrue(context.networkCleared);
        assertTrue(context.chatCleared);
        assertTrue(context.currentUserProfileCleared);
    }

    @Test
    void register_and_unregister_presence_listener_delegates_to_channel() {
        StubSessionChannel sessionChannel = new StubSessionChannel();
        StubSessionContext context = new StubSessionContext(null, sessionChannel);
        DefaultMainSessionService service = new DefaultMainSessionService(context, (name, task) -> task.run());

        MessagesViewModel.PresenceListener listener = (userId, active) -> {
        };
        service.registerPresenceListener(listener);
        service.unregisterPresenceListener();

        assertEquals(1, sessionChannel.presenceAddCalls.get());
        assertEquals(1, sessionChannel.presenceRemoveCalls.get());
        assertEquals(listener, sessionChannel.lastPresenceRemoved.get());
    }

    @Test
    void register_and_unregister_incoming_listener_delegates_to_channel() {
        StubSessionChannel sessionChannel = new StubSessionChannel();
        StubSessionContext context = new StubSessionContext(null, sessionChannel);
        DefaultMainSessionService service = new DefaultMainSessionService(context, (name, task) -> task.run());

        MessagesViewModel.IncomingMessageListener listener = (senderId, message) -> {
        };
        service.registerIncomingMessageListener(listener);
        service.unregisterIncomingMessageListener();

        assertEquals(1, sessionChannel.incomingAddCalls.get());
        assertEquals(1, sessionChannel.incomingRemoveCalls.get());
        assertEquals(listener, sessionChannel.lastIncomingRemoved.get());
    }

    private static final class StubNetworkGateway implements DefaultMainSessionService.NetworkGateway {
        private final AtomicInteger revokeCalls = new AtomicInteger();
        private final AtomicInteger closeCalls = new AtomicInteger();
        private CompletableFuture<String> revokeResult = CompletableFuture.completedFuture("{}");

        @Override
        public CompletableFuture<String> revokeSession() {
            revokeCalls.incrementAndGet();
            return revokeResult;
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
        }
    }

    private static final class StubSessionChannel implements DefaultMainSessionService.SessionChannel {
        private final AtomicInteger presenceAddCalls = new AtomicInteger();
        private final AtomicInteger presenceRemoveCalls = new AtomicInteger();
        private final AtomicReference<MessagesViewModel.PresenceListener> lastPresenceAdded = new AtomicReference<>();
        private final AtomicReference<MessagesViewModel.PresenceListener> lastPresenceRemoved = new AtomicReference<>();
        private final AtomicInteger incomingAddCalls = new AtomicInteger();
        private final AtomicInteger incomingRemoveCalls = new AtomicInteger();
        private final AtomicReference<MessagesViewModel.IncomingMessageListener> lastIncomingAdded = new AtomicReference<>();
        private final AtomicReference<MessagesViewModel.IncomingMessageListener> lastIncomingRemoved = new AtomicReference<>();
        private final AtomicInteger stopReceivingCalls = new AtomicInteger();

        @Override
        public void addPresenceListener(MessagesViewModel.PresenceListener listener) {
            presenceAddCalls.incrementAndGet();
            lastPresenceAdded.set(listener);
        }

        @Override
        public void removePresenceListener(MessagesViewModel.PresenceListener listener) {
            presenceRemoveCalls.incrementAndGet();
            lastPresenceRemoved.set(listener);
        }

        @Override
        public void addIncomingMessageListener(MessagesViewModel.IncomingMessageListener listener) {
            incomingAddCalls.incrementAndGet();
            lastIncomingAdded.set(listener);
        }

        @Override
        public void removeIncomingMessageListener(MessagesViewModel.IncomingMessageListener listener) {
            incomingRemoveCalls.incrementAndGet();
            lastIncomingRemoved.set(listener);
        }

        @Override
        public void stopReceiving() {
            stopReceivingCalls.incrementAndGet();
        }
    }

    private static final class StubSessionContext implements DefaultMainSessionService.SessionContext {
        private final DefaultMainSessionService.NetworkGateway networkGateway;
        private final DefaultMainSessionService.SessionChannel sessionChannel;
        private boolean networkCleared;
        private boolean chatCleared;
        private boolean currentUserProfileCleared;

        private StubSessionContext(
                DefaultMainSessionService.NetworkGateway networkGateway,
                DefaultMainSessionService.SessionChannel sessionChannel) {
            this.networkGateway = networkGateway;
            this.sessionChannel = sessionChannel;
        }

        @Override
        public DefaultMainSessionService.NetworkGateway networkGateway() {
            return networkGateway;
        }

        @Override
        public DefaultMainSessionService.SessionChannel sessionChannel() {
            return sessionChannel;
        }

        @Override
        public void clearNetworkSession() {
            networkCleared = true;
        }

        @Override
        public void clearChatSession() {
            chatCleared = true;
        }

        @Override
        public void clearCurrentUserProfile() {
            currentUserProfileCleared = true;
        }
    }
}
