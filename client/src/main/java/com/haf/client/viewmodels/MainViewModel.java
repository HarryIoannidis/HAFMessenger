package com.haf.client.viewmodels;

import com.haf.client.core.NetworkSession;
import com.haf.client.models.ContactInfo;
import com.haf.client.utils.RuntimeIssue;
import com.haf.shared.requests.AddContactRequest;
import com.haf.shared.responses.ContactsResponse;
import com.haf.shared.dto.UserSearchResultDTO;
import com.haf.shared.utils.JsonCodec;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntUnaryOperator;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewModel for the main screen.
 *
 * Owns contact data + lightweight page state while controllers focus on view
 * wiring and navigation.
 */
public class MainViewModel {

    public enum MainTab {
        MESSAGES,
        SEARCH
    }

    public enum ContactSelectionAction {
        SWITCH_TO_MESSAGES_TAB,
        DESELECT_AND_SHOW_PLACEHOLDER,
        KEEP_SELECTED_CONTACT
    }

    public enum IncomingUnreadAction {
        INCREMENT,
        RESET
    }

    public interface ContactsGateway {
        /**
         * Fetches full contact list snapshot from backend.
         *
         * @return future containing raw contacts JSON payload
         */
        CompletableFuture<String> fetchContacts();

        /**
         * Adds a contact by user id on backend.
         *
         * @param userId target user id
         * @return future containing backend response payload
         */
        CompletableFuture<String> addContact(String userId);

        /**
         * Removes a contact by user id on backend.
         *
         * @param userId target user id
         * @return future containing backend response payload
         */
        CompletableFuture<String> removeContact(String userId);
    }

    private static final Logger LOGGER = Logger.getLogger(MainViewModel.class.getName());
    private static final long ADD_CONTACT_PRESENCE_FALLBACK_DELAY_MS = 700L;
    private static final String UNKNOWN_CONTACT_NAME_PLACEHOLDER = "Unknown Contact";
    private static final String UNKNOWN_CONTACT_REG_PLACEHOLDER = "";

    private final ContactsGateway contactsGateway;
    private final ObservableList<ContactInfo> contacts = FXCollections.observableArrayList();
    private final CopyOnWriteArrayList<Consumer<RuntimeIssue>> runtimeIssueListeners = new CopyOnWriteArrayList<>();
    private final ObjectProperty<MainTab> activeTab = new SimpleObjectProperty<>(MainTab.MESSAGES);
    private final BooleanProperty hasSearchResults = new SimpleBooleanProperty(false);
    private final ConcurrentHashMap<String, Long> presenceSignalByUser = new ConcurrentHashMap<>();
    private final AtomicLong presenceSignalCounter = new AtomicLong();

    /**
     * Creates main view-model with an injected contacts gateway.
     *
     * @param contactsGateway gateway used for contact CRUD synchronization
     */
    public MainViewModel(ContactsGateway contactsGateway) {
        this.contactsGateway = Objects.requireNonNull(contactsGateway, "contactsGateway");
    }

    /**
     * Factory using the current authenticated session.
     */
    public static MainViewModel createDefault() {
        return new MainViewModel(new ContactsGateway() {
            /**
             * Loads the authenticated user's current contacts snapshot.
             *
             * Used by the main screen to populate the left contact list from the backend.
             *
             * @return future with the raw contacts payload as JSON, or an empty JSON object
             *         when no session exists
             */
            @Override
            public CompletableFuture<String> fetchContacts() {
                if (NetworkSession.get() == null) {
                    return CompletableFuture.completedFuture("{}");
                }
                return NetworkSession.get().getAuthenticated("/api/v1/contacts");
            }

            /**
             * Sends an authenticated add-contact request for a user id.
             *
             * @param userId id of the user to add to the contact list
             * @return future with the backend response body
             * @throws IllegalStateException when there is no active network session
             */
            @Override
            public CompletableFuture<String> addContact(String userId) {
                if (NetworkSession.get() == null) {
                    return CompletableFuture.failedFuture(new IllegalStateException("No active network session."));
                }

                AddContactRequest request = new AddContactRequest(userId);
                String body = JsonCodec.toJson(request);
                return NetworkSession.get().postAuthenticated("/api/v1/contacts", body);
            }

            /**
             * Sends an authenticated remove-contact request for a user id.
             *
             * @param userId id of the contact to remove
             * @return future with the backend response body
             * @throws IllegalStateException when there is no active network session
             */
            @Override
            public CompletableFuture<String> removeContact(String userId) {
                if (NetworkSession.get() == null) {
                    return CompletableFuture.failedFuture(new IllegalStateException("No active network session."));
                }

                String path = "/api/v1/contacts?contactId="
                        + URLEncoder.encode(userId, StandardCharsets.UTF_8);
                return NetworkSession.get().deleteAuthenticated(path);
            }
        });
    }

