package com.haf.client.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeIssuePopupGateTest {

    @Test
    void same_key_is_suppressed_within_cooldown_window() {
        RuntimeIssuePopupGate gate = new RuntimeIssuePopupGate(10_000L);

        assertTrue(gate.shouldShow("messaging.send.failed", 1_000L));
        assertFalse(gate.shouldShow("messaging.send.failed", 10_999L));
        assertTrue(gate.shouldShow("messaging.send.failed", 11_000L));
    }

    @Test
    void different_keys_are_not_suppressed_by_each_other() {
        RuntimeIssuePopupGate gate = new RuntimeIssuePopupGate(10_000L);

        assertTrue(gate.shouldShow("search.request.failed", 1_000L));
        assertTrue(gate.shouldShow("contacts.fetch.failed", 1_001L));
    }
}
