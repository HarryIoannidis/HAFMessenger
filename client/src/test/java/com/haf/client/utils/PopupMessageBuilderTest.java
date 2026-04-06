package com.haf.client.utils;

import org.junit.jupiter.api.Test;

import com.haf.client.builders.PopupMessageBuilder;

import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopupMessageBuilderTest {

    @Test
    void builder_uses_expected_defaults() {
        PopupMessageSpec spec = PopupMessageBuilder.create().build();

        assertEquals("popup-message-default", spec.popupKey());
        assertEquals("Notice", spec.title());
        assertEquals("", spec.message());
        assertEquals("OK", spec.actionText());
        assertEquals("Cancel", spec.cancelText());
        assertTrue(spec.showCancel());
        assertFalse(spec.dangerAction());
        assertTrue(spec.movable());
        assertNotNull(spec.onAction());
        assertNotNull(spec.onCancel());
    }

    @Test
    void builder_applies_chainable_values() {
        AtomicInteger actionCalls = new AtomicInteger();
        AtomicInteger cancelCalls = new AtomicInteger();

        PopupMessageSpec spec = PopupMessageBuilder.create()
                .popupKey("custom-key")
                .title("Delete chat")
                .message("This action is not recoverable.")
                .actionText("Delete")
                .cancelText("Keep")
                .dangerAction(true)
                .movable(false)
                .singleAction(false)
                .onAction(actionCalls::incrementAndGet)
                .onCancel(cancelCalls::incrementAndGet)
                .build();

        assertEquals("custom-key", spec.popupKey());
        assertEquals("Delete chat", spec.title());
        assertEquals("This action is not recoverable.", spec.message());
        assertEquals("Delete", spec.actionText());
        assertEquals("Keep", spec.cancelText());
        assertTrue(spec.showCancel());
        assertTrue(spec.dangerAction());
        assertFalse(spec.movable());

        spec.onAction().run();
        spec.onCancel().run();
        assertEquals(1, actionCalls.get());
        assertEquals(1, cancelCalls.get());
    }

    @Test
    void builder_single_action_hides_cancel_button() {
        PopupMessageSpec spec = PopupMessageBuilder.create()
                .singleAction(true)
                .build();

        assertFalse(spec.showCancel());
    }
}
