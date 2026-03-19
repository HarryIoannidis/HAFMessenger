package com.haf.client.viewmodels;

import com.haf.client.core.NetworkSession;
import com.haf.client.models.ContactInfo;
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
import java.util.function.IntUnaryOperator;
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

    public interface ContactsGateway {
        CompletableFuture<String> fetchContacts();

        CompletableFuture<String> addContact(String userId);

        CompletableFuture<String> removeContact(String userId);
    }

    private static final Logger LOGGER = Logger.getLogger(MainViewModel.class.getName());
    private static final long ADD_CONTACT_PRESENCE_FALLBACK_DELAY_MS = 700L;

    private final ContactsGateway contactsGateway;
    private final ObservableList<ContactInfo> contacts = FXCollections.observableArrayList();
    private final ObjectProperty<MainTab> activeTab = new SimpleObjectProperty<>(MainTab.MESSAGES);
    private final BooleanProperty hasSearchResults = new SimpleBooleanProperty(false);
    private final ConcurrentHashMap<String, Long> presenceSignalByUser = new ConcurrentHashMap<>();
    private final AtomicLong presenceSignalCounter = new AtomicLong();

    public MainViewModel(ContactsGateway contactsGateway) {
        this.contactsGateway = Objects.requireNonNull(contactsGateway, "contactsGateway");
    }

    /**
     * Factory using the current authenticated session.
     */
    public static MainViewModel createDefault() {
        return new MainViewModel(new ContactsGateway() {
            @Override
            public CompletableFuture<String> fetchContacts() {
                if (NetworkSession.get() == null) {
                    return CompletableFuture.completedFuture("{}");
                }
                return NetworkSession.get().getAuthenticated("/api/v1/contacts");
            }

            @Override
            public CompletableFuture<String> addContact(String userId) {
                if (NetworkSession.get() == null) {
                    return CompletableFuture.failedFuture(new IllegalStateException("No active network session."));
                }

                AddContactRequest request = new AddContactRequest(userId);
                String body = JsonCodec.toJson(request);
                return NetworkSession.get().postAuthenticated("/api/v1/contacts", body);
            }

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

    public ObservableList<ContactInfo> contactsProperty() {
        return contacts;
    }

    public ObjectProperty<MainTab> activeTabProperty() {
        return activeTab;
    }

    public BooleanProperty hasSearchResultsProperty() {
        return hasSearchResults;
    }

    public void setActiveTab(MainTab tab) {
        activeTab.set(tab);
    }

    public void setHasSearchResults(boolean value) {
        hasSearchResults.set(value);
    }

    /**
     * Pure UI-state decision for contact click behavior on the Main screen.
     */
    public ContactSelectionAction resolveContactSelectionAction(MainTab currentTab, boolean clickedSameAsLastSelection) {
        if (currentTab == MainTab.SEARCH) {
            return ContactSelectionAction.SWITCH_TO_MESSAGES_TAB;
        }
        if (clickedSameAsLastSelection) {
            return ContactSelectionAction.DESELECT_AND_SHOW_PLACEHOLDER;
        }
        return ContactSelectionAction.KEEP_SELECTED_CONTACT;
    }

    public void fetchContacts() {
        contactsGateway.fetchContacts()
                .thenAccept(this::applyContactsSnapshot)
                .exceptionally(ex -> {
                    LOGGER.log(Level.SEVERE, "Failed to load contacts", ex);
                    return null;
                });
    }

    public ContactInfo ensureChatContact(String userId, String fullName, String regNumber) {
        return ensureChatContact(userId, fullName, regNumber, null, null, null, null);
    }

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

    public boolean hasContact(String userId) {
        return findContactIndex(userId) >= 0;
    }

    public ContactInfo getContactById(String userId) {
        int index = findContactIndex(userId);
        if (index < 0) {
            return null;
        }
        return contacts.get(index);
    }

    public void addContact(String userId, String fullName, String regNumber) {
        addContact(userId, fullName, regNumber, null, null, null, null);
    }

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
        contactsGateway.addContact(userId)
                .thenAccept(ignored -> {
                    // Hydrate optimistic rows (e.g. incoming unknown sender) with real
                    // server-side contact profile fields as soon as add succeeds.
                    fetchContacts();
                    scheduleAddContactPresenceFallback(userId, baselinePresenceSignal);
                })
                .exceptionally(ex -> {
                    LOGGER.log(Level.SEVERE, "Failed to add contact on server", ex);
                    return null;
                });
    }

    public void removeContact(String userId) {
        contacts.removeIf(info -> info.id().equals(userId));
        contactsGateway.removeContact(userId)
                .exceptionally(ex -> {
                    LOGGER.log(Level.SEVERE, "Failed to remove contact on server", ex);
                    return null;
                });
    }

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

    public ContactInfo incrementUnread(String userId) {
        return updateUnread(userId, count -> count == Integer.MAX_VALUE ? Integer.MAX_VALUE : count + 1);
    }

    public ContactInfo resetUnread(String userId) {
        return updateUnread(userId, ignored -> 0);
    }

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

    private int findContactIndex(String userId) {
        for (int i = 0; i < contacts.size(); i++) {
            ContactInfo info = contacts.get(i);
            if (info.id().equals(userId)) {
                return i;
            }
        }
        return -1;
    }

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

    private static String preferNonBlank(String incoming, String fallback) {
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return fallback;
    }

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

    private static void runOnUiThread(Runnable action) {
        try {
            Platform.runLater(action);
        } catch (IllegalStateException ex) {
            action.run();
        }
    }
}
