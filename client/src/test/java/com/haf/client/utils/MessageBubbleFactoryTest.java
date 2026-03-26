package com.haf.client.utils;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageBubbleFactoryTest {

    private static final Path FACTORY_SOURCE = Path.of("src/main/java/com/haf/client/utils/MessageBubbleFactory.java");

    @Test
    void message_bubbles_are_wrapped_with_stackpane_and_overlay_button() throws IOException {
        String source = Files.readString(FACTORY_SOURCE);

        assertTrue(source.contains("StackPane bubbleStack = new StackPane(bubble, rippleOverlay);"));
        assertTrue(source.contains("row.getChildren().add(bubbleStack);"));
        assertTrue(source.contains("bubble.getStyleClass().add(message.isOutgoing() ? \"bubble-out\" : \"bubble-in\");"));
    }

    @Test
    void ripple_overlay_styles_cover_shared_and_directional_classes() throws IOException {
        String source = Files.readString(FACTORY_SOURCE);

        assertTrue(source.contains("rippleOverlay.getStyleClass().add(\"bubble-ripple-overlay\");"));
        assertTrue(source.contains("message.isOutgoing() ? \"bubble-ripple-out\" : \"bubble-ripple-in\""));
    }

    @Test
    void overlay_hover_and_pressed_states_are_forwarded_to_bubble() throws IOException {
        String source = Files.readString(FACTORY_SOURCE);

        assertTrue(source.contains("bubble.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, hovering)"));
        assertTrue(source.contains("bubble.pseudoClassStateChanged(PRESSED_PSEUDO_CLASS, pressed)"));
    }
}
