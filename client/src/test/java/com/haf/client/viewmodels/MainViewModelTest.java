package com.haf.client.viewmodels;

import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.client.models.ContactInfo;
import com.haf.client.utils.RuntimeIssue;
import com.haf.shared.responses.ContactsResponse;
import com.haf.shared.dto.UserSearchResultDTO;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class MainViewModelTest {

    @Test
    void fetch_contacts_upserts_without_duplicates() {
        ContactsResponse first = ContactsResponse.success(List.of(
                new UserSearchResultDTO("u-1", "Jane Doe", "100", "jane@haf.gr", "SMINIAS", false)));
        ContactsResponse second = ContactsResponse.success(List.of(
                new UserSearchResultDTO("u-1", "Jane Updated", "100", "jane@haf.gr", "SMINIAS", true)));

        AtomicInteger calls = new AtomicInteger();
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture(
                        calls.incrementAndGet() == 1 ? JsonCodec.toJson(first) : JsonCodec.toJson(second))));

        viewModel.fetchContacts();
        awaitCondition(() -> viewModel.contactsProperty().size() == 1);
        assertEquals("Jane Doe", viewModel.contactsProperty().getFirst().name());
        assertEquals("Inactive", viewModel.contactsProperty().getFirst().activenessLabel());

        viewModel.fetchContacts();
        awaitCondition(() -> "Jane Updated".equals(viewModel.contactsProperty().getFirst().name()));

        assertEquals(1, viewModel.contactsProperty().size());
        assertEquals("Active", viewModel.contactsProperty().getFirst().activenessLabel());
    }

    @Test
    void add_contact_adds_locally_and_refreshes_contacts_after_server_add() {
        AtomicInteger addCalls = new AtomicInteger();
        AtomicInteger fetchCalls = new AtomicInteger();
        ContactsResponse refreshed = ContactsResponse.success(List.of(
                new UserSearchResultDTO("u-1", "Jane", "100", "jane@haf.gr", "SMINIAS", true)));
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> {
                    fetchCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(JsonCodec.toJson(refreshed));
                },
                userId -> {
                    addCalls.incrementAndGet();
                    return CompletableFuture.completedFuture("{}");
                },
                userId -> CompletableFuture.completedFuture("{}")));

        viewModel.addContact("u-1", "Jane", "100");
        viewModel.addContact("u-1", "Jane", "100");

        assertTrue(viewModel.hasContact("u-1"));
        assertEquals(1, viewModel.contactsProperty().size());
        assertEquals(1, addCalls.get());
        awaitCondition(() -> fetchCalls.get() >= 1);
        awaitCondition(() -> "Active".equals(viewModel.contactsProperty().getFirst().activenessLabel()));
    }

    @Test
    void add_contact_still_hydrates_profiles_even_when_presence_event_arrives() {
        AtomicInteger addCalls = new AtomicInteger();
        AtomicInteger fetchCalls = new AtomicInteger();
        ContactsResponse refreshed = ContactsResponse.success(List.of(
                new UserSearchResultDTO("u-1", "Jane", "100", "jane@haf.gr", "SMINIAS", true)));
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> {
                    fetchCalls.incrementAndGet();
                    return CompletableFuture.completedFuture(JsonCodec.toJson(refreshed));
                },
                userId -> {
                    addCalls.incrementAndGet();
                    return CompletableFuture.completedFuture("{}");
                },
                userId -> CompletableFuture.completedFuture("{}")));

        viewModel.addContact("u-1", "Jane", "100");
        ContactInfo updated = viewModel.updateContactPresence("u-1", true);

        assertNotNull(updated);
        awaitCondition(() -> "Active".equals(viewModel.contactsProperty().getFirst().activenessLabel()));
        awaitCondition(() -> fetchCalls.get() >= 1);
        assertEquals(1, addCalls.get());
    }

    @Test
    void remove_contact_removes_locally_and_calls_gateway() {
        AtomicInteger removeCalls = new AtomicInteger();
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}"),
                userId -> CompletableFuture.completedFuture("{}"),
                userId -> {
                    removeCalls.incrementAndGet();
                    return CompletableFuture.completedFuture("{}");
                }));

        viewModel.ensureChatContact("u-1", "Jane", "100");
        assertTrue(viewModel.hasContact("u-1"));

        viewModel.removeContact("u-1");

        assertFalse(viewModel.hasContact("u-1"));
        assertEquals(1, removeCalls.get());
    }

    @Test
    void fetch_contacts_failure_emits_runtime_issue_with_retry_action() {
        AtomicInteger fetchCalls = new AtomicInteger();
        AtomicBoolean failFetch = new AtomicBoolean(true);
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> {
                    fetchCalls.incrementAndGet();
                    if (failFetch.get()) {
                        return CompletableFuture.failedFuture(new RuntimeException("server unreachable"));
                    }
                    return CompletableFuture.completedFuture(JsonCodec.toJson(ContactsResponse.success(List.of())));
                }));
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        viewModel.fetchContacts();
        awaitCondition(() -> !issues.isEmpty());

        assertEquals("contacts.fetch.failed", issues.getFirst().dedupeKey());
        failFetch.set(false);
        issues.getFirst().retryAction().run();

        awaitCondition(() -> fetchCalls.get() >= 2);
    }

    @Test
    void fetch_contacts_invalid_session_emits_revoked_session_issue() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.failedFuture(
                        new HttpCommunicationException(
                                "HTTP GET failed with status 401: {\"error\":\"invalid session\"}",
                                401,
                                "{\"error\":\"invalid session\"}"))));
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        viewModel.fetchContacts();
        awaitCondition(() -> !issues.isEmpty());

        assertEquals("messaging.session.revoked", issues.getFirst().dedupeKey());
        assertEquals("Session expired", issues.getFirst().title());
    }

    @Test
    void fetch_contacts_takeover_session_emits_takeover_issue() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.failedFuture(
                        new HttpCommunicationException(
                                "HTTP GET failed with status 401: {\"error\":\"session revoked by takeover\"}",
                                401,
                                "{\"error\":\"session revoked by takeover\"}"))));
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        viewModel.fetchContacts();
        awaitCondition(() -> !issues.isEmpty());

        assertEquals("messaging.session.takeover", issues.getFirst().dedupeKey());
        assertEquals("Logged out", issues.getFirst().title());
    }

    @Test
    void add_contact_failure_emits_runtime_issue_and_retry_does_not_duplicate_local_contact() {
        AtomicInteger addCalls = new AtomicInteger();
        AtomicBoolean failAdd = new AtomicBoolean(true);
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}"),
                userId -> {
                    addCalls.incrementAndGet();
                    if (failAdd.get()) {
                        return CompletableFuture.failedFuture(new RuntimeException("add failed"));
                    }
                    return CompletableFuture.completedFuture("{}");
                },
                userId -> CompletableFuture.completedFuture("{}")));
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        viewModel.addContact("u-1", "Jane", "100");
        awaitCondition(() -> !issues.isEmpty());

        assertEquals(1, viewModel.contactsProperty().size());
        assertEquals("contacts.add.failed", issues.getFirst().dedupeKey());
        failAdd.set(false);
        issues.getFirst().retryAction().run();

        awaitCondition(() -> addCalls.get() >= 2);
        assertEquals(1, viewModel.contactsProperty().size());
    }

    @Test
    void remove_contact_failure_emits_runtime_issue_with_retry_action() {
        AtomicInteger removeCalls = new AtomicInteger();
        AtomicBoolean failRemove = new AtomicBoolean(true);
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}"),
                userId -> CompletableFuture.completedFuture("{}"),
                userId -> {
                    removeCalls.incrementAndGet();
                    if (failRemove.get()) {
                        return CompletableFuture.failedFuture(new RuntimeException("remove failed"));
                    }
                    return CompletableFuture.completedFuture("{}");
                }));
        List<RuntimeIssue> issues = new CopyOnWriteArrayList<>();
        viewModel.addRuntimeIssueListener(issues::add);

        viewModel.ensureChatContact("u-1", "Jane", "100");
        viewModel.removeContact("u-1");
        awaitCondition(() -> !issues.isEmpty());

        assertEquals("contacts.remove.failed", issues.getFirst().dedupeKey());
        failRemove.set(false);
        issues.getFirst().retryAction().run();
        awaitCondition(() -> removeCalls.get() >= 2);
    }

    @Test
    void ensure_chat_contact_returns_existing_contact_without_duplicate() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}")));

        ContactInfo created = viewModel.ensureChatContact("u-1", "Jane", "100");
        ContactInfo second = viewModel.ensureChatContact("u-1", "Jane", "100");

        assertSame(created, second);
        assertEquals(1, viewModel.contactsProperty().size());
        assertEquals("", created.activenessLabel());
        assertEquals("transparent", created.activenessColor());
    }

    @Test
    void update_contact_presence_replaces_contact_state() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}")));

        viewModel.ensureChatContact("u-1", "Jane", "100");
        assertEquals("", viewModel.contactsProperty().getFirst().activenessLabel());
        assertEquals("transparent", viewModel.contactsProperty().getFirst().activenessColor());

        ContactInfo updated = viewModel.updateContactPresence("u-1", true);

        assertNotNull(updated);
        assertEquals("Active", updated.activenessLabel());
        assertEquals("#00b706", updated.activenessColor());
        assertEquals("Active", viewModel.contactsProperty().getFirst().activenessLabel());
    }

    @Test
    void update_contact_presence_marks_contact_inactive_when_active_is_false() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}")));

        viewModel.ensureChatContact("u-1", "Jane", "100");

        ContactInfo updated = viewModel.updateContactPresence("u-1", false);

        assertNotNull(updated);
        assertEquals("Inactive", updated.activenessLabel());
        assertEquals("#ff0000", updated.activenessColor());
        assertEquals("Inactive", viewModel.contactsProperty().getFirst().activenessLabel());
    }

    @Test
    void enriched_profile_fields_survive_presence_update() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}")));
        UserSearchResultDTO dto = new UserSearchResultDTO(
                "u-1",
                "Jane",
                "100",
                "jane@haf.gr",
                "SMINIAS",
                "6900000000",
                "2026-01-01",
                false);

        viewModel.addContact(dto);
        ContactInfo contact = viewModel.getContactById("u-1");
        assertNotNull(contact);
        assertEquals("SMINIAS", contact.rank());
        assertEquals("jane@haf.gr", contact.email());
        assertEquals("6900000000", contact.telephone());
        assertEquals("2026-01-01", contact.joinedDate());

        ContactInfo updated = viewModel.updateContactPresence("u-1", true);
        assertNotNull(updated);
        assertEquals("SMINIAS", updated.rank());
        assertEquals("jane@haf.gr", updated.email());
        assertEquals("6900000000", updated.telephone());
        assertEquals("2026-01-01", updated.joinedDate());
        assertEquals("Active", updated.activenessLabel());
    }

    @Test
    void increment_and_reset_unread_are_applied_per_contact() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}")));
        viewModel.ensureChatContact("u-1", "Jane", "100");

        ContactInfo first = viewModel.incrementUnread("u-1");
        ContactInfo second = viewModel.incrementUnread("u-1");
        ContactInfo reset = viewModel.resetUnread("u-1");
        ContactInfo resetAgain = viewModel.resetUnread("u-1");

        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(reset);
        assertNotNull(resetAgain);
        assertEquals(1, first.unreadCount());
        assertEquals(2, second.unreadCount());
        assertEquals(0, reset.unreadCount());
        assertEquals(0, resetAgain.unreadCount());
        assertEquals(0, viewModel.getContactById("u-1").unreadCount());
    }

    @Test
    void unread_is_preserved_when_presence_and_contact_snapshot_update_the_contact() {
        ContactsResponse refreshed = ContactsResponse.success(List.of(
                new UserSearchResultDTO("u-1", "Jane Updated", "100", "jane@haf.gr", "SMINIAS", true)));
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture(JsonCodec.toJson(refreshed))));

        viewModel.ensureChatContact("u-1", "Jane", "100");
        viewModel.incrementUnread("u-1");
        viewModel.incrementUnread("u-1");

        ContactInfo afterPresence = viewModel.updateContactPresence("u-1", true);
        assertNotNull(afterPresence);
        assertEquals(2, afterPresence.unreadCount());

        viewModel.fetchContacts();
        awaitCondition(() -> {
            ContactInfo current = viewModel.getContactById("u-1");
            return current != null
                    && "Jane Updated".equals(current.name())
                    && current.unreadCount() == 2;
        });
    }

    @Test
    void increment_unread_returns_null_for_missing_contact_until_sender_is_auto_added() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}")));

        assertNull(viewModel.incrementUnread("sender-1"));

        viewModel.ensureChatContact("sender-1", "sender-1", "sender-1");
        ContactInfo updated = viewModel.incrementUnread("sender-1");

        assertNotNull(updated);
        assertEquals("sender-1", updated.name());
        assertEquals("sender-1", updated.regNumber());
        assertEquals(1, updated.unreadCount());
    }

    @Test
    void ensure_incoming_contact_auto_adds_unknown_sender_with_placeholder_profile_fields() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}")));

        ContactInfo created = viewModel.ensureIncomingContact("sender-42");

        assertNotNull(created);
        assertEquals("sender-42", created.id());
        assertEquals("Unknown Contact", created.name());
        assertEquals("", created.regNumber());
        assertEquals(0, created.unreadCount());
    }

    @Test
    void incoming_unread_action_resets_when_active_chat_is_open() {
        MainViewModel.IncomingUnreadAction action = MainViewModel.resolveIncomingUnreadAction(
                MainViewModel.MainTab.MESSAGES,
                "user-1",
                "user-1");

        assertEquals(MainViewModel.IncomingUnreadAction.RESET, action);
    }

    @Test
    void incoming_unread_action_increments_when_chat_is_not_open() {
        MainViewModel.IncomingUnreadAction actionInSearchTab = MainViewModel.resolveIncomingUnreadAction(
                MainViewModel.MainTab.SEARCH,
                "user-1",
                "user-1");
        MainViewModel.IncomingUnreadAction actionForDifferentOpenChat = MainViewModel.resolveIncomingUnreadAction(
                MainViewModel.MainTab.MESSAGES,
                "user-1",
                "user-2");

        assertEquals(MainViewModel.IncomingUnreadAction.INCREMENT, actionInSearchTab);
        assertEquals(MainViewModel.IncomingUnreadAction.INCREMENT, actionForDifferentOpenChat);
    }

    @Test
    void apply_incoming_message_resets_for_active_chat_and_increments_for_inactive_chat() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}")));
        viewModel.ensureChatContact("user-1", "Alice", "AS-1000");
        viewModel.ensureChatContact("user-2", "Bob", "AS-2000");
        viewModel.incrementUnread("user-1");
        viewModel.incrementUnread("user-2");
        viewModel.setActiveTab(MainViewModel.MainTab.MESSAGES);

        MainViewModel.IncomingUnreadAction resetAction = viewModel.applyIncomingMessage("user-1", "user-1");
        MainViewModel.IncomingUnreadAction incrementAction = viewModel.applyIncomingMessage("user-2", "user-1");

        assertEquals(MainViewModel.IncomingUnreadAction.RESET, resetAction);
        assertEquals(MainViewModel.IncomingUnreadAction.INCREMENT, incrementAction);
        assertEquals(0, viewModel.getContactById("user-1").unreadCount());
        assertEquals(2, viewModel.getContactById("user-2").unreadCount());
    }

    @Test
    void reset_unread_on_chat_open_clears_badge_count() {
        MainViewModel viewModel = new MainViewModel(new StubContactsGateway(
                () -> CompletableFuture.completedFuture("{}")));
        viewModel.ensureChatContact("user-1", "Alice", "AS-1000");
        viewModel.incrementUnread("user-1");
        viewModel.incrementUnread("user-1");

        viewModel.resetUnreadOnChatOpen("user-1");

        assertEquals(0, viewModel.getContactById("user-1").unreadCount());
    }

    @Test
    void should_show_placeholder_after_removal_when_contacts_are_empty() {
        assertTrue(MainViewModel.shouldShowPlaceholderAfterRemoval(
                "user-1",
                null,
                null,
                true));
    }

    @Test
    void should_show_placeholder_after_removal_when_removed_contact_was_selected() {
        ContactInfo selectedBeforeRemoval = ContactInfo.active("user-1", "Alice", "AS-1000");

        assertTrue(MainViewModel.shouldShowPlaceholderAfterRemoval(
                "user-1",
                selectedBeforeRemoval,
                "other-user",
                false));
    }

    @Test
    void should_show_placeholder_after_removal_when_removed_contact_is_active_chat_recipient() {
        ContactInfo selectedBeforeRemoval = ContactInfo.active("user-2", "Bob", "AS-2000");

        assertTrue(MainViewModel.shouldShowPlaceholderAfterRemoval(
                "user-1",
                selectedBeforeRemoval,
                "user-1",
                false));
    }

    @Test
    void should_not_show_placeholder_after_removal_for_unrelated_contact_when_contacts_remain() {
        ContactInfo selectedBeforeRemoval = ContactInfo.active("user-2", "Bob", "AS-2000");

        assertFalse(MainViewModel.shouldShowPlaceholderAfterRemoval(
                "user-1",
                selectedBeforeRemoval,
                "user-3",
                false));
    }

    @Test
    void same_contact_selection_compares_by_contact_id() {
        ContactInfo clicked = new ContactInfo(
                "user-1",
                "Alice",
                "AS-1000",
                "SMINIAS",
                "alice@haf.gr",
                "6900000000",
                "2026-01-01",
                "Active",
                "#00b706",
                0);
        ContactInfo sameIdWithDifferentUnread = new ContactInfo(
                "user-1",
                "Alice",
                "AS-1000",
                "SMINIAS",
                "alice@haf.gr",
                "6900000000",
                "2026-01-01",
                "Active",
                "#00b706",
                7);

        assertTrue(MainViewModel.isSameContactSelection(clicked, sameIdWithDifferentUnread));
    }

    private static void awaitCondition(BooleanSupplier condition) {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return;
            }
            java.util.concurrent.locks.LockSupport.parkNanos(10_000_000L);
            if (Thread.interrupted()) {
                fail("Interrupted while waiting for async operation");
            }
        }
        fail("Timed out waiting for async operation");
    }

    private static final class StubContactsGateway implements MainViewModel.ContactsGateway {
        private final java.util.function.Supplier<CompletableFuture<String>> fetch;
        private final java.util.function.Function<String, CompletableFuture<String>> add;
        private final java.util.function.Function<String, CompletableFuture<String>> remove;

        private StubContactsGateway(java.util.function.Supplier<CompletableFuture<String>> fetch) {
            this(fetch, userId -> CompletableFuture.completedFuture("{}"),
                    userId -> CompletableFuture.completedFuture("{}"));
        }

        private StubContactsGateway(
                java.util.function.Supplier<CompletableFuture<String>> fetch,
                java.util.function.Function<String, CompletableFuture<String>> add,
                java.util.function.Function<String, CompletableFuture<String>> remove) {
            this.fetch = fetch;
            this.add = add;
            this.remove = remove;
        }

        @Override
        public CompletableFuture<String> fetchContacts() {
            return fetch.get();
        }

        @Override
        public CompletableFuture<String> addContact(String userId) {
            return add.apply(userId);
        }

        @Override
        public CompletableFuture<String> removeContact(String userId) {
            return remove.apply(userId);
        }
    }
}
