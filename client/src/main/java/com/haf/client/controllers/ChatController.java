package com.haf.client.controllers;

import com.haf.client.core.ChatSession;
import com.haf.client.models.MessageType;
import com.haf.client.models.MessageVM;
import com.haf.client.services.ChatAttachmentService;
import com.haf.client.services.DefaultChatAttachmentService;
import com.haf.client.utils.ContextMenuBuilder;
import com.haf.client.utils.ImageSaveSupport;
import com.haf.client.utils.MessageBubbleFactory;
import com.haf.client.utils.PopupMessageBuilder;
import com.haf.client.utils.PopupMessageSpec;
import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.ChatViewModel;
import com.haf.client.viewmodels.MessageViewModel;
import com.jfoenix.controls.JFXButton;
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
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for {@code chat.fxml}.
 */
public class ChatController {

    private static final Logger LOGGER = Logger.getLogger(ChatController.class.getName());
    private static final String PREVIEW_POPUP_KEY = "message-image-preview-popup";

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

    private ListChangeListener<MessageVM> messageListener;
    private WeakListChangeListener<MessageVM> weakMessageListener;
    private javafx.collections.ObservableList<MessageVM> currentObservedList;

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
     * @throws NullPointerException when {@code chatAttachmentService} is {@code null}
     */
    ChatController(ChatAttachmentService chatAttachmentService) {
        this.chatAttachmentService = Objects.requireNonNull(chatAttachmentService, "chatAttachmentService");
    }

