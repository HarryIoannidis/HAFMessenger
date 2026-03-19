package com.haf.client.controllers;

import com.haf.client.core.ChatSession;
import com.haf.client.models.MessageType;
import com.haf.client.models.MessageVM;
import com.haf.client.services.ChatAttachmentService;
import com.haf.client.services.DefaultChatAttachmentService;
import com.haf.client.utils.ContextMenuBuilder;
import com.haf.client.utils.ImageSaveSupport;
import com.haf.client.utils.MessageBubbleFactory;
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

    @FXML
    private ScrollPane chatScrollPane;

    @FXML
    private VBox chatBox;

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

    public ChatController() {
        this(new DefaultChatAttachmentService());
    }

    ChatController(ChatAttachmentService chatAttachmentService) {
        this.chatAttachmentService = Objects.requireNonNull(chatAttachmentService, "chatAttachmentService");
    }

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

    private void removeCurrentListener() {
        if (currentObservedList != null && weakMessageListener != null) {
            currentObservedList.removeListener(weakMessageListener);
        }
    }

    private void loadInitialMessages() {
        for (MessageVM vm : viewModel.getActiveMessages()) {
            chatBox.getChildren().add(createMessageNode(vm));
        }
    }

    private void setupMessageListener() {
        String activeRecipient = viewModel.getRecipientId();
        messageListener = change -> handleMessageChange(change, activeRecipient);
        currentObservedList = viewModel.getActiveMessages();
        weakMessageListener = new WeakListChangeListener<>(messageListener);
        currentObservedList.addListener(weakMessageListener);
    }

    private void handleMessageChange(ListChangeListener.Change<? extends MessageVM> change, String activeRecipient) {
        List<MessageVM> addedMessages = new ArrayList<>();
        boolean requiresFullRefresh = false;

        while (change.next()) {
            if (change.wasAdded() && !change.wasRemoved() && !change.wasPermutated() && !change.wasUpdated()) {
                addedMessages.addAll(change.getAddedSubList());
            } else {
                requiresFullRefresh = true;
            }
        }

        if (requiresFullRefresh) {
            refreshRenderedMessages();
            return;
        }

        if (!addedMessages.isEmpty()) {
            processAddedMessages(addedMessages, activeRecipient);
        }
    }

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

    private void sendMessage() {
        if (viewModel == null)
            return;
        viewModel.sendCurrentDraft();
    }

    private void chooseImageAttachment() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Image");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"));
        selectAndSendAttachment(chooser);
    }

    private void chooseDocumentAttachment() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Document");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.docx", "*.xlsx"));
        selectAndSendAttachment(chooser);
    }

    private void selectAndSendAttachment(FileChooser chooser) {
        if (viewModel == null || !viewModel.isReady()) {
            return;
        }

        File selected = chooser.showOpenDialog(resolveOwnerWindow());
        dispatchSelectedAttachment(viewModel.getRecipientId(), selected);
    }

    void dispatchSelectedAttachment(String recipientId, File selected) {
        if (selected == null || recipientId == null || recipientId.isBlank()) {
            return;
        }
        chatAttachmentService.sendAttachment(recipientId, selected.toPath());
    }

    private Node createMessageNode(MessageVM message) {
        Node node = MessageBubbleFactory.create(message);
        installMessageContextMenu(node, message);
        installImagePrimaryClickPreview(node, message);
        installFilePrimaryClickDownload(node, message);
        return node;
    }

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

    static String resolveSuggestedDownloadFileName(MessageVM message) {
        if (message == null) {
            return ImageSaveSupport.resolveSuggestedFileName(null, null);
        }
        return ImageSaveSupport.resolveSuggestedFileName(message.fileName(), resolveDownloadSourceReference(message));
    }

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

    private void copyMessageText(MessageVM message) {
        if (message == null || message.content() == null || message.content().isBlank()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(message.content());
        Clipboard.getSystemClipboard().setContent(content);
    }

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

    private void downloadImageFromMessage(MessageVM message) {
        if (message == null) {
            return;
        }

        String sourceReference = resolveDownloadSourceReference(message);
        if (sourceReference == null || sourceReference.isBlank()) {
            return;
        }

        Path sourcePath = ImageSaveSupport.resolveLocalSourcePath(sourceReference);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            LOGGER.warning("Attachment source path is unavailable for download.");
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
        }
    }

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

    private Node resolveInteractionTarget(Node messageNode) {
        if (messageNode instanceof HBox row && !row.getChildren().isEmpty()) {
            return row.getChildren().get(0);
        }
        return messageNode;
    }

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
}
