package com.haf.client.controllers;

import com.haf.client.services.ChatAttachmentService;
import com.haf.client.models.MessageType;
import com.haf.client.models.MessageVM;
import com.haf.client.utils.UiConstants;
import com.haf.client.viewmodels.MessagesViewModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatControllerTest {
    private static final Path CONTROLLER_SOURCE = Path.of("src/main/java/com/haf/client/controllers/ChatController.java");

    @TempDir
    Path tempDir;

    @Test
    void constructor_rejects_null_attachment_service() {
        assertThrows(NullPointerException.class, () -> new ChatController(null));
    }

    @Test
    void selected_attachment_delegates_recipient_and_path_to_service() {
        StubChatAttachmentService attachmentService = new StubChatAttachmentService();
        ChatController controller = new ChatController(attachmentService);
        File selected = new File("doc.pdf");

        controller.dispatchSelectedAttachment("recipient-1", selected);

        assertEquals(1, attachmentService.calls.get());
        assertEquals("recipient-1", attachmentService.recipientId.get());
        assertEquals(selected.toPath(), attachmentService.filePath.get());
    }

    @Test
    void selected_attachment_is_noop_when_recipient_is_missing_or_blank() {
        StubChatAttachmentService attachmentService = new StubChatAttachmentService();
        ChatController controller = new ChatController(attachmentService);
        File selected = new File("doc.pdf");

        controller.dispatchSelectedAttachment(null, selected);
        controller.dispatchSelectedAttachment("", selected);
        controller.dispatchSelectedAttachment("   ", selected);

        assertEquals(0, attachmentService.calls.get());
    }

    @Test
    void context_action_mapping_matches_spec_for_text_and_images() {
        MessageVM outgoingText = new MessageVM(
                true,
                MessageType.TEXT,
                "hello",
                null,
                null,
                null,
                LocalDateTime.now(),
                false);
        MessageVM incomingText = new MessageVM(
                false,
                MessageType.TEXT,
                "hello",
                null,
                null,
                null,
                LocalDateTime.now(),
                false);
        MessageVM outgoingImage = new MessageVM(
                true,
                MessageType.IMAGE,
                "file:///tmp/outgoing.png",
                null,
                "outgoing.png",
                null,
                LocalDateTime.now(),
                false);
        MessageVM incomingImage = new MessageVM(
                false,
                MessageType.IMAGE,
                "file:///tmp/incoming.png",
                null,
                "incoming.png",
                null,
                LocalDateTime.now(),
                false);
        MessageVM fileMessage = new MessageVM(
                false,
                MessageType.FILE,
                null,
                "file:///tmp/report.pdf",
                "report.pdf",
                "25 KB",
                LocalDateTime.now(),
                false);
        MessageVM loadingFileMessage = new MessageVM(
                false,
                MessageType.FILE,
                null,
                null,
                "report.pdf",
                "25 KB",
                LocalDateTime.now(),
                true);

        assertEquals(List.of(ChatController.MessageContextAction.COPY), ChatController.resolveContextActions(outgoingText));
        assertEquals(List.of(ChatController.MessageContextAction.COPY), ChatController.resolveContextActions(incomingText));
        assertEquals(List.of(ChatController.MessageContextAction.PREVIEW), ChatController.resolveContextActions(outgoingImage));
        assertEquals(
                List.of(ChatController.MessageContextAction.PREVIEW, ChatController.MessageContextAction.DOWNLOAD),
                ChatController.resolveContextActions(incomingImage));
        assertEquals(List.of(ChatController.MessageContextAction.DOWNLOAD), ChatController.resolveContextActions(fileMessage));
        assertEquals(List.of(), ChatController.resolveContextActions(loadingFileMessage));
    }

    @Test
    void suggested_image_name_prefers_message_file_name_then_source_then_default() {
        MessageVM withProvidedName = new MessageVM(
                false,
                MessageType.IMAGE,
                "file:///tmp/fallback.png",
                null,
                "provided-name.png",
                null,
                LocalDateTime.now(),
                false);
        MessageVM withSourceOnly = new MessageVM(
                false,
                MessageType.IMAGE,
                "file:///tmp/from-source.webp",
                null,
                null,
                null,
                LocalDateTime.now(),
                false);
        MessageVM withNothing = new MessageVM(
                false,
                MessageType.IMAGE,
                null,
                null,
                null,
                null,
                LocalDateTime.now(),
                false);

        assertEquals("provided-name.png", ChatController.resolveSuggestedDownloadFileName(withProvidedName));
        assertEquals("from-source.webp", ChatController.resolveSuggestedDownloadFileName(withSourceOnly));
        assertEquals("image-preview.png", ChatController.resolveSuggestedDownloadFileName(withNothing));
    }

    @Test
    void download_source_reference_uses_content_for_images_and_local_path_for_files() {
        MessageVM image = new MessageVM(
                false,
                MessageType.IMAGE,
                "file:///tmp/image.png",
                null,
                "image.png",
                null,
                LocalDateTime.now(),
                false);
        MessageVM file = new MessageVM(
                false,
                MessageType.FILE,
                null,
                "file:///tmp/report.pdf",
                "report.pdf",
                "25 KB",
                LocalDateTime.now(),
                false);

        assertEquals("file:///tmp/image.png", ChatController.resolveDownloadSourceReference(image));
        assertEquals("file:///tmp/report.pdf", ChatController.resolveDownloadSourceReference(file));
    }

    @Test
    void attachment_error_spec_defaults_and_custom_messages_are_stable() {
        assertEquals(
                "Attachment operation failed.",
                ChatController.buildAttachmentErrorSpec(null).message());
        assertEquals(
                "Attachment source file could not be found.",
                ChatController.buildAttachmentErrorSpec("Attachment source file could not be found.").message());
        assertEquals(
                "Attachment error",
                ChatController.buildAttachmentErrorSpec("x").title());
        assertTrue(ChatController.buildAttachmentErrorSpec("x").movable());
    }

    @Test
    void oversize_attachment_spec_uses_image_wording_and_pick_another_action() {
        var spec = ChatController.buildAttachmentTooLargeSpec(Path.of("photo.png"), () -> {
        });

        assertEquals("The image you are trying to send is over 10MB.", spec.message());
        assertEquals("Pick Another", spec.actionText());
        assertEquals("Cancel", spec.cancelText());
        assertTrue(spec.movable());
    }

    @Test
    void oversize_attachment_spec_uses_file_wording_for_non_image() {
        var spec = ChatController.buildAttachmentTooLargeSpec(Path.of("report.pdf"), () -> {
        });

        assertEquals("The file you are trying to send is over 10MB.", spec.message());
    }

    @Test
    void image_fallback_spec_uses_close_and_dont_show_again_actions() {
        MessagesViewModel.ImagePreviewFallbackNotice notice = new MessagesViewModel.ImagePreviewFallbackNotice(
                "bob",
                true,
                "cloud.png",
                "image/png",
                "webp");
        AtomicInteger dontShowCalls = new AtomicInteger();

        var spec = ChatController.buildImageFallbackNoticeSpec(notice, dontShowCalls::incrementAndGet);

        assertEquals(UiConstants.POPUP_IMAGE_FALLBACK_NOTICE, spec.popupKey());
        assertEquals("Image sent as file", spec.title());
        assertEquals("Don't show again", spec.actionText());
        assertEquals("Close", spec.cancelText());
        assertTrue(spec.showCancel());
        assertTrue(spec.message().contains("labeled as PNG"));
        assertTrue(spec.message().contains("WEBP"));
        spec.onAction().run();
        assertEquals(1, dontShowCalls.get());
    }

    @Test
    void image_fallback_message_handles_unknown_signature() {
        MessagesViewModel.ImagePreviewFallbackNotice notice = new MessagesViewModel.ImagePreviewFallbackNotice(
                "bob",
                false,
                "mystery.png",
                "image/png",
                "unknown");

        String message = ChatController.buildImageFallbackMessage(notice);
        assertTrue(message.contains("could not be recognized"));
    }

    @Test
    void over_attachment_limit_detects_files_larger_than_10mb() throws Exception {
        Path exact10Mb = tempDir.resolve("exact.bin");
        Path above10Mb = tempDir.resolve("above.bin");

        Files.write(exact10Mb, new byte[10 * 1024 * 1024]);
        Files.write(above10Mb, new byte[10 * 1024 * 1024 + 1]);

        assertEquals(false, ChatController.isOverAttachmentLimit(exact10Mb));
        assertEquals(true, ChatController.isOverAttachmentLimit(above10Mb));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "if (firstChild instanceof StackPane stackPane)",
            "Node rippleOverlay = findRippleOverlay(stackPane);",
            "if (rippleOverlay != null) {",
            "return rippleOverlay;",
            "return firstChild;",
            "return messageNode;",
            "\"bubble-ripple-overlay\"",
            "button.getStyleClass().contains(\"bubble-ripple-overlay\")",
            "if (settings.isChatSendOnEnter()) {",
            "if (settings.isChatAutoScrollToLatest()) {",
            "if (!settings.isMediaOpenPreviewOnImageClick()) {",
            "if (!settings.isPrivacyConfirmAttachmentOpen()) {",
            ".movable(spec.movable())",
            "settings.setMediaShowImageFallbackPopup(false)",
            "shouldAcknowledgeIncomingAsRead",
            "pendingReadAcknowledgement = true;",
            "window.isFocused()",
            "!stage.isIconified()"
    })
    void controller_source_contains_expected_logic_and_behavior_wiring(String requiredCodeSnippet) throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);
        assertTrue(source.contains(requiredCodeSnippet), "Source should contain logic: " + requiredCodeSnippet);
    }

    private static final class StubChatAttachmentService implements ChatAttachmentService {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> recipientId = new AtomicReference<>();
        private final AtomicReference<Path> filePath = new AtomicReference<>();

        @Override
        public void sendAttachment(String recipientId, Path filePath) {
            calls.incrementAndGet();
            this.recipientId.set(recipientId);
            this.filePath.set(filePath);
        }
    }
}