    /**
     * Exposes observable contacts list used by contact list UI.
     *
     * @return observable contacts list
     */
    public ObservableList<ContactInfo> contactsProperty() {
        return contacts;
    }

    /**
     * Exposes active main-tab property.
     *
     * @return observable active-tab property
     */
    public ObjectProperty<MainTab> activeTabProperty() {
        return activeTab;
    }

    /**
     * Exposes whether search currently has results.
     *
     * @return observable search-results flag
     */
    public BooleanProperty hasSearchResultsProperty() {
        return hasSearchResults;
    }

    /**
     * Sets currently active tab.
     *
     * @param tab tab to activate
     */
    public void setActiveTab(MainTab tab) {
        activeTab.set(tab);
    }

    /**
     * Sets whether search result panel currently has results.
     *
     * @param value search-results flag value
     */
    public void setHasSearchResults(boolean value) {
        hasSearchResults.set(value);
    }

    /**
     * Registers a listener for recoverable runtime issues.
     *
     * @param listener runtime issue listener
     */
    public void addRuntimeIssueListener(Consumer<RuntimeIssue> listener) {
        if (listener != null) {
            runtimeIssueListeners.add(listener);
        }
    }

    /**
     * Unregisters a previously registered runtime-issue listener.
     *
     * @param listener runtime issue listener
     */
    public void removeRuntimeIssueListener(Consumer<RuntimeIssue> listener) {
        if (listener != null) {
            runtimeIssueListeners.remove(listener);
        }
    }

    /**
     * Pure UI-state decision for contact click behavior on the Main screen.
     */
    public ContactSelectionAction resolveContactSelectionAction(MainTab currentTab,
            boolean clickedSameAsLastSelection) {
        if (currentTab == MainTab.SEARCH) {
            return ContactSelectionAction.SWITCH_TO_MESSAGES_TAB;
        }
        if (clickedSameAsLastSelection) {
            return ContactSelectionAction.DESELECT_AND_SHOW_PLACEHOLDER;
        }
        return ContactSelectionAction.KEEP_SELECTED_CONTACT;
    }

    /**
     * Refreshes contacts list from backend snapshot.
     */
    public void fetchContacts() {
        contactsGateway.fetchContacts()
                .thenAccept(this::applyContactsSnapshot)
                .exceptionally(ex -> {
                    LOGGER.log(Level.SEVERE, "Failed to load contacts", ex);
                    publishRuntimeIssue(
                            "contacts.fetch.failed",
                            "Contacts could not be loaded",
                            "Failed to load contacts from server. " + resolveErrorMessage(ex, "Please retry."),
                            this::fetchContacts);
                    return null;
                });
    }

    /**
     * Ensures contact exists (or is created) for chat/open-conversation use.
     *
     * @param userId    user id
     * @param fullName  contact full name
     * @param regNumber registration number
     * @return existing or newly created contact entry
     */
    public ContactInfo ensureChatContact(String userId, String fullName, String regNumber) {
        return ensureChatContact(userId, fullName, regNumber, null, null, null, null);
    }

