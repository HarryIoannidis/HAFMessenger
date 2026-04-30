package com.haf.client.controllers;

import com.haf.client.builders.ContextMenuBuilder;
import com.haf.client.builders.MessageBubbleFactory;
import com.haf.client.builders.PopupMessageBuilder;
import com.haf.client.core.ChatSession;
import com.haf.client.models.MessageType;
import com.haf.client.models.MessageVM;
import com.haf.client.services.ChatAttachmentService;
import com.haf.client.services.DefaultChatAttachmentService;
import com.haf.client.utils.ClientSettings;
import com.haf.client.utils.ImageSaveSupport;
import com.haf.client.utils.PopupMessageSpec;
import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.ChatViewModel;
import com.haf.client.viewmodels.MessagesViewModel;
import com.jfoenix.controls.JFXButton;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for {@code chat.fxml}.
 */
public class ChatController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatController.class);
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024L * 1024L;
    private static final double MESSAGE_CONTEXT_MENU_DELAY_MS = 170.0;
    private static final double OPEN_CHAT_SCROLL_SETTLE_MS = 180.0;

    enum MessageContextAction {
        COPY,
        PREVIEW,
        DOWNLOAD
    }

    // Message feed
    @FXML
    private ScrollPane chatScrollPane;
    @FXML
    private VBox chatBox;

    // Composer controls
    @FXML
    private TextField messageField;
    @FXML
    private JFXButton sendButton;
    @FXML
    private JFXButton imageButton;
    @FXML
    private JFXButton fileButton;

    private final ChatAttachmentService chatAttachmentService;
    private ChatViewModel viewModel;
    private ClientSettings settings = ClientSettings.defaults();
    private ClientSettings.Listener settingsListener;
    private MessagesViewModel.ImagePreviewFallbackListener imagePreviewFallbackListener;

    private ListChangeListener<MessageVM> messageListenerAnchor;
    private WeakListChangeListener<MessageVM> weakMessageListener;
    private javafx.collections.ObservableList<MessageVM> currentObservedList;
    private PauseTransition chatOpenScrollSettleTransition;

    /**
     * Creates the chat controller with the default attachment service.
     */
    public ChatController() {
        this(new DefaultChatAttachmentService());
    }

    /**
     * Creates the chat controller with an explicit attachment service dependency.
     *
     * @param chatAttachmentService service responsible for attachment sending
     * @throws NullPointerException when {@code chatAttachmentService} is
     *                              {@code null}
     */
    ChatController(ChatAttachmentService chatAttachmentService) {
        this.chatAttachmentService = Objects.requireNonNull(chatAttachmentService, "chatAttachmentService");
    }

    /**
     * Injects active client settings used by chat interactions and rendering.
     *
     * @param settings active settings instance
     */
    public void setSettings(ClientSettings settings) {
        if (this.settingsListener != null) {
            this.settings.removeListener(this.settingsListener);
        }
        this.settings = settings == null ? ClientSettings.defaults() : settings;
        this.settingsListener = key -> {
            if (key == ClientSettings.Key.CHAT_SHOW_MESSAGE_TIMESTAMPS
                    || key == ClientSettings.Key.CHAT_USE_24_HOUR_TIME
                    || key == ClientSettings.Key.CHAT_AUTO_SCROLL_TO_LATEST) {
                Platform.runLater(this::refreshRenderedMessages);
            }
        };
        this.settings.addListener(this.settingsListener);
    }

    /**
     * Initializes chat view bindings and UI action handlers.
     */
    @FXML
    public void initialize() {
        MessagesViewModel messageViewModel = ChatSession.get();
        viewModel = new ChatViewModel(messageViewModel);
        bindImageFallbackListener();
        bindViewModel();

        if (!viewModel.isReady()) {
            return;
        }

        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> {
            if (settings.isChatSendOnEnter()) {
                sendMessage();
            }
        });
        if (imageButton != null) {
            imageButton.setOnAction(e -> chooseImageAttachment());
        }
        if (fileButton != null) {
            fileButton.setOnAction(e -> chooseFileAttachment());
        }
    }

    /**
     * Binds compose field and send-button enablement to the chat view-model state.
     */
    private void bindViewModel() {
        if (messageField != null) {
            messageField.textProperty().bindBidirectional(viewModel.draftTextProperty());
        }
        if (sendButton != null) {
            sendButton.disableProperty().bind(viewModel.canSendProperty().not());
        }
    }

    /**
     * Registers chat-level listener for image payloads that are rendered as files
     * due to preview incompatibility.
     */
    private void bindImageFallbackListener() {
        if (viewModel == null || !viewModel.isReady()) {
            return;
        }
        if (imagePreviewFallbackListener != null) {
            viewModel.removeImagePreviewFallbackListener(imagePreviewFallbackListener);
        }
        imagePreviewFallbackListener = notice -> Platform.runLater(() -> showImageFallbackPopupIfEnabled(notice));
        viewModel.addImagePreviewFallbackListener(imagePreviewFallbackListener);
    }

    /**
     * Shows fallback popup for downgraded image previews when user preference
     * allows it and the affected contact is currently active.
     *
     * @param notice fallback event from message pipeline
     */
    private void showImageFallbackPopupIfEnabled(MessagesViewModel.ImagePreviewFallbackNotice notice) {
        if (notice == null || !settings.isMediaShowImageFallbackPopup()) {
            return;
        }
        if (viewModel == null || !viewModel.isReady()) {
            return;
        }
        String activeRecipient = viewModel.getRecipientId();
        if (activeRecipient == null || activeRecipient.isBlank() || !activeRecipient.equals(notice.contactId())) {
            return;
        }

        PopupMessageSpec spec = buildImageFallbackNoticeSpec(
                notice,
                () -> settings.setMediaShowImageFallbackPopup(false));
        PopupMessageBuilder.create()
                .popupKey(spec.popupKey())
                .title(spec.title())
                .message(spec.message())
                .actionText(spec.actionText())
                .cancelText(spec.cancelText())
                .showCancel(spec.showCancel())
                .movable(spec.movable())
                .onAction(spec.onAction())
                .onCancel(spec.onCancel())
                .show();
    }

    /**
     * Loads the messages for the specified recipient and attaches a listener.
     */
    private void loadMessages() {
        if (viewModel == null || !viewModel.isReady()) {
            return;
        }

        removeCurrentListener();
        chatBox.getChildren().clear();
        loadInitialMessages();
        setupMessageListener();
        viewModel.acknowledgeActiveRecipient();
        scrollToLatestOnChatOpen();
    }

    /**
     * Detaches the listener from the previously observed message list.
     */
    private void removeCurrentListener() {
        if (messageListenerAnchor != null && currentObservedList != null && weakMessageListener != null) {
            currentObservedList.removeListener(weakMessageListener);
        }
        currentObservedList = null;
        weakMessageListener = null;
        messageListenerAnchor = null;
    }

    /**
     * Renders already-existing messages for the active recipient into the chat
     * container.
     */
    private void loadInitialMessages() {
        for (MessageVM vm : viewModel.getActiveMessages()) {
            chatBox.getChildren().add(createMessageNode(vm));
        }
    }

    /**
     * Attaches a weak list-change listener to the active message list.
     */
    private void setupMessageListener() {
        String activeRecipient = viewModel.getRecipientId();
        ListChangeListener<MessageVM> messageListener = change -> handleMessageChange(change, activeRecipient);
        messageListenerAnchor = messageListener;
        currentObservedList = viewModel.getActiveMessages();
        weakMessageListener = new WeakListChangeListener<>(messageListenerAnchor);
        currentObservedList.addListener(weakMessageListener);
    }

    /**
     * Applies incremental UI updates for message-list changes and falls back to
     * full refresh when needed.
     *
     * @param change          list change descriptor from observable messages list
     * @param activeRecipient active recipient id used for acknowledgements
     */
    private void handleMessageChange(ListChangeListener.Change<? extends MessageVM> change, String activeRecipient) {
        MessageChangeState state = new MessageChangeState();
        while (change.next()) {
            applyMessageChangeChunk(change, state);
        }

        if (state.requiresFullRefresh) {
            refreshRenderedMessages();
            return;
        }

        if (state.shouldScrollToLatest) {
            scrollToLatestIfEnabled();
        }
        if (state.hasIncomingMessages) {
            viewModel.acknowledgeRecipient(activeRecipient);
        }
    }

    /**
     * Applies one incremental list-change chunk into the rendered chat view.
     *
     * @param change observable list change chunk
     * @param state  cumulative processing state for current list-change event
     */
    private void applyMessageChangeChunk(
            ListChangeListener.Change<? extends MessageVM> change,
            MessageChangeState state) {
        if (state.requiresFullRefresh || change.wasPermutated() || change.wasUpdated()) {
            state.requiresFullRefresh = true;
            return;
        }
        if (change.wasReplaced()) {
            applyReplaceChunk(change, state);
            return;
        }
        if (change.wasRemoved() && !change.wasAdded()) {
            applyRemovalChunk(change, state);
            return;
        }
        if (change.wasAdded() && !change.wasRemoved()) {
            applyAddChunk(change, state);
            return;
        }
        state.requiresFullRefresh = true;
    }

    /**
     * Applies replacement chunk updates and records UI side-effects.
     *
     * @param change replacement chunk
     * @param state  cumulative state flags
     */
    private void applyReplaceChunk(ListChangeListener.Change<? extends MessageVM> change, MessageChangeState state) {
        if (!applyReplacementChange(change)) {
            state.requiresFullRefresh = true;
            return;
        }
        state.shouldScrollToLatest = true;
        state.hasIncomingMessages = state.hasIncomingMessages || containsIncoming(change.getAddedSubList());
    }

    /**
     * Applies removal chunk updates and records fallback behavior when needed.
     *
     * @param change removal chunk
     * @param state  cumulative state flags
     */
    private void applyRemovalChunk(ListChangeListener.Change<? extends MessageVM> change, MessageChangeState state) {
        if (!applyRemovalChange(change)) {
            state.requiresFullRefresh = true;
        }
    }

    /**
     * Applies add chunk updates and records UI side-effects.
     *
     * @param change add chunk
     * @param state  cumulative state flags
     */
    private void applyAddChunk(ListChangeListener.Change<? extends MessageVM> change, MessageChangeState state) {
        if (!applyAddChange(change)) {
            state.requiresFullRefresh = true;
            return;
        }
        state.shouldScrollToLatest = true;
        state.hasIncomingMessages = state.hasIncomingMessages || containsIncoming(change.getAddedSubList());
    }

    /**
     * Re-renders all messages from the currently observed list and scrolls to
     * bottom.
     */
    private void refreshRenderedMessages() {
        chatBox.getChildren().clear();
        if (currentObservedList != null) {
            for (MessageVM vm : currentObservedList) {
                chatBox.getChildren().add(createMessageNode(vm));
            }
        }
        scrollToLatestIfEnabled();
    }

    /**
     * Applies an add-only list change into the rendered chat node list while
     * preserving insertion order/index.
     *
     * @param change add-only observable list change
     * @return {@code true} when applied successfully; {@code false} when bounds are
     *         inconsistent and caller should fall back to full refresh
     */
    private boolean applyAddChange(ListChangeListener.Change<? extends MessageVM> change) {
        int from = change.getFrom();
        if (from < 0 || from > chatBox.getChildren().size()) {
            return false;
        }
        int insertionIndex = from;
        for (MessageVM vm : change.getAddedSubList()) {
            chatBox.getChildren().add(insertionIndex++, createMessageNode(vm));
        }
        return true;
    }

    /**
     * Applies a remove-only list change into the rendered chat node list.
     *
     * @param change remove-only observable list change
     * @return {@code true} when applied successfully; {@code false} when bounds are
     *         inconsistent and caller should fall back to full refresh
     */
    private boolean applyRemovalChange(ListChangeListener.Change<? extends MessageVM> change) {
        int from = change.getFrom();
        int removedSize = change.getRemovedSize();
        if (removedSize == 0) {
            return true;
        }
        int to = from + removedSize;
        if (from < 0 || to > chatBox.getChildren().size()) {
            return false;
        }
        chatBox.getChildren().remove(from, to);
        return true;
    }

    /**
     * Applies a replacement change (remove+add at index) into the rendered chat
     * node list.
     *
     * @param change replacement observable list change
     * @return {@code true} when applied successfully; {@code false} when bounds are
     *         inconsistent and caller should fall back to full refresh
     */
    private boolean applyReplacementChange(ListChangeListener.Change<? extends MessageVM> change) {
        int from = change.getFrom();
        int removedSize = change.getRemovedSize();
        int to = from + removedSize;
        if (from < 0 || to > chatBox.getChildren().size()) {
            return false;
        }
        chatBox.getChildren().remove(from, to);
        int insertionIndex = from;
        for (MessageVM vm : change.getAddedSubList()) {
            chatBox.getChildren().add(insertionIndex++, createMessageNode(vm));
        }
        return true;
    }

    /**
     * Returns whether a batch of changed messages contains any incoming item.
     *
     * @param changedMessages changed message models
     * @return {@code true} when at least one message is incoming
     */
    private static boolean containsIncoming(List<? extends MessageVM> changedMessages) {
        for (MessageVM vm : changedMessages) {
            if (!vm.isOutgoing()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mutable accumulator for per-event message-change processing.
     */
    private static final class MessageChangeState {
        private boolean shouldScrollToLatest;
        private boolean hasIncomingMessages;
        private boolean requiresFullRefresh;
    }

    /**
     * Forces the viewport to the latest message when opening a chat.
     *
     * This path intentionally ignores the auto-scroll setting so chat-open always
     * lands at the bottom, while incoming/outgoing updates continue using the
     * setting-gated behavior.
     */
    private void scrollToLatestOnChatOpen() {
        if (chatScrollPane == null) {
            return;
        }
        scrollToLatestNow();
        Platform.runLater(() -> {
            scrollToLatestNow();
            Platform.runLater(this::scrollToLatestNow);
        });

        if (chatOpenScrollSettleTransition != null) {
            chatOpenScrollSettleTransition.stop();
        }
        chatOpenScrollSettleTransition = new PauseTransition(Duration.millis(OPEN_CHAT_SCROLL_SETTLE_MS));
        chatOpenScrollSettleTransition.setOnFinished(event -> scrollToLatestNow());
        chatOpenScrollSettleTransition.playFromStart();
    }

    /**
     * Scrolls chat viewport to latest message and repeats across two additional UI
     * pulses to handle delayed layout/scene attachment.
     */
    private void scrollToLatestIfEnabled() {
        if (chatScrollPane == null) {
            return;
        }
        if (settings.isChatAutoScrollToLatest()) {
            scrollToLatestNow();
            Platform.runLater(() -> {
                scrollToLatestNow();
                Platform.runLater(this::scrollToLatestNow);
            });
        }
    }

    /**
     * Applies CSS/layout and moves the chat viewport to the bottom.
     */
    private void scrollToLatestNow() {
        if (chatScrollPane == null) {
            return;
        }
        chatScrollPane.applyCss();
        chatScrollPane.layout();
        chatScrollPane.setVvalue(1.0);
    }

    /**
     * Called by {@link MainController} after loading this FXML.
     */
    public void setRecipient(String recipientId) {
        if (viewModel == null) {
            return;
        }
        viewModel.setRecipient(recipientId);
        loadMessages();
    }

    /**
     * Sends the current draft text using the view-model.
     */
    private void sendMessage() {
        if (viewModel == null)
            return;
        viewModel.sendCurrentDraft();
    }

    /**
     * Opens file chooser restricted to supported image extensions and sends
     * selected file.
     */
    private void chooseImageAttachment() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Image");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        selectAndSendAttachment(chooser);
    }

    /**
     * Opens file chooser for any attachment type and sends the selected file.
     */
    private void chooseFileAttachment() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("Documents",
                        "*.pdf", "*.doc", "*.docx", "*.odt", "*.rtf", "*.xls", "*.xlsx", "*.ods", "*.csv",
                        "*.ppt", "*.pptx", "*.odp"),
                new FileChooser.ExtensionFilter("Images",
                        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.svg", "*.webp", "*.tif", "*.tiff"),
                new FileChooser.ExtensionFilter("Audio",
                        "*.mp3", "*.wav", "*.ogg", "*.flac", "*.aac", "*.m4a"),
                new FileChooser.ExtensionFilter("Video",
                        "*.mp4", "*.mov", "*.avi", "*.mkv", "*.webm", "*.mpeg", "*.mpg"),
                new FileChooser.ExtensionFilter("Archives",
                        "*.zip", "*.rar", "*.7z", "*.tar", "*.gz", "*.tgz", "*.bz2", "*.xz"),
                new FileChooser.ExtensionFilter("Code and Text",
                        "*.xml", "*.html", "*.htm", "*.json", "*.java", "*.js", "*.jsx", "*.ts", "*.tsx", "*.css",
                        "*.scss", "*.sql", "*.py", "*.sh", "*.txt", "*.md", "*.log"),
                new FileChooser.ExtensionFilter("Applications",
                        "*.exe", "*.msi", "*.dmg", "*.app", "*.apk", "*.deb", "*.rpm", "*.bat", "*.cmd"));
        selectAndSendAttachment(chooser);
    }

    /**
     * Opens chooser and dispatches selected attachment for the active recipient.
     *
     * @param chooser preconfigured chooser instance
     */
    private void selectAndSendAttachment(FileChooser chooser) {
        if (viewModel == null || !viewModel.isReady()) {
            return;
        }

        File selected = chooser.showOpenDialog(resolveOwnerWindow());
        validateAndDispatchSelectedAttachment(viewModel.getRecipientId(), chooser, selected);
    }

    /**
     * Validates selected attachment size and either dispatches it or prompts the
     * user
     * to pick another file.
     *
     * @param recipientId target recipient id
     * @param chooser     chooser instance used to re-open selection flow
     * @param selected    selected file candidate
     */
    private void validateAndDispatchSelectedAttachment(String recipientId, FileChooser chooser, File selected) {
        if (selected == null || recipientId == null || recipientId.isBlank()) {
            return;
        }

        Path selectedPath = selected.toPath();
        if (isOverAttachmentLimit(selectedPath)) {
            showAttachmentTooLargeError(selectedPath, recipientId, chooser);
            return;
        }

        dispatchSelectedAttachment(recipientId, selected);
    }

    /**
     * Checks whether a file exceeds the 10MB attachment limit.
     *
     * @param filePath candidate attachment path
     * @return {@code true} when file size is greater than 10MB
     */
    static boolean isOverAttachmentLimit(Path filePath) {
        if (filePath == null) {
            return false;
        }
        try {
            return Files.size(filePath) > MAX_ATTACHMENT_BYTES;
        } catch (Exception ex) {
            LOGGER.warn("Could not read attachment size", ex);
            return false;
        }
    }

    /**
     * Displays a two-action popup for oversize attachments and lets the user pick a
     * new file immediately.
     *
     * @param selectedPath selected oversize file path
     * @param recipientId  active recipient id
     * @param chooser      chooser used for file selection
     */
    private void showAttachmentTooLargeError(Path selectedPath, String recipientId, FileChooser chooser) {
        PopupMessageSpec spec = buildAttachmentTooLargeSpec(selectedPath, () -> {
            File retrySelection = chooser.showOpenDialog(resolveOwnerWindow());
            validateAndDispatchSelectedAttachment(recipientId, chooser, retrySelection);
        });

        PopupMessageBuilder.create()
                .popupKey(spec.popupKey())
                .title(spec.title())
                .message(spec.message())
                .actionText(spec.actionText())
                .cancelText(spec.cancelText())
                .showCancel(spec.showCancel())
                .movable(spec.movable())
                .onAction(spec.onAction())
                .onCancel(spec.onCancel())
                .show();
    }

    /**
     * Builds popup specification for oversize image/file selection.
     *
     * @param selectedPath  selected path used to determine "image" vs "file"
     * @param onPickAnother callback executed when user chooses "Pick Another"
     * @return popup configuration object
     */
    static PopupMessageSpec buildAttachmentTooLargeSpec(Path selectedPath, Runnable onPickAnother) {
        String targetType = isLikelyImage(selectedPath) ? "image" : "file";
        String message = "The " + targetType + " you are trying to send is over 10MB.";
        return new PopupMessageSpec(
                UiConstants.POPUP_ATTACHMENT_ERROR,
                "Attachment too large",
                message,
                "Pick Another",
                "Cancel",
                true,
                false,
                true,
                onPickAnother,
                null);
    }

    /**
     * Builds popup specification describing why an image-like attachment was shown
     * as a file tile.
     *
     * @param notice          fallback notice emitted by the messaging pipeline
     * @param onDontShowAgain callback invoked when user opts out of future popups
     * @return popup configuration object
     */
    static PopupMessageSpec buildImageFallbackNoticeSpec(
            MessagesViewModel.ImagePreviewFallbackNotice notice,
            Runnable onDontShowAgain) {
        return new PopupMessageSpec(
                UiConstants.POPUP_IMAGE_FALLBACK_NOTICE,
                "Image sent as file",
                buildImageFallbackMessage(notice),
                "Don't show again",
                "Close",
                true,
                false,
                true,
                onDontShowAgain,
                null);
    }

    /**
     * Creates user-facing explanation for why in-app image preview was downgraded.
     *
     * @param notice fallback notice describing declared and detected payload
     *               formats
     * @return popup body message
     */
    static String buildImageFallbackMessage(MessagesViewModel.ImagePreviewFallbackNotice notice) {
        if (notice == null) {
            return "This image could not be previewed in-app, so it was sent as a downloadable file.";
        }

        String fileName = notice.fileName();
        String fileTarget = (fileName == null || fileName.isBlank()) ? "This image" : "\"" + fileName + "\"";
        String declaredMedia = notice.declaredMediaType() == null ? "" : notice.declaredMediaType().trim();
        String detectedSignature = notice.detectedPayloadSignature() == null
                ? "unknown"
                : notice.detectedPayloadSignature().trim().toLowerCase(Locale.ROOT);
        String expectedSignature = resolveExpectedSignature(declaredMedia);

        if ("image/webp".equals(declaredMedia)) {
            return fileTarget + " was sent as a file because WEBP preview is not supported in this build.";
        }
        if (expectedSignature != null && !"unknown".equals(detectedSignature)
                && !expectedSignature.equals(detectedSignature)) {
            return fileTarget + " was sent as a file because it was labeled as "
                    + formatSignatureLabel(expectedSignature) + " but contains "
                    + formatSignatureLabel(detectedSignature) + " data.";
        }
        if ("unknown".equals(detectedSignature)) {
            return fileTarget + " was sent as a file because its internal image format could not be recognized.";
        }
        return fileTarget + " was sent as a file because "
                + formatSignatureLabel(detectedSignature)
                + " preview is not supported in this build.";
    }

    /**
     * Maps declared MIME type to expected image signature shorthand.
     *
     * @param declaredMediaType normalized MIME type
     * @return expected signature token, or {@code null} when unknown
     */
    private static String resolveExpectedSignature(String declaredMediaType) {
        if (declaredMediaType == null || declaredMediaType.isBlank()) {
            return null;
        }
        return switch (declaredMediaType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpeg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> null;
        };
    }

    /**
     * Formats image signature token for user-facing copy.
     *
     * @param signature signature token
     * @return printable label
     */
    private static String formatSignatureLabel(String signature) {
        if (signature == null || signature.isBlank()) {
            return "image";
        }
        return signature.toUpperCase(Locale.ROOT);
    }

    /**
     * Infers whether selected file path likely points to an image by extension.
     *
     * @param path file path candidate
     * @return {@code true} for common image extensions
     */
    private static boolean isLikelyImage(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String lower = path.getFileName().toString().toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp");
    }

    /**
     * Sends selected attachment file to recipient through attachment service.
     *
     * @param recipientId target recipient id
     * @param selected    selected file
     */
    void dispatchSelectedAttachment(String recipientId, File selected) {
        if (selected == null || recipientId == null || recipientId.isBlank()) {
            return;
        }
        chatAttachmentService.sendAttachment(recipientId, selected.toPath());
    }

    /**
     * Builds a message bubble node and installs interaction handlers.
     *
     * @param message message model to render
     * @return rendered node for the message
     */
    private Node createMessageNode(MessageVM message) {
        Node node = MessageBubbleFactory.create(
                message,
                settings.isChatShowMessageTimestamps(),
                settings.isChatUse24HourTime());
        installMessageContextMenu(node, message);
        installImagePrimaryClickPreview(node, message);
        installFilePrimaryClickDownload(node, message);
        return node;
    }

    /**
     * Resolves context-menu actions available for a message based on type,
     * direction, and available data.
     *
     * @param message message candidate
     * @return immutable list of actions to expose
     */
    static List<MessageContextAction> resolveContextActions(MessageVM message) {
        if (message == null) {
            return List.of();
        }

        if (message.type() == MessageType.TEXT) {
            if (message.content() != null && !message.content().isBlank()) {
                return List.of(MessageContextAction.COPY);
            }
            return List.of();
        }

        if (message.type() == MessageType.IMAGE) {
            if (message.content() == null || message.content().isBlank()) {
                return List.of();
            }
            if (message.isOutgoing()) {
                return List.of(MessageContextAction.PREVIEW);
            }
            return List.of(MessageContextAction.PREVIEW, MessageContextAction.DOWNLOAD);
        }

        if (message.type() == MessageType.FILE) {
            String source = resolveDownloadSourceReference(message);
            if (source != null && !source.isBlank()) {
                return List.of(MessageContextAction.DOWNLOAD);
            }
        }

        return List.of();
    }

    /**
     * Resolves suggested filename for saving/downloading attachment messages.
     *
     * @param message message carrying attachment metadata
     * @return suggested filename
     */
    static String resolveSuggestedDownloadFileName(MessageVM message) {
        if (message == null) {
            return ImageSaveSupport.resolveSuggestedFileName(null, null);
        }
        return ImageSaveSupport.resolveSuggestedFileName(message.fileName(), resolveDownloadSourceReference(message));
    }

    /**
     * Resolves local source reference used for download/preview depending on
     * message type.
     *
     * @param message message candidate
     * @return content/local-path source reference, or {@code null} when unavailable
     */
    static String resolveDownloadSourceReference(MessageVM message) {
        if (message == null) {
            return null;
        }
        if (message.type() == MessageType.IMAGE) {
            return message.content();
        }
        if (message.type() == MessageType.FILE) {
            return message.localPath();
        }
        return null;
    }

    /**
     * Installs right-click context menu on the message interaction target.
     *
     * @param messageNode rendered message node
     * @param message     message model backing the node
     */
    private void installMessageContextMenu(Node messageNode, MessageVM message) {
        Node interactionTarget = resolveInteractionTarget(messageNode);
        List<MessageContextAction> actions = resolveContextActions(message);
        if (interactionTarget == null || actions.isEmpty()) {
            return;
        }

        ContextMenu menu = buildMessageContextMenu(message, actions);
        final PauseTransition[] contextMenuDelay = new PauseTransition[1];
        interactionTarget.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                event -> handleContextMenuRequested(event, interactionTarget, menu, contextMenuDelay));
        interactionTarget.addEventFilter(MouseEvent.MOUSE_PRESSED,
                event -> handleMousePressedForContextMenu(event, menu, contextMenuDelay));
    }

    /**
     * Handles context-menu-requested events by firing the interaction target,
     * then scheduling a delayed context menu display.
     *
     * @param event             context menu event
     * @param interactionTarget node that owns the context menu
     * @param menu              context menu to show
     * @param contextMenuDelay  shared delay holder for cancellation support
     */
    private void handleContextMenuRequested(ContextMenuEvent event, Node interactionTarget,
            ContextMenu menu, PauseTransition[] contextMenuDelay) {
        final double screenX = event.getScreenX();
        final double screenY = event.getScreenY();
        if (menu.isShowing()) {
            menu.hide();
        }
        if (interactionTarget instanceof JFXButton button) {
            button.fire();
        }
        if (contextMenuDelay[0] != null) {
            contextMenuDelay[0].stop();
        }
        contextMenuDelay[0] = new PauseTransition(Duration.millis(MESSAGE_CONTEXT_MENU_DELAY_MS));
        contextMenuDelay[0].setOnFinished(ignored -> {
            if (interactionTarget.getScene() != null) {
                menu.show(interactionTarget, screenX, screenY);
            }
        });
        contextMenuDelay[0].playFromStart();
        event.consume();
    }

    /**
     * Handles mouse-pressed events by cancelling any pending context-menu delay
     * and hiding the menu on non-secondary clicks.
     *
     * @param event            mouse event
     * @param menu             context menu to dismiss
     * @param contextMenuDelay shared delay holder for cancellation support
     */
    private void handleMousePressedForContextMenu(MouseEvent event, ContextMenu menu,
            PauseTransition[] contextMenuDelay) {
        if (contextMenuDelay[0] != null) {
            contextMenuDelay[0].stop();
        }
        if (menu.isShowing() && event.getButton() != MouseButton.SECONDARY) {
            menu.hide();
        }
    }

    /**
     * Builds context menu entries for the provided message actions.
     *
     * @param message message bound to actions
     * @param actions actions to include
     * @return configured context menu
     */
    private ContextMenu buildMessageContextMenu(MessageVM message, List<MessageContextAction> actions) {
        ContextMenuBuilder builder = ContextMenuBuilder.create();
        for (MessageContextAction action : actions) {
            switch (action) {
                case COPY -> builder.addOption(
                        "mdi2c-content-copy",
                        "Copy",
                        () -> copyMessageText(message));
                case PREVIEW -> builder.addOption(
                        "mdi2i-image-outline",
                        "Preview",
                        () -> openImagePreview(message));
                case DOWNLOAD -> builder.addOption(
                        "mdi2d-download",
                        "Download",
                        () -> downloadImageFromMessage(message));
            }
        }
        ContextMenu menu = builder.build();
        menu.setAutoHide(true);
        return menu;
    }

    /**
     * Copies text message content to system clipboard.
     *
     * @param message text message to copy
     */
    private void copyMessageText(MessageVM message) {
        if (message == null || message.content() == null || message.content().isBlank()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(message.content());
        Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Opens the image preview popup for an image message.
     *
     * @param message image message to preview
     */
    private void openImagePreview(MessageVM message) {
        runWithAttachmentOpenConfirmation(
                "Open preview",
                "Open this attachment preview?",
                () -> openImagePreviewNow(message));
    }

    /**
     * Opens the preview popup immediately after confirmation has been handled.
     *
     * @param message image message to render in preview
     */
    private void openImagePreviewNow(MessageVM message) {
        if (message == null || message.content() == null || message.content().isBlank()) {
            return;
        }
        String suggestedName = resolveSuggestedDownloadFileName(message);
        boolean downloadAllowed = isImageDownloadAvailable(message);
        ViewRouter.showPopup(
                "message-image-preview-popup",
                UiConstants.FXML_PREVIEW,
                PreviewController.class,
                controller -> {
                    controller.setSettings(settings);
                    controller.showImage(message.content(), suggestedName, downloadAllowed);
                });
    }

    /**
     * Checks whether the message can be downloaded as an image from an existing
     * local source path.
     *
     * @param message message candidate
     * @return {@code true} when download source exists locally
     */
    private boolean isImageDownloadAvailable(MessageVM message) {
        if (message == null || message.type() != MessageType.IMAGE) {
            return false;
        }
        String sourceReference = resolveDownloadSourceReference(message);
        if (sourceReference == null || sourceReference.isBlank()) {
            return false;
        }
        Path sourcePath = ImageSaveSupport.resolveLocalSourcePath(sourceReference);
        return sourcePath != null && Files.exists(sourcePath);
    }

    /**
     * Prompts user for destination and copies message attachment/image to disk.
     *
     * @param message message containing downloadable source
     */
    private void downloadImageFromMessage(MessageVM message) {
        runWithAttachmentOpenConfirmation(
                "Open attachment",
                "Open or download this attachment?",
                () -> downloadImageFromMessageNow(message));
    }

    /**
     * Performs the attachment/image save flow immediately after confirmation has
     * been handled.
     *
     * @param message message containing downloadable source metadata
     */
    private void downloadImageFromMessageNow(MessageVM message) {
        if (message == null) {
            return;
        }

        String sourceReference = resolveDownloadSourceReference(message);
        if (sourceReference == null || sourceReference.isBlank()) {
            showAttachmentError("Attachment source is unavailable.");
            return;
        }

        Path sourcePath = ImageSaveSupport.resolveLocalSourcePath(sourceReference);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            LOGGER.warn("Attachment source path is unavailable for download.");
            showAttachmentError("Attachment source file could not be found.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save " + (message.type() == MessageType.FILE ? "File" : "Image"));
        String suggestedFileName = resolveSuggestedDownloadFileName(message);
        if (message.type() == MessageType.IMAGE) {
            ImageSaveSupport.configureImageSaveChooser(chooser, suggestedFileName, sourceReference);
        } else {
            ImageSaveSupport.configureSaveChooser(chooser, suggestedFileName, sourceReference);
        }
        File selected = chooser.showSaveDialog(resolveOwnerWindow());
        if (selected == null) {
            return;
        }

        try {
            Path destination = selected.toPath();
            if (sourcePath.equals(destination)) {
                return;
            }
            Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            LOGGER.warn("Failed to save image download", ex);
            showAttachmentError("Could not save attachment. Please try again.");
        }
    }

    /**
     * Runs an attachment action and optionally gates it behind a privacy
     * confirmation popup.
     *
     * @param title   popup title when confirmation is enabled
     * @param message popup body when confirmation is enabled
     * @param action  action to run when confirmed
     */
    private void runWithAttachmentOpenConfirmation(String title, String message, Runnable action) {
        if (action == null) {
            return;
        }
        if (!settings.isPrivacyConfirmAttachmentOpen()) {
            action.run();
            return;
        }
        PopupMessageBuilder.create()
                .popupKey("popup-confirm-attachment-open")
                .title(title)
                .message(message)
                .actionText("Continue")
                .cancelText("Cancel")
                .showCancel(true)
                .onAction(action)
                .show();
    }

    /**
     * Installs primary-click handler for file messages to trigger download.
     *
     * @param messageNode rendered message node
     * @param message     backing file message
     */
    private void installFilePrimaryClickDownload(Node messageNode, MessageVM message) {
        Node interactionTarget = resolveInteractionTarget(messageNode);
        if (interactionTarget == null || message == null || message.type() != MessageType.FILE) {
            return;
        }
        if (message.isLoading()) {
            return;
        }
        String sourceReference = resolveDownloadSourceReference(message);
        if (sourceReference == null || sourceReference.isBlank()) {
            return;
        }

        interactionTarget.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1) {
                return;
            }
            downloadImageFromMessage(message);
            event.consume();
        });
    }

    /**
     * Installs primary-click handler for image messages to open preview.
     *
     * @param messageNode rendered message node
     * @param message     backing image message
     */
    private void installImagePrimaryClickPreview(Node messageNode, MessageVM message) {
        Node interactionTarget = resolveInteractionTarget(messageNode);
        if (interactionTarget == null || message == null || message.type() != MessageType.IMAGE) {
            return;
        }
        if (message.content() == null || message.content().isBlank()) {
            return;
        }

        interactionTarget.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1) {
                return;
            }
            if (!settings.isMediaOpenPreviewOnImageClick()) {
                return;
            }
            openImagePreview(message);
            event.consume();
        });
    }

    /**
     * Resolves best interaction target node for event filters (bubble content for
     * row wrappers).
     *
     * @param messageNode rendered message node
     * @return interaction target node
     */
    static Node resolveInteractionTarget(Node messageNode) {
        if (messageNode instanceof HBox row && !row.getChildren().isEmpty()) {
            Node firstChild = row.getChildren().get(0);
            if (firstChild instanceof StackPane stackPane) {
                Node rippleOverlay = findRippleOverlay(stackPane);
                if (rippleOverlay != null) {
                    return rippleOverlay;
                }
            }
            return firstChild;
        }
        return messageNode;
    }

    /**
     * Finds the message ripple overlay button inside a stack bubble wrapper.
     *
     * @param stackPane bubble wrapper node
     * @return overlay button when present; otherwise {@code null}
     */
    private static Node findRippleOverlay(StackPane stackPane) {
        for (Node child : stackPane.getChildren()) {
            if (child instanceof JFXButton button
                    && button.getStyleClass().contains("bubble-ripple-overlay")) {
                return button;
            }
        }
        return null;
    }

    /**
     * Resolves owner stage for file chooser dialogs.
     *
     * @return owner stage, or {@code null} when none can be derived
     */
    private Stage resolveOwnerWindow() {
        if (sendButton != null && sendButton.getScene() != null
                && sendButton.getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        if (chatBox != null && chatBox.getScene() != null && chatBox.getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return null;
    }

    /**
     * Displays a standardized attachment error popup.
     *
     * @param message error message body
     */
    private void showAttachmentError(String message) {
        PopupMessageSpec spec = buildAttachmentErrorSpec(message);
        PopupMessageBuilder.create()
                .popupKey(spec.popupKey())
                .title(spec.title())
                .message(spec.message())
                .actionText(spec.actionText())
                .singleAction(true)
                .movable(spec.movable())
                .show();
    }

    /**
     * Builds popup specification for attachment-related error messages.
     *
     * @param message raw message text
     * @return popup configuration object
     */
    static PopupMessageSpec buildAttachmentErrorSpec(String message) {
        String resolved = message == null || message.isBlank() ? "Attachment operation failed." : message;
        return new PopupMessageSpec(
                UiConstants.POPUP_ATTACHMENT_ERROR,
                "Attachment error",
                resolved,
                "OK",
                "Cancel",
                false,
                false,
                true,
                null,
                null);
    }
}
