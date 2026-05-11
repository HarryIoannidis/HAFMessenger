package com.haf.client.utils;

import com.haf.client.builders.MessageBubbleFactory;
import com.haf.client.models.MessageType;
import com.haf.client.models.MessageVM;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignZ;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageBubbleFactoryTest {

    private static final Path FACTORY_SOURCE = Path.of("src/main/java/com/haf/client/builders/MessageBubbleFactory.java");

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
    void outgoing_pending_text_bubbles_apply_pending_style_overrides() throws IOException {
        String source = Files.readString(FACTORY_SOURCE);

        assertTrue(source.contains("bubble.getStyleClass().add(\"bubble-out-pending\");"));
        assertTrue(source.contains("rippleOverlay.getStyleClass().add(\"bubble-ripple-out-pending\");"));
        assertTrue(source.contains("text.getStyleClass().add(\"bubble-text-out-pending\");"));
        assertTrue(source.contains("ts.getStyleClass().add(\"bubble-time-out-pending\");"));
    }

    @Test
    void outgoing_receipt_badge_uses_single_faded_or_double_full_checks() throws IOException {
        String source = Files.readString(FACTORY_SOURCE);

        assertTrue(source.contains("buildReceiptBadge(message)"));
        assertTrue(source.contains("int checkCount = read ? 2 : 1;"));
        assertTrue(source.contains("receipt.setOpacity(read ? 1.0 : 0.45);"));
        assertTrue(source.contains("badge.setPrefWidth(18);"));
    }

    @Test
    void overlay_hover_and_pressed_states_are_forwarded_to_bubble() throws IOException {
        String source = Files.readString(FACTORY_SOURCE);

        assertTrue(source.contains("bubble.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, hovering)"));
        assertTrue(source.contains("bubble.pseudoClassStateChanged(PRESSED_PSEUDO_CLASS, pressed)"));
    }

    @Test
    void file_icon_resolver_uses_existing_mdi2_icons_for_common_extensions() throws Exception {
        assertSame(MaterialDesignF.FILE_PDF_BOX, resolveFileIcon("orders.pdf"));
        assertSame(MaterialDesignF.FILE_WORD_BOX, resolveFileIcon("brief.docx"));
        assertSame(MaterialDesignF.FILE_EXCEL_BOX, resolveFileIcon("sheet.xlsx"));
        assertSame(MaterialDesignF.FILE_POWERPOINT_BOX, resolveFileIcon("deck.pptx"));
        assertSame(MaterialDesignZ.ZIP_BOX, resolveFileIcon("archive.zip"));
        assertSame(MaterialDesignF.FILE, resolveFileIcon("unknown.haf"));
    }

    private static Ikon resolveFileIcon(String fileName) throws Exception {
        Method method = MessageBubbleFactory.class.getDeclaredMethod("resolveFileIcon", MessageVM.class);
        method.setAccessible(true);
        MessageVM message = new MessageVM(
                false,
                MessageType.FILE,
                null,
                null,
                fileName,
                "1 KB",
                LocalDateTime.now(),
                false);
        return (Ikon) method.invoke(null, message);
    }
}
