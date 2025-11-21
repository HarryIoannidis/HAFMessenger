package com.haf.client.viewmodel;

import com.haf.client.network.MessageReceiver;
import com.haf.client.network.MessageSender;
import com.haf.shared.constants.MessageHeader;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.nio.charset.StandardCharsets;

public class MessageViewModel {
    private final MessageSender messageSender;
    private final MessageReceiver messageReceiver;
    private final StringProperty status = new SimpleStringProperty("Ready");
    private final ObservableList<String> messages = FXCollections.observableArrayList();

    /**
     * Creates a MessageViewModel with the specified MessageSender and MessageReceiver.
     * @param messageSender the message sender
     * @param messageReceiver the message receiver
     */
    public MessageViewModel(MessageSender messageSender, MessageReceiver messageReceiver) {
        this.messageSender = messageSender;
        this.messageReceiver = messageReceiver;
        
        // Set up message receiver listener
        messageReceiver.setMessageListener(new MessageReceiver.MessageListener() {
            @Override
            public void onMessage(byte[] plaintext, String senderId, String contentType) {
                // Phase 4: Simple text message handling
                // Phase 6: Will implement contentType-based rendering
                if ("text/plain".equals(contentType)) {
                    String message = new String(plaintext, StandardCharsets.UTF_8);
                    Platform.runLater(() -> {
                        messages.add("[" + senderId + "]: " + message);
                        status.set("Message received from " + senderId);
                    });
                } else {
                    Platform.runLater(() -> {
                        status.set("Received " + contentType + " message from " + senderId + " (rendering in Phase 6)");
                    });
                }
            }

            @Override
            public void onError(Throwable error) {
                Platform.runLater(() -> {
                    status.set("Error: " + error.getMessage());
                    messages.add("[ERROR]: " + error.getMessage());
                });
            }
        });
    }

    /**
     * Sends a text message to a recipient.
     * @param recipientId the recipient's ID
     * @param messageText the message text
     */
    public void sendTextMessage(String recipientId, String messageText) {
        try {
            byte[] payload = messageText.getBytes(StandardCharsets.UTF_8);
            messageSender.sendMessage(payload, recipientId, "text/plain", MessageHeader.MAX_TTL_SECONDS);
            
            Platform.runLater(() -> {
                status.set("Message sent to " + recipientId);
                messages.add("[You -> " + recipientId + "]: " + messageText);
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                status.set("Failed to send message: " + e.getMessage());
                messages.add("[ERROR]: " + e.getMessage());
            });
        }
    }

    /**
     * Starts receiving messages.
     */
    public void startReceiving() {
        try {
            messageReceiver.start();
            Platform.runLater(() -> {
                status.set("Receiving messages...");
            });
        } catch (Exception e) {
            Platform.runLater(() -> {
                status.set("Failed to start receiving: " + e.getMessage());
            });
        }
    }

    /**
     * Stops receiving messages.
     */
    public void stopReceiving() {
        messageReceiver.stop();
        Platform.runLater(() -> {
            status.set("Stopped receiving messages");
        });
    }

    /**
     * Gets the status property.
     * @return the status property
     */
    public StringProperty statusProperty() {
        return status;
    }

    /**
     * Gets the messages list.
     * @return the observable list of messages
     */
    public ObservableList<String> getMessages() {
        return messages;
    }
}

