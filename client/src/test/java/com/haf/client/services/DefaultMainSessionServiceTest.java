package com.haf.client.services;

import com.haf.client.viewmodels.MessageViewModel;
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
        StubPresenceChannel presenceChannel = new StubPresenceChannel();
        StubSessionContext context = new StubSessionContext(networkGateway, presenceChannel);

        DefaultMainSessionService service = new DefaultMainSessionService(context, (name, task) -> task.run());
        service.registerPresenceListener((userId, active) -> {
        });

        service.logout().join();

        assertEquals(1, networkGateway.revokeCalls.get());
        assertEquals(1, networkGateway.closeCalls.get());
        assertEquals(1, presenceChannel.addCalls.get());
        assertEquals(1, presenceChannel.removeCalls.get());
        assertTrue(context.networkCleared);
        assertTrue(context.chatCleared);
        assertTrue(context.currentUserProfileCleared);
    }

    @Test
    void logout_server_failure_still_closes_and_clears_sessions() {
        StubNetworkGateway networkGateway = new StubNetworkGateway();
        networkGateway.revokeResult = CompletableFuture.failedFuture(new RuntimeException("server down"));
        StubSessionContext context = new StubSessionContext(networkGateway, new StubPresenceChannel());

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
        StubPresenceChannel presenceChannel = new StubPresenceChannel();
        StubSessionContext context = new StubSessionContext(null, presenceChannel);
        DefaultMainSessionService service = new DefaultMainSessionService(context, (name, task) -> task.run());

        MessageViewModel.PresenceListener listener = (userId, active) -> {
        };
        service.registerPresenceListener(listener);
        service.unregisterPresenceListener();

        assertEquals(1, presenceChannel.addCalls.get());
        assertEquals(1, presenceChannel.removeCalls.get());
        assertEquals(listener, presenceChannel.lastRemoved.get());
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

    private static final class StubPresenceChannel implements DefaultMainSessionService.PresenceChannel {
        private final AtomicInteger addCalls = new AtomicInteger();
        private final AtomicInteger removeCalls = new AtomicInteger();
        private final AtomicReference<MessageViewModel.PresenceListener> lastAdded = new AtomicReference<>();
        private final AtomicReference<MessageViewModel.PresenceListener> lastRemoved = new AtomicReference<>();

        @Override
        public void addPresenceListener(MessageViewModel.PresenceListener listener) {
            addCalls.incrementAndGet();
            lastAdded.set(listener);
        }

        @Override
        public void removePresenceListener(MessageViewModel.PresenceListener listener) {
            removeCalls.incrementAndGet();
            lastRemoved.set(listener);
        }
    }

    private static final class StubSessionContext implements DefaultMainSessionService.SessionContext {
        private final DefaultMainSessionService.NetworkGateway networkGateway;
        private final DefaultMainSessionService.PresenceChannel presenceChannel;
        private boolean networkCleared;
        private boolean chatCleared;
        private boolean currentUserProfileCleared;

        private StubSessionContext(
                DefaultMainSessionService.NetworkGateway networkGateway,
                DefaultMainSessionService.PresenceChannel presenceChannel) {
            this.networkGateway = networkGateway;
            this.presenceChannel = presenceChannel;
        }

        @Override
        public DefaultMainSessionService.NetworkGateway networkGateway() {
            return networkGateway;
        }

        @Override
        public DefaultMainSessionService.PresenceChannel presenceChannel() {
            return presenceChannel;
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
