package com.haf.client.controllers;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewControllerTest {
    private static final Path CONTROLLER_SOURCE = Path.of("src/main/java/com/haf/client/controllers/PreviewController.java");

    @Test
    void attachment_error_spec_defaults_and_custom_messages_are_stable() {
        assertEquals(
                "Attachment operation failed.",
                PreviewController.buildAttachmentErrorSpec(null).message());
        assertEquals(
                "Could not save image. Please try again.",
                PreviewController.buildAttachmentErrorSpec("Could not save image. Please try again.").message());
        assertEquals(
                "Attachment error",
                PreviewController.buildAttachmentErrorSpec("x").title());
        assertTrue(PreviewController.buildAttachmentErrorSpec("x").movable());
    }

    @Test
    void preview_behaviors_are_wired_to_settings_flags() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("boolean visible = settings.isMediaShowDownloadButton();"));
        assertTrue(source.contains("if (!settings.isMediaHoverZoom()) {"));
        assertTrue(source.contains("if (settings.isPrivacyConfirmAttachmentOpen()) {"));
        assertTrue(source.contains(".movable(spec.movable())"));
        assertTrue(source.contains("long previewLoadId = ++activePreviewLoadId;"));
        assertTrue(source.contains("completePreviewLoadIfCurrent(previewLoadId, image)"));
        assertTrue(source.contains("previewLoadId == activePreviewLoadId"));
        assertTrue(source.contains("runOnFxThread(() -> completePreviewLoadIfCurrent(previewLoadId, image));"));
        assertTrue(source.contains("if (image.getProgress() >= 1.0) {"));
    }
}
