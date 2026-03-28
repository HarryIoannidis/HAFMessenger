package com.haf.client.controllers;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsControllerLogicTest {

    private static final Path CONTROLLER_SOURCE = Path.of("src/main/java/com/haf/client/controllers/SettingsController.java");

    @Test
    void close_flow_uses_restart_dirty_flag_and_shows_restart_popup() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("if (!settings.isRestartRequiredDirty()) {"));
        assertTrue(source.contains(".title(\"Restart required\")"));
        assertTrue(source.contains(".actionText(\"Restart now\")"));
        assertTrue(source.contains(".cancelText(\"Later\")"));
    }

    @Test
    void row_overlay_clicks_toggle_switch_and_checkbox_controls() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("wireOverlayRowToggle(rowId, () -> toggle.setSelected(!toggle.isSelected()));"));
        assertTrue(source.contains("wireOverlayRowToggle(rowId, () -> checkBox.setSelected(!checkBox.isSelected()));"));
    }
}
