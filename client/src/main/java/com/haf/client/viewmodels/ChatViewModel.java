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
 * UI-oriented wrapper around {@link MessagesViewModel} for chat screen state.
 *
 * Tracks active recipient, per-recipient drafts and send enablement while
 * message transport remains in {@link MessagesViewModel}.
 */
public class ChatViewModel {

    private static final ObservableList<MessageVM> EMPTY_MESSAGES = FXCollections.observableArrayList();

    private final MessagesViewModel messageViewModel;
    private final Map<String, String> draftsByRecipient = new HashMap<>();

    private final StringProperty recipientId = new SimpleStringProperty("");
    private final StringProperty draftText = new SimpleStringProperty("");
    private final BooleanBinding canSend;

    /**
     * Creates a chat view-model wrapper around the shared message view-model.
     *
     * @param messageViewModel transport/state view-model backing message operations
     */
    public ChatViewModel(MessagesViewModel messageViewModel) {
        this.messageViewModel = messageViewModel;
        this.canSend = Bindings.createBooleanBinding(
                () -> messageViewModel != null
                        && !normalize(recipientId.get()).isEmpty()
                        && !normalizeDraft(draftText.get()).isEmpty(),
                recipientId,
                draftText);
    }

    /**
     * Indicates whether this chat view-model can interact with messaging services.
     *
     * @return {@code true} when a backing {@link MessagesViewModel} is available
     */
    public boolean isReady() {
        return messageViewModel != null;
    }

    /**
     * Exposes the current draft text property for the active recipient.
     *
     * @return observable draft text property bound by the chat input field
     */
    public StringProperty draftTextProperty() {
        return draftText;
    }

    /**
     * Exposes whether the send action should currently be enabled.
     *
     * @return boolean binding that becomes {@code true} when recipient + draft are
     *         valid
     */
    public BooleanBinding canSendProperty() {
        return canSend;
    }

    /**
     * Returns the currently selected chat recipient id.
     *
     * @return active recipient id, or empty string when no contact is selected
     */
    public String getRecipientId() {
        return recipientId.get();
    }

    /**
     * Switches active recipient while preserving/restoring per-recipient drafts.
     *
     * @param newRecipientId recipient id to activate
     */
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

    /**
     * Returns the observable message list for the active recipient.
     *
     * @return recipient-specific message list, or an empty list when no active
     *         recipient exists
     */
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

    /**
     * Marks pending messages for the active recipient as acknowledged/read.
     */
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

    /**
     * Marks pending messages as acknowledged for an explicit sender id.
     *
     * @param senderId sender/recipient id whose pending messages should be
     *                 acknowledged
     */
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

    /**
     * Sends the current draft text to the active recipient and clears the draft.
     */
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

    /**
     * Normalizes recipient identifiers by trimming and converting null to empty.
     *
     * @param value raw recipient id
     * @return normalized recipient id
     */
    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Normalizes draft text by trimming and converting null to empty.
     *
     * @param value raw draft value
     * @return normalized draft text
     */
    private static String normalizeDraft(String value) {
        return value == null ? "" : value.trim();
    }
}
