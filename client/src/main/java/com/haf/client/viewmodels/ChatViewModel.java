package com.haf.client.viewmodels;

import com.haf.client.models.MessageVM;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.Map;

/**
 * UI-oriented wrapper around {@link MessageViewModel} for chat screen state.
 *
 * Tracks active recipient, per-recipient drafts and send enablement while
 * message transport remains in {@link MessageViewModel}.
 */
public class ChatViewModel {

    private static final ObservableList<MessageVM> EMPTY_MESSAGES = FXCollections.observableArrayList();

    private final MessageViewModel messageViewModel;
    private final Map<String, String> draftsByRecipient = new HashMap<>();

    private final StringProperty recipientId = new SimpleStringProperty("");
    private final StringProperty draftText = new SimpleStringProperty("");
    private final BooleanBinding canSend;

    public ChatViewModel(MessageViewModel messageViewModel) {
        this.messageViewModel = messageViewModel;
        this.canSend = Bindings.createBooleanBinding(
                () -> messageViewModel != null
                        && !normalize(recipientId.get()).isEmpty()
                        && !normalizeDraft(draftText.get()).isEmpty(),
                recipientId,
                draftText);
    }

    public boolean isReady() {
        return messageViewModel != null;
    }

    public StringProperty draftTextProperty() {
        return draftText;
    }

    public BooleanBinding canSendProperty() {
        return canSend;
    }

    public String getRecipientId() {
        return recipientId.get();
    }

    public void setRecipient(String newRecipientId) {
        String previousRecipient = normalize(recipientId.get());
        if (!previousRecipient.isEmpty()) {
            String currentDraft = draftText.get();
            if (currentDraft != null && !currentDraft.isEmpty()) {
                draftsByRecipient.put(previousRecipient, currentDraft);
            } else {
                draftsByRecipient.remove(previousRecipient);
            }
        }

        String normalized = normalize(newRecipientId);
        recipientId.set(normalized);

        String restoredDraft = draftsByRecipient.getOrDefault(normalized, "");
        draftText.set(restoredDraft);

        acknowledgeActiveRecipient();
    }

    public ObservableList<MessageVM> getActiveMessages() {
        if (messageViewModel == null) {
            return EMPTY_MESSAGES;
        }
        String activeRecipient = normalize(recipientId.get());
        if (activeRecipient.isEmpty()) {
            return EMPTY_MESSAGES;
        }
        return messageViewModel.getMessages(activeRecipient);
    }

    public void acknowledgeActiveRecipient() {
        if (messageViewModel == null) {
            return;
        }
        String activeRecipient = normalize(recipientId.get());
        if (activeRecipient.isEmpty()) {
            return;
        }
        messageViewModel.acknowledgeMessagesFrom(activeRecipient);
    }

    public void acknowledgeRecipient(String senderId) {
        if (messageViewModel == null) {
            return;
        }
        String normalized = normalize(senderId);
        if (normalized.isEmpty()) {
            return;
        }
        messageViewModel.acknowledgeMessagesFrom(normalized);
    }

    public void sendCurrentDraft() {
        if (messageViewModel == null) {
            return;
        }

        String activeRecipient = normalize(recipientId.get());
        String message = normalizeDraft(draftText.get());
        if (activeRecipient.isEmpty() || message.isEmpty()) {
            return;
        }

        messageViewModel.sendTextMessage(activeRecipient, message);
        draftText.set("");
        draftsByRecipient.remove(activeRecipient);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeDraft(String value) {
        return value == null ? "" : value.trim();
    }
}