    /**
     * Initializes chat view bindings and UI action handlers.
     */
    @FXML
    public void initialize() {
        MessageViewModel messageViewModel = ChatSession.get();
        viewModel = new ChatViewModel(messageViewModel);
        bindViewModel();

        if (!viewModel.isReady()) {
            return;
        }

        sendButton.setOnAction(e -> sendMessage());
        messageField.setOnAction(e -> sendMessage());
        if (imageButton != null) {
            imageButton.setOnAction(e -> chooseImageAttachment());
        }
        if (fileButton != null) {
            fileButton.setOnAction(e -> chooseDocumentAttachment());
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
    }

    /**
     * Detaches the listener from the previously observed message list.
     */
    private void removeCurrentListener() {
        if (currentObservedList != null && weakMessageListener != null) {
            currentObservedList.removeListener(weakMessageListener);
        }
    }

    /**
     * Renders already-existing messages for the active recipient into the chat container.
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
        messageListener = change -> handleMessageChange(change, activeRecipient);
        currentObservedList = viewModel.getActiveMessages();
        weakMessageListener = new WeakListChangeListener<>(messageListener);
        currentObservedList.addListener(weakMessageListener);
    }

    /**
     * Applies incremental UI updates for message-list changes and falls back to full refresh when needed.
     *
     * @param change list change descriptor from observable messages list
     * @param activeRecipient active recipient id used for acknowledgements
     */
    private void handleMessageChange(ListChangeListener.Change<? extends MessageVM> change, String activeRecipient) {
        List<MessageVM> addedMessages = null;
        boolean requiresFullRefresh = false;

        while (change.next()) {
            if (change.wasAdded() && !change.wasRemoved() && !change.wasPermutated() && !change.wasUpdated()) {
                if (addedMessages == null) {
                    addedMessages = new ArrayList<>();
                }
                addedMessages.addAll(change.getAddedSubList());
            } else {
                requiresFullRefresh = true;
            }
        }

        if (requiresFullRefresh) {
            refreshRenderedMessages();
            return;
        }

        if (addedMessages != null && !addedMessages.isEmpty()) {
            processAddedMessages(addedMessages, activeRecipient);
        }
    }

    /**
     * Re-renders all messages from the currently observed list and scrolls to bottom.
     */
    private void refreshRenderedMessages() {
        chatBox.getChildren().clear();
        if (currentObservedList != null) {
            for (MessageVM vm : currentObservedList) {
                chatBox.getChildren().add(createMessageNode(vm));
            }
        }
        chatScrollPane.layout();
        chatScrollPane.setVvalue(1.0);
    }

    /**
     * Renders newly added messages and acknowledges inbound messages for active recipient.
     *
     * @param addedMessages newly appended messages
     * @param activeRecipient active recipient id used for ack
     */
    private void processAddedMessages(List<? extends MessageVM> addedMessages, String activeRecipient) {
        boolean hasIncomingMessages = false;
        for (MessageVM vm : addedMessages) {
            chatBox.getChildren().add(createMessageNode(vm));
            if (!vm.isOutgoing()) {
                hasIncomingMessages = true;
            }
        }
        chatScrollPane.layout();
        chatScrollPane.setVvalue(1.0);

        if (hasIncomingMessages) {
            viewModel.acknowledgeRecipient(activeRecipient);
        }
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
     * Opens file chooser restricted to supported image extensions and sends selected file.
     */
    private void chooseImageAttachment() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Image");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        selectAndSendAttachment(chooser);
    }

    /**
     * Opens file chooser restricted to supported document extensions and sends selected file.
     */
    private void chooseDocumentAttachment() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Document");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.docx", "*.xlsx"));
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
        dispatchSelectedAttachment(viewModel.getRecipientId(), selected);
    }

    /**
     * Sends selected attachment file to recipient through attachment service.
     *
     * @param recipientId target recipient id
     * @param selected selected file
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
        Node node = MessageBubbleFactory.create(message);
        installMessageContextMenu(node, message);
        installImagePrimaryClickPreview(node, message);
        installFilePrimaryClickDownload(node, message);
        return node;
    }

    /**
     * Resolves context-menu actions available for a message based on type, direction, and available data.
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
     * Resolves local source reference used for download/preview depending on message type.
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
     * @param message message model backing the node
     */
    private void installMessageContextMenu(Node messageNode, MessageVM message) {
        Node interactionTarget = resolveInteractionTarget(messageNode);
        List<MessageContextAction> actions = resolveContextActions(message);
        if (interactionTarget == null || actions.isEmpty()) {
            return;
        }

        ContextMenu menu = buildMessageContextMenu(message, actions);
        interactionTarget.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            if (menu.isShowing()) {
                menu.hide();
            }
            menu.show(interactionTarget, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        interactionTarget.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (menu.isShowing() && event.getButton() != MouseButton.SECONDARY) {
                menu.hide();
            }
        });
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
        if (message == null || message.content() == null || message.content().isBlank()) {
            return;
        }
        String suggestedName = resolveSuggestedDownloadFileName(message);
        boolean downloadAllowed = isImageDownloadAvailable(message);
        ViewRouter.showPopup(
                PREVIEW_POPUP_KEY,
                UiConstants.FXML_PREVIEW,
                PreviewController.class,
                controller -> controller.showImage(message.content(), suggestedName, downloadAllowed));
    }

    /**
     * Checks whether the message can be downloaded as an image from an existing local source path.
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
            LOGGER.warning("Attachment source path is unavailable for download.");
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
            LOGGER.log(Level.WARNING, "Failed to save image download", ex);
            showAttachmentError("Could not save attachment. Please try again.");
        }
    }

    /**
     * Installs primary-click handler for file messages to trigger download.
     *
     * @param messageNode rendered message node
     * @param message backing file message
     */
    private void installFilePrimaryClickDownload(Node messageNode, MessageVM message) {
        Node interactionTarget = resolveInteractionTarget(messageNode);
        if (interactionTarget == null || message == null || message.type() != MessageType.FILE) {
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
     * @param message backing image message
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
            openImagePreview(message);
            event.consume();
        });
    }

    /**
     * Resolves best interaction target node for event filters (bubble content for row wrappers).
     *
     * @param messageNode rendered message node
     * @return interaction target node
     */
    private Node resolveInteractionTarget(Node messageNode) {
        if (messageNode instanceof HBox row && !row.getChildren().isEmpty()) {
            return row.getChildren().get(0);
        }
        return messageNode;
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
                null,
                null);
    }
}
