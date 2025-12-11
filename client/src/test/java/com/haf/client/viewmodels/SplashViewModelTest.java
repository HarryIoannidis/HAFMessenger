package com.haf.client.viewmodels;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SplashViewModelTest {

    @Test
    void bootstrap_runs_and_reaches_ready() throws Exception {
        CountDownLatch success = new CountDownLatch(1);

        SplashViewModel vm = new SplashViewModel(
                () -> "2.0.0-test",
                () -> {},
                () -> {},
                () -> {}
        );

        vm.startBootstrap(success::countDown, ex -> fail("Should not fail"));

        assertTrue(success.await(2, TimeUnit.SECONDS));
        assertEquals("Ready", vm.statusProperty().get());
        assertEquals("2.0.0-test", vm.versionProperty().get());
        assertEquals(1.0, vm.progressProperty().get(), 0.001);
        assertEquals("100%", vm.percentageProperty().get());
    }

    @Test
    void bootstrap_failure_invokes_error_handler() throws Exception {
        CountDownLatch failure = new CountDownLatch(1);
        AtomicBoolean successCalled = new AtomicBoolean(false);

        SplashViewModel vm = new SplashViewModel(
                () -> "ver",
                () -> {},
                () -> {},
                () -> { throw new IOException("network down"); }
        );

        vm.startBootstrap(() -> successCalled.set(true), ex -> failure.countDown());

        assertTrue(failure.await(2, TimeUnit.SECONDS));
        assertFalse(successCalled.get());
        assertTrue(vm.progressProperty().get() < 1.0);
    }

    @Test
    void bootstrap_updates_messages_in_order() throws Exception {
        CountDownLatch success = new CountDownLatch(1);
        List<String> updates = new CopyOnWriteArrayList<>();

        SplashViewModel vm = new SplashViewModel(
                () -> "v",
                () -> {},
                () -> {},
                () -> {}
        );

        vm.statusProperty().addListener((obs, oldV, newV) -> updates.add(newV));

        vm.startBootstrap(success::countDown, ex -> fail("Should not fail"));

        assertTrue(success.await(2, TimeUnit.SECONDS));

        List<String> expectedOrder = List.of(
                "Loading configuration...",
                "Initializing security modules...",
                "Checking local resources...",
                "Verifying network reachability...",
                "Ready"
        );

        int cursor = 0;
        for (String update : updates) {
            if (cursor < expectedOrder.size() && update.equals(expectedOrder.get(cursor))) {
                cursor++;
            }
        }
        assertEquals(expectedOrder.size(), cursor, "Messages should appear in order");
    }
}
