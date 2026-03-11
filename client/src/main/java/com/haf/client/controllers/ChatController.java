package com.haf.client.controllers;

import com.haf.client.core.ChatSession;
import com.haf.client.models.MessageVM;
import com.haf.client.utils.MessageBubbleFactory;
import com.haf.client.viewmodels.MessageViewModel;
import com.jfoenix.controls.JFXButton;
import javafx.collections.ListChangeListener;
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

    /** The ID of the contact currently being chatted with. */
    private String recipientId = "";

    private MessageViewModel viewModel;

    // We hold a strong reference to the listener to avoid early garbage collection
    // while the WeakListChangeListener wrapper is active.
    private ListChangeListener<MessageVM> messageListener;

    @FXML
    public void initialize() {
        viewModel = ChatSession.get();

        if (viewModel == null) {
            // No session yet — UI stays enabled; sendMessage() guards the null.
            return;
        }

        // Wire send button.
        sendButton.setOnAction(e -> sendMessage());

        // Wire Enter key on message field.
        messageField.setOnAction(e -> sendMessage());
    }

    /**
     * Loads the messages for the specified recipient and attaches a listener.
     */
    private void loadMessages() {
        if (viewModel == null) {
            return;
        }

        // Clear existing messages
        chatBox.getChildren().clear();

        // Populate existing messages for this contact
        for (MessageVM vm : viewModel.getMessages(recipientId)) {
            chatBox.getChildren().add(MessageBubbleFactory.create(vm));
        }

        // Create a new listener
        messageListener = change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (MessageVM vm : change.getAddedSubList()) {
                        Node bubble = MessageBubbleFactory.create(vm);
                        chatBox.getChildren().add(bubble);
                    }
                    // Auto-scroll to the newest bubble.
                    chatScrollPane.layout();
                    chatScrollPane.setVvalue(1.0);
                }
            }
        };

        // Attach via WeakListChangeListener to prevent leaking this controller
        viewModel.getMessages(recipientId).addListener(messageListener);
    }

    /**
     * Called by {@link MainController} immediately after loading this FXML so the
     * controller knows who the messages are addressed to.
     *
     * @param recipientId the contact's unique identifier
     */
    public void setRecipient(String recipientId) {
        this.recipientId = recipientId != null ? recipientId : "";
        loadMessages();
    }

    private void sendMessage() {
        if (viewModel == null)
            return;

        String text = messageField.getText().trim();
        if (text.isEmpty())
            return;

        viewModel.sendTextMessage(recipientId, text);
        messageField.clear();
    }
}
