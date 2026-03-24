package com.haf.client.viewmodels;

import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class SplashViewModelTest {
    private static volatile boolean javaFxAvailable = true;

    @BeforeAll
    static void initJavaFx() {
        // Initialize JavaFX toolkit once for all tests; ignore if already initialized
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // already started
        } catch (Throwable ex) {
            javaFxAvailable = false;
        }
    }

    private static void assumeJavaFxAvailable() {
        Assumptions.assumeTrue(javaFxAvailable, "JavaFX toolkit unavailable in this environment");
    }

    @Test
    void bootstrap_runs_and_reaches_ready() throws Exception {
        assumeJavaFxAvailable();
        CountDownLatch success = new CountDownLatch(1);

        SplashViewModel vm = new SplashViewModel(
                () -> "2.0.0-test",
                () -> {},
                () -> {},
                () -> {}
        );

        vm.startBootstrap(success::countDown, ex -> fail("Should not fail"));

        assertTrue(success.await(5, TimeUnit.SECONDS));
        assertEquals("Ready", vm.statusProperty().get());
        assertEquals("2.0.0-test", vm.versionProperty().get());
        assertEquals(1.0, vm.progressProperty().get(), 0.001);
        assertEquals("100%", vm.percentageProperty().get());
    }

    @Test
    void bootstrap_failure_invokes_error_handler() throws Exception {
        assumeJavaFxAvailable();
        CountDownLatch failure = new CountDownLatch(1);
        AtomicBoolean successCalled = new AtomicBoolean(false);

        SplashViewModel vm = new SplashViewModel(
                () -> "ver",
                () -> {},
                () -> {},
                () -> { throw new IOException("network down"); }
        );

        vm.startBootstrap(() -> successCalled.set(true), ex -> failure.countDown());

        assertTrue(failure.await(5, TimeUnit.SECONDS));
        assertFalse(successCalled.get());
        assertTrue(vm.progressProperty().get() < 1.0);
    }

    @Test
    void bootstrap_updates_messages_in_order() throws Exception {
        assumeJavaFxAvailable();
        CountDownLatch success = new CountDownLatch(1);
        List<String> order = new CopyOnWriteArrayList<>();

        SplashViewModel vm = new SplashViewModel(
                () -> {
                    order.add("Loading configuration...");
                    return "v";
                },
                () -> order.add("Initializing security modules..."),
                () -> order.add("Checking local resources..."),
                () -> order.add("Verifying network reachability...")
        );

        vm.startBootstrap(success::countDown, ex -> fail("Should not fail"));

        assertTrue(success.await(6, TimeUnit.SECONDS));

        List<String> expectedOrder = List.of(
                "Loading configuration...",
                "Initializing security modules...",
                "Checking local resources...",
                "Verifying network reachability..."
        );

        assertEquals(expectedOrder, order, "Stages should execute in order");
    }
}
