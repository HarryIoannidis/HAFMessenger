package com.haf.client.utils;

import com.haf.client.exceptions.HttpCommunicationException;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.ConnectException;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeIssueTest {

    @Test
    void legacy_constructor_defaults_connection_flag_to_false() {
        RuntimeIssue issue = new RuntimeIssue("k", "t", "m", () -> {
        });
        assertFalse(issue.connectionIssue());
    }

    @Test
    void canonical_constructor_preserves_connection_flag() {
        RuntimeIssue issue = new RuntimeIssue("k", "t", "m", () -> {
        }, true);
        assertTrue(issue.connectionIssue());
    }

    @Test
    void connection_failure_classifier_detects_transport_errors_and_http_5xx() {
        assertTrue(RuntimeIssue.isConnectionFailure(new ConnectException("refused")));
        assertTrue(RuntimeIssue.isConnectionFailure(
                new HttpCommunicationException("HTTP GET failed", 503, "{\"error\":\"down\"}")));
        assertTrue(RuntimeIssue.isConnectionFailure(new IOException("io", new ConnectException("refused"))));
        assertFalse(RuntimeIssue.isConnectionFailure(
                new HttpCommunicationException("HTTP GET failed", 401, "{\"error\":\"invalid session\"}")));
        assertFalse(RuntimeIssue.isConnectionFailure(new IllegalArgumentException("bad payload")));
    }
}