    /**
     * Ensures contact exists using fields from search result DTO.
     *
     * @param result search result payload
     * @return existing or newly created contact entry
     */
    public ContactInfo ensureChatContact(UserSearchResultDTO result) {
        if (result == null) {
            return null;
        }
        return ensureChatContact(
                result.getUserId(),
                result.getFullName(),
                result.getRegNumber(),
                result.getRank(),
                result.getEmail(),
                result.getTelephone(),
                result.getJoinedDate());
    }

    /**
     * Ensures contact exists and merges profile fields when contact already exists.
     *
     * @param userId     user id
     * @param fullName   contact full name
     * @param regNumber  registration number
     * @param rank       rank
     * @param email      email
     * @param telephone  telephone
     * @param joinedDate joined date
     * @return existing or newly created contact entry
     */
    public ContactInfo ensureChatContact(
            String userId,
            String fullName,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate) {
        ContactInfo target = getContactById(userId);
        if (target != null) {
            mergeContactProfile(target.id(), fullName, regNumber, rank, email, telephone, joinedDate);
            return target;
        }

        ContactInfo created = ContactInfo.unknown(userId, fullName, regNumber, rank, email, telephone, joinedDate);
        contacts.add(created);
        return created;
    }

    /**
     * Checks whether a contact exists locally.
     *
     * @param userId user id to check
     * @return {@code true} when contact exists
     */
    public boolean hasContact(String userId) {
        return findContactIndex(userId) >= 0;
    }

    /**
     * Finds contact by id.
     *
     * @param userId contact id
     * @return contact entry or {@code null} when not found
     */
    public ContactInfo getContactById(String userId) {
        int index = findContactIndex(userId);
        if (index < 0) {
            return null;
        }
        return contacts.get(index);
    }

    /**
     * Adds a contact using minimal profile fields.
     *
     * @param userId    user id
     * @param fullName  full name
     * @param regNumber registration number
     */
    public void addContact(String userId, String fullName, String regNumber) {
        addContact(userId, fullName, regNumber, null, null, null, null);
    }

    /**
     * Adds a contact using fields from search result DTO.
     *
     * @param result search result payload
     */
    public void addContact(UserSearchResultDTO result) {
        if (result == null) {
            return;
        }
        addContact(
                result.getUserId(),
                result.getFullName(),
                result.getRegNumber(),
                result.getRank(),
                result.getEmail(),
                result.getTelephone(),
                result.getJoinedDate());
    }

    /**
     * Adds contact optimistically and syncs with backend.
     *
     * @param userId     user id
     * @param fullName   full name
     * @param regNumber  registration number
     * @param rank       rank
     * @param email      email
     * @param telephone  telephone
     * @param joinedDate joined date
     */
    public void addContact(
            String userId,
            String fullName,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        if (hasContact(userId)) {
            mergeContactProfile(userId, fullName, regNumber, rank, email, telephone, joinedDate);
            return;
        }

        long baselinePresenceSignal = presenceSignalByUser.getOrDefault(userId, 0L);
        contacts.add(ContactInfo.unknown(userId, fullName, regNumber, rank, email, telephone, joinedDate));
        syncAddContactWithServer(userId, baselinePresenceSignal);
    }

    /**
     * Removes contact locally and attempts backend removal.
     *
     * @param userId user id to remove
     */
    public void removeContact(String userId) {
        contacts.removeIf(info -> info.id().equals(userId));
        syncRemoveContactWithServer(userId);
    }

    /**
     * Updates contact presence state and records a presence signal marker.
     *
     * @param userId contact id
     * @param active presence flag
     * @return updated contact, or {@code null} when contact does not exist
     */
    public ContactInfo updateContactPresence(String userId, boolean active) {
        int index = findContactIndex(userId);
        if (index < 0) {
            return null;
        }

        ContactInfo existing = contacts.get(index);
        ContactInfo updated = ContactInfo.fromPresence(
                existing.id(),
                existing.name(),
                existing.regNumber(),
                existing.rank(),
                existing.email(),
                existing.telephone(),
                existing.joinedDate(),
                active,
                existing.unreadCount());
        contacts.set(index, updated);
        presenceSignalByUser.put(userId, presenceSignalCounter.incrementAndGet());
        return updated;
    }

