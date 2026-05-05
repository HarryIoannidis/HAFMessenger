package com.haf.client.controllers;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopupMessageControllerTest {

    private static final Path CONTROLLER_SOURCE = Path.of("src/main/java/com/haf/client/controllers/PopupMessageController.java");

    @Test
    void drag_handlers_respect_popup_movable_flag() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("if (!currentSpec.movable()) {"));
        assertTrue(source.contains("titleBar.setOnMousePressed"));
        assertTrue(source.contains("titleBar.setOnMouseDragged"));
    }

    @Test
    void startup_privacy_unlock_popup_keeps_close_button_hidden_rule() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("STARTUP_PRIVACY_UNLOCK_POPUP_KEY"));
        assertTrue(source.contains("SESSION_REVOKED_POPUP_KEY"));
        assertTrue(source.contains("boolean showCloseButton = !isCloseDisabledPopup(spec.popupKey());"));
        assertTrue(source.contains("|| SESSION_REVOKED_POPUP_KEY.equals(popupKey)"));
        assertTrue(source.contains("|| UiConstants.POPUP_CONNECTION_LOSS.equals(popupKey)"));
    }

    @Test
    void popup_buttons_are_focus_traversable_for_keyboard_navigation() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("closeButton.setFocusTraversable(false);"));
        assertTrue(source.contains("cancelButton.setFocusTraversable(true);"));
        assertTrue(source.contains("actionButton.setFocusTraversable(true);"));
    }
}
