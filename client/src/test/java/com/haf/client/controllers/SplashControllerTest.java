package com.haf.client.controllers;

import com.haf.client.utils.PopupMessageSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplashControllerTest {

    @Test
    void classify_failure_detects_network_errors() {
        SplashController.FailurePresentation presentation = SplashController
                .classifyFailure(new IOException("Failed to check server reachability", new ConnectException("Connection refused")));

        assertEquals("Cannot reach server", presentation.title());
        assertTrue(presentation.message().contains("Connection refused"));
    }

    @Test
    void classify_failure_detects_missing_resource_errors() {
        SplashController.FailurePresentation presentation = SplashController
                .classifyFailure(new IOException("Login view missing at /fxml/login.fxml"));

        assertEquals("Application files missing", presentation.title());
        assertTrue(presentation.message().contains("missing"));
    }

    @Test
    void classify_failure_detects_security_errors() {
        SplashController.FailurePresentation presentation = SplashController
                .classifyFailure(new IOException("Security init failed", new GeneralSecurityException("No such algorithm")));

        assertEquals("Security initialization failed", presentation.title());
        assertTrue(presentation.message().contains("No such algorithm"));
    }

    @Test
    void classify_failure_uses_generic_fallback_when_uncategorized() {
        SplashController.FailurePresentation presentation = SplashController
                .classifyFailure(new IllegalStateException("Unexpected startup branch"));

        assertEquals("Startup failed", presentation.title());
        assertTrue(presentation.message().contains("Unexpected startup branch"));
    }

    @Test
    void failure_popup_spec_routes_retry_and_exit_callbacks() {
        AtomicInteger retryCalls = new AtomicInteger();
        AtomicInteger exitCalls = new AtomicInteger();

        PopupMessageSpec spec = SplashController.buildFailurePopupSpec(
                new IOException("network down"),
                retryCalls::incrementAndGet,
                exitCalls::incrementAndGet);

        spec.onAction().run();
        spec.onCancel().run();

        assertEquals("Retry", spec.actionText());
        assertEquals("Exit", spec.cancelText());
        assertEquals(1, retryCalls.get());
        assertEquals(1, exitCalls.get());
    }
}
