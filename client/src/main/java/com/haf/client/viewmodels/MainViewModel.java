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

    public interface ContactsGateway {
        CompletableFuture<String> fetchContacts();

        CompletableFuture<String> addContact(String userId);

        CompletableFuture<String> removeContact(String userId);
    }

    private static final Logger LOGGER = Logger.getLogger(MainViewModel.class.getName());

    private final ContactsGateway contactsGateway;
    private final ObservableList<ContactInfo> contacts = FXCollections.observableArrayList();
    private final ObjectProperty<MainTab> activeTab = new SimpleObjectProperty<>(MainTab.MESSAGES);
    private final BooleanProperty hasSearchResults = new SimpleBooleanProperty(false);

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

    public void fetchContacts() {
        contactsGateway.fetchContacts()
                .thenAccept(responseJson -> {
                    ContactsResponse response = JsonCodec.fromJson(responseJson, ContactsResponse.class);
                    if (response == null || response.getContacts() == null) {
                        return;
                    }

                    runOnUiThread(() -> {
                        for (UserSearchResultDTO contact : response.getContacts()) {
                            upsertContact(contact.getUserId(), contact.getFullName(), contact.getRegNumber(),
                                    contact.isActive());
                        }
                    });
                })
                .exceptionally(ex -> {
                    LOGGER.log(Level.SEVERE, "Failed to load contacts", ex);
                    return null;
                });
    }

    public ContactInfo ensureChatContact(String userId, String fullName, String regNumber) {
        ContactInfo target = getContactById(userId);
        if (target != null) {
            return target;
        }

        ContactInfo created = ContactInfo.inactive(userId, fullName, regNumber);
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
        if (hasContact(userId)) {
            return;
        }

        contacts.add(ContactInfo.inactive(userId, fullName, regNumber));
        contactsGateway.addContact(userId)
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
        ContactInfo updated = ContactInfo.fromPresence(existing.id(), existing.name(), existing.regNumber(), active);
        contacts.set(index, updated);
        return updated;
    }

    private void upsertContact(String userId, String fullName, String regNumber, boolean active) {
        ContactInfo contact = ContactInfo.fromPresence(userId, fullName, regNumber, active);
        int index = findContactIndex(userId);
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

    private static void runOnUiThread(Runnable action) {
        try {
            Platform.runLater(action);
        } catch (IllegalStateException ex) {
            action.run();
        }
    }
}
