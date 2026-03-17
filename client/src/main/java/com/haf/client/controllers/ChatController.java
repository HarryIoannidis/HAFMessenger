package com.haf.client.controllers;

import com.haf.client.core.ChatSession;
import com.haf.client.models.MessageVM;
import com.haf.client.services.ChatAttachmentService;
import com.haf.client.services.DefaultChatAttachmentService;
import com.haf.client.utils.MessageBubbleFactory;
import com.haf.client.viewmodels.ChatViewModel;
import com.haf.client.viewmodels.MessageViewModel;
import com.jfoenix.controls.JFXButton;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Objects;

/**
 * Controller for {@code chat.fxml}.
 */
public class ChatController {

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

        if (currentObservedList != null && weakMessageListener != null) {
            currentObservedList.removeListener(weakMessageListener);
        }

        chatBox.getChildren().clear();

        String activeRecipient = viewModel.getRecipientId();

        for (MessageVM vm : viewModel.getActiveMessages()) {
            chatBox.getChildren().add(MessageBubbleFactory.create(vm));
        }

        messageListener = change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    boolean hasIncomingMessages = false;
                    for (MessageVM vm : change.getAddedSubList()) {
                        Node bubble = MessageBubbleFactory.create(vm);
                        chatBox.getChildren().add(bubble);
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
            }
        };

        currentObservedList = viewModel.getActiveMessages();
        weakMessageListener = new WeakListChangeListener<>(messageListener);
        currentObservedList.addListener(weakMessageListener);

        viewModel.acknowledgeActiveRecipient();
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

    private Stage resolveOwnerWindow() {
        if (sendButton != null && sendButton.getScene() != null && sendButton.getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        if (chatBox != null && chatBox.getScene() != null && chatBox.getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return null;
    }
}
