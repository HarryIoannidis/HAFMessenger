package com.haf.client.controllers;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactCellTest {

    @Test
    void contact_cell_fxml_exposes_required_fx_ids_for_binding() throws IOException {
        var resource = ContactCellTest.class.getResourceAsStream("/fxml/contact_cell.fxml");
        assertNotNull(resource);
        String fxml;
        try (resource) {
            fxml = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(fxml.contains("fx:id=\"nameText\""));
        assertTrue(fxml.contains("fx:id=\"regNumberText\""));
        assertTrue(fxml.contains("fx:id=\"activenessCircle\""));
        assertTrue(fxml.contains("fx:id=\"unreadBadge\""));
        assertTrue(fxml.contains("fx:id=\"unreadBadgeText\""));
        assertTrue(fxml.contains("fx:id=\"overlayButton\""));
    }

    @Test
    void unread_badge_text_uses_dynamic_cap_format() throws Exception {
        String source = java.nio.file.Files
                .readString(java.nio.file.Path.of("src/main/java/com/haf/client/controllers/ContactCellController.java"));

        assertTrue(source.contains("formatUnreadBadgeText(unreadCount, unreadBadgeCap)"));
        assertTrue(source.contains("if (normalized > cap) {"));
        assertTrue(source.contains("return cap + \"+\";"));
        assertEquals(true, source.contains("showUnreadBadges && shouldShowUnreadBadge(unreadCount)"));
    }
}
