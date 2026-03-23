package com.haf.client.controllers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreviewControllerTest {

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
    }
}
