package com.haf.client.controllers;

import com.haf.client.core.ChatSession;
import com.haf.client.models.MessageVM;
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

/**
 * Controller for {@code chat.fxml}.
 *
 * 
 * Loaded dynamically by {@link MainController} each time a contact is
 * selected. It binds the bubble list to the {@link MessageViewModel} held in
 * {@link ChatSession} and wires the send controls.
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

    private ChatViewModel viewModel;

    // We hold a strong reference to the listener to avoid early garbage collection
    // while the WeakListChangeListener wrapper is active.
    private ListChangeListener<MessageVM> messageListener;
    private WeakListChangeListener<MessageVM> weakMessageListener;
    private javafx.collections.ObservableList<MessageVM> currentObservedList;

    @FXML
    public void initialize() {
        MessageViewModel messageViewModel = ChatSession.get();
        viewModel = new ChatViewModel(messageViewModel);
        bindViewModel();

        if (!viewModel.isReady()) {
            // No session yet — UI stays enabled; sendMessage() guards the null.
            return;
        }

        // Wire send button.
        sendButton.setOnAction(e -> sendMessage());

        // Wire Enter key on message field.
        messageField.setOnAction(e -> sendMessage());
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

        // Remove old listener from the previous contact's list
        if (currentObservedList != null && weakMessageListener != null) {
            currentObservedList.removeListener(weakMessageListener);
        }

        // Clear existing messages
        chatBox.getChildren().clear();

        String activeRecipient = viewModel.getRecipientId();

        // Populate existing messages for this contact
        for (MessageVM vm : viewModel.getActiveMessages()) {
            chatBox.getChildren().add(MessageBubbleFactory.create(vm));
        }

        // Create a new listener
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
                    // Auto-scroll to the newest bubble.
                    chatScrollPane.layout();
                    chatScrollPane.setVvalue(1.0);

                    // User is currently viewing this chat, so newly arrived incoming
                    // envelopes can be acknowledged immediately.
                    if (hasIncomingMessages) {
                        viewModel.acknowledgeRecipient(activeRecipient);
                    }
                }
            }
        };

        // Track and attach via WeakListChangeListener
        currentObservedList = viewModel.getActiveMessages();
        weakMessageListener = new WeakListChangeListener<>(messageListener);
        currentObservedList.addListener(weakMessageListener);

        // ACK all pending envelopes from this sender now that the user is viewing the chat.
        viewModel.acknowledgeActiveRecipient();
    }

    /**
     * Called by {@link MainController} immediately after loading this FXML so the
     * controller knows who the messages are addressed to.
     *
     * @param recipientId the contact's unique identifier
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
}