    /**
     * Increments unread count for a contact.
     *
     * @param userId contact id
     * @return updated contact, or {@code null} when contact does not exist
     */
    public ContactInfo incrementUnread(String userId) {
        return updateUnread(userId, count -> count == Integer.MAX_VALUE ? Integer.MAX_VALUE : count + 1);
    }

    /**
     * Resets unread count for a contact.
     *
     * @param userId contact id
     * @return updated contact, or {@code null} when contact does not exist
     */
    public ContactInfo resetUnread(String userId) {
        return updateUnread(userId, ignored -> 0);
    }

    /**
     * Resets unread count when opening chat with specific recipient.
     *
     * @param recipientId active chat recipient id
     */
    public void resetUnreadOnChatOpen(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            return;
        }
        resetUnread(recipientId);
    }

    /**
     * Ensures an incoming sender exists in contacts, creating placeholders when
     * needed.
     *
     * @param senderId sender id from incoming message
     * @return existing or newly created contact entry
     */
    public ContactInfo ensureIncomingContact(String senderId) {
        if (senderId == null || senderId.isBlank()) {
            return null;
        }

        ContactInfo existing = getContactById(senderId);
        if (existing != null) {
            return existing;
        }

        addContact(
                senderId,
                UNKNOWN_CONTACT_NAME_PLACEHOLDER,
                UNKNOWN_CONTACT_REG_PLACEHOLDER);

        ContactInfo created = getContactById(senderId);
        if (created == null) {
            created = ensureChatContact(
                    senderId,
                    UNKNOWN_CONTACT_NAME_PLACEHOLDER,
                    UNKNOWN_CONTACT_REG_PLACEHOLDER);
        }
        return created;
    }

    /**
     * Applies unread policy for incoming message based on active tab/recipient.
     *
     * @param senderId               sender id of incoming message
     * @param currentChatRecipientId currently opened chat recipient id
     * @return unread action that was applied, or {@code null} when contact cannot
     *         be resolved
     */
    public IncomingUnreadAction applyIncomingMessage(String senderId, String currentChatRecipientId) {
        ContactInfo contact = ensureIncomingContact(senderId);
        if (contact == null) {
            return null;
        }

        IncomingUnreadAction action = resolveIncomingUnreadAction(activeTab.get(), currentChatRecipientId, senderId);
        if (action == IncomingUnreadAction.RESET) {
            resetUnread(senderId);
        } else {
            incrementUnread(senderId);
        }
        return action;
    }

    /**
     * Inserts or replaces a contact entry from server snapshot fields and presence
     * state.
     */
    private void upsertContact(
            String userId,
            String fullName,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate,
            boolean active) {
        int index = findContactIndex(userId);
        int unreadCount = index >= 0 ? contacts.get(index).unreadCount() : 0;

        ContactInfo contact = ContactInfo.fromPresence(
                userId,
                fullName,
                regNumber,
                rank,
                email,
                telephone,
                joinedDate,
                active,
                unreadCount);
        if (index >= 0) {
            contacts.set(index, contact);
        } else {
            contacts.add(contact);
        }
    }

    /**
     * Finds index of contact by user id.
     *
     * @param userId contact id
     * @return contact index or {@code -1} when not found
     */
    private int findContactIndex(String userId) {
        for (int i = 0; i < contacts.size(); i++) {
            ContactInfo info = contacts.get(i);
            if (info.id().equals(userId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Applies backend contacts response JSON snapshot into local observable list.
     *
     * @param responseJson contacts response payload
     */
    private void applyContactsSnapshot(String responseJson) {
        ContactsResponse response = JsonCodec.fromJson(responseJson, ContactsResponse.class);
        if (response == null || response.getContacts() == null) {
            return;
        }

        runOnUiThread(() -> {
            for (UserSearchResultDTO contact : response.getContacts()) {
                upsertContact(
                        contact.getUserId(),
                        contact.getFullName(),
                        contact.getRegNumber(),
                        contact.getRank(),
                        contact.getEmail(),
                        contact.getTelephone(),
                        contact.getJoinedDate(),
                        contact.isActive());
            }
        });
    }

    /**
     * Merges non-blank profile fields into an existing contact entry.
     */
    private void mergeContactProfile(
            String userId,
            String fullName,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate) {
        int index = findContactIndex(userId);
        if (index < 0) {
            return;
        }

        ContactInfo existing = contacts.get(index);
        ContactInfo merged = new ContactInfo(
                existing.id(),
                preferNonBlank(fullName, existing.name()),
                preferNonBlank(regNumber, existing.regNumber()),
                preferNonBlank(rank, existing.rank()),
                preferNonBlank(email, existing.email()),
                preferNonBlank(telephone, existing.telephone()),
                preferNonBlank(joinedDate, existing.joinedDate()),
                existing.activenessLabel(),
                existing.activenessColor(),
                existing.unreadCount());
        contacts.set(index, merged);
    }

    /**
     * Updates unread count for a contact using provided update function.
     */
    private ContactInfo updateUnread(String userId, IntUnaryOperator updater) {
        if (userId == null || userId.isBlank()) {
            return null;
        }

        int index = findContactIndex(userId);
        if (index < 0) {
            return null;
        }

        ContactInfo existing = contacts.get(index);
        int nextUnread = Math.max(0, updater.applyAsInt(existing.unreadCount()));
        if (nextUnread == existing.unreadCount()) {
            return existing;
        }

        ContactInfo updated = new ContactInfo(
                existing.id(),
                existing.name(),
                existing.regNumber(),
                existing.rank(),
                existing.email(),
                existing.telephone(),
                existing.joinedDate(),
                existing.activenessLabel(),
                existing.activenessColor(),
                nextUnread);
        contacts.set(index, updated);
        return updated;
    }

    /**
     * Prefers incoming non-blank field value, otherwise keeps fallback.
     */
    private static String preferNonBlank(String incoming, String fallback) {
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return fallback;
    }

    /**
     * Evaluates whether chat placeholder should be shown after contact removal
     * using
     * current contacts state.
     */
    public boolean shouldShowPlaceholderAfterRemoval(
            String removedUserId,
            ContactInfo selectedBeforeRemoval,
            String activeChatRecipientId) {
        return shouldShowPlaceholderAfterRemoval(
                removedUserId,
                selectedBeforeRemoval,
                activeChatRecipientId,
                contacts.isEmpty());
    }

    /**
     * Evaluates whether chat placeholder should be shown after contact removal.
     */
    public static boolean shouldShowPlaceholderAfterRemoval(
            String removedUserId,
            ContactInfo selectedBeforeRemoval,
            String activeChatRecipientId,
            boolean contactsEmptyAfterRemoval) {
        if (contactsEmptyAfterRemoval) {
            return true;
        }
        if (removedUserId == null || removedUserId.isBlank()) {
            return false;
        }
        if (selectedBeforeRemoval != null && removedUserId.equals(selectedBeforeRemoval.id())) {
            return true;
        }
        return activeChatRecipientId != null && removedUserId.equals(activeChatRecipientId);
    }

    /**
     * Resolves unread action for incoming message based on active tab and selected
     * chat recipient.
     */
    public static IncomingUnreadAction resolveIncomingUnreadAction(
            MainTab activeTab,
            String currentChatRecipientId,
            String senderId) {
        if (activeTab == MainTab.MESSAGES
                && senderId != null
                && senderId.equals(currentChatRecipientId)) {
            return IncomingUnreadAction.RESET;
        }
        return IncomingUnreadAction.INCREMENT;
    }

    /**
     * Compares two potential contact selections by id.
     */
    public static boolean isSameContactSelection(ContactInfo clicked, Object candidate) {
        if (clicked == null || clicked.id() == null || clicked.id().isBlank()) {
            return false;
        }
        if (!(candidate instanceof ContactInfo previous)) {
            return false;
        }
        return clicked.id().equals(previous.id());
    }

    /**
     * Schedules a delayed contacts refresh when no newer presence signal has
     * arrived.
     */
    private void scheduleAddContactPresenceFallback(String userId, long baselinePresenceSignal) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        Thread.ofVirtual().name("add-contact-presence-fallback").start(() -> {
            try {
                Thread.sleep(ADD_CONTACT_PRESENCE_FALLBACK_DELAY_MS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }

            if (!hasContact(userId)) {
                return;
            }

            long latestSignal = presenceSignalByUser.getOrDefault(userId, 0L);
            if (latestSignal != baselinePresenceSignal) {
                return;
            }

            contactsGateway.fetchContacts()
                    .thenAccept(this::applyContactsSnapshot)
                    .exceptionally(ex -> {
                        LOGGER.log(Level.FINE, "Presence fallback refresh failed", ex);
                        return null;
                    });
        });
    }

    /**
     * Synchronizes optimistic add-contact operation with backend.
     *
     * @param userId target contact id
     * @param baselinePresenceSignal presence signal snapshot for fallback logic
     */
    private void syncAddContactWithServer(String userId, long baselinePresenceSignal) {
        contactsGateway.addContact(userId)
                .thenAccept(ignored -> {
                    // Hydrate optimistic rows (e.g. incoming unknown sender) with real
                    // server-side contact profile fields as soon as add succeeds.
                    fetchContacts();
                    scheduleAddContactPresenceFallback(userId, baselinePresenceSignal);
                })
                .exceptionally(ex -> {
                    LOGGER.log(Level.SEVERE, "Failed to add contact on server", ex);
                    publishRuntimeIssue(
                            "contacts.add.failed",
                            "Contact could not be added",
                            "Failed to sync contact add with server. " + resolveErrorMessage(ex, "Please retry."),
                            () -> syncAddContactWithServer(userId, baselinePresenceSignal));
                    return null;
                });
    }

    /**
     * Synchronizes local contact removal with backend.
     *
     * @param userId target contact id
     */
    private void syncRemoveContactWithServer(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        contactsGateway.removeContact(userId)
                .exceptionally(ex -> {
                    LOGGER.log(Level.SEVERE, "Failed to remove contact on server", ex);
                    publishRuntimeIssue(
                            "contacts.remove.failed",
                            "Contact removal failed",
                            "Failed to sync contact removal with server. " + resolveErrorMessage(ex, "Please retry."),
                            () -> syncRemoveContactWithServer(userId));
                    return null;
                });
    }

    /**
     * Dispatches a runtime issue to registered listeners.
     *
     * @param dedupeKey issue dedupe key
     * @param title issue title
     * @param message issue message
     * @param retryAction retry callback
     */
    private void publishRuntimeIssue(String dedupeKey, String title, String message, Runnable retryAction) {
        RuntimeIssue issue = new RuntimeIssue(dedupeKey, title, message, retryAction);
        for (Consumer<RuntimeIssue> listener : runtimeIssueListeners) {
            try {
                listener.accept(issue);
            } catch (Exception ignored) {
                // Listener failures should not block others.
            }
        }
    }

    /**
     * Resolves readable throwable message with fallback.
     *
     * @param error throwable
     * @param fallback fallback text
     * @return resolved message
     */
    private static String resolveErrorMessage(Throwable error, String fallback) {
        if (error == null) {
            return fallback;
        }
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }

    /**
     * Runs UI updates on JavaFX thread with fallback for non-JavaFX test contexts.
     */
    private static void runOnUiThread(Runnable action) {
        try {
            Platform.runLater(action);
        } catch (IllegalStateException ex) {
            action.run();
        }
    }
}
