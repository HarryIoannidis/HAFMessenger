package com.haf.client.controllers;

import com.haf.client.models.ContactInfo;
import com.haf.client.viewmodels.MainViewModel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainControllerTest {

    @Test
    void constructor_rejects_null_session_service() {
        assertThrows(NullPointerException.class, () -> new MainController(null));
    }

    @Test
    void contact_selection_decision_matrix_matches_expected_actions() {
        MainViewModel viewModel = new MainViewModel(new NoOpContactsGateway());

        assertEquals(MainViewModel.ContactSelectionAction.SWITCH_TO_MESSAGES_TAB,
                viewModel.resolveContactSelectionAction(MainViewModel.MainTab.SEARCH, false));
        assertEquals(MainViewModel.ContactSelectionAction.SWITCH_TO_MESSAGES_TAB,
                viewModel.resolveContactSelectionAction(MainViewModel.MainTab.SEARCH, true));
        assertEquals(MainViewModel.ContactSelectionAction.DESELECT_AND_SHOW_PLACEHOLDER,
                viewModel.resolveContactSelectionAction(MainViewModel.MainTab.MESSAGES, true));
        assertEquals(MainViewModel.ContactSelectionAction.KEEP_SELECTED_CONTACT,
                viewModel.resolveContactSelectionAction(MainViewModel.MainTab.MESSAGES, false));
    }

    @Test
    void apply_contact_selection_action_routes_to_expected_callback_without_fx_toolkit() {
        AtomicInteger switchCalls = new AtomicInteger();
        AtomicInteger deselectCalls = new AtomicInteger();
        AtomicInteger keepCalls = new AtomicInteger();

        MainController.applyContactSelectionAction(
                MainViewModel.ContactSelectionAction.SWITCH_TO_MESSAGES_TAB,
                switchCalls::incrementAndGet,
                deselectCalls::incrementAndGet,
                keepCalls::incrementAndGet);
        assertEquals(1, switchCalls.get());
        assertEquals(0, deselectCalls.get());
        assertEquals(0, keepCalls.get());

        MainController.applyContactSelectionAction(
                MainViewModel.ContactSelectionAction.DESELECT_AND_SHOW_PLACEHOLDER,
                switchCalls::incrementAndGet,
                deselectCalls::incrementAndGet,
                keepCalls::incrementAndGet);
        assertEquals(1, switchCalls.get());
        assertEquals(1, deselectCalls.get());
        assertEquals(0, keepCalls.get());

        MainController.applyContactSelectionAction(
                MainViewModel.ContactSelectionAction.KEEP_SELECTED_CONTACT,
                switchCalls::incrementAndGet,
                deselectCalls::incrementAndGet,
                keepCalls::incrementAndGet);
        assertEquals(1, switchCalls.get());
        assertEquals(1, deselectCalls.get());
        assertEquals(1, keepCalls.get());
    }

    @Test
    void contact_context_action_routes_to_expected_callback() {
        AtomicInteger profileCalls = new AtomicInteger();
        AtomicInteger deleteChatCalls = new AtomicInteger();
        AtomicInteger removeContactCalls = new AtomicInteger();

        MainController.applyContactContextAction(
                MainController.ContactContextAction.PROFILE,
                profileCalls::incrementAndGet,
                deleteChatCalls::incrementAndGet,
                removeContactCalls::incrementAndGet);
        assertEquals(1, profileCalls.get());
        assertEquals(0, deleteChatCalls.get());
        assertEquals(0, removeContactCalls.get());

        MainController.applyContactContextAction(
                MainController.ContactContextAction.DELETE_CHAT,
                profileCalls::incrementAndGet,
                deleteChatCalls::incrementAndGet,
                removeContactCalls::incrementAndGet);
        assertEquals(1, profileCalls.get());
        assertEquals(1, deleteChatCalls.get());
        assertEquals(0, removeContactCalls.get());

        MainController.applyContactContextAction(
                MainController.ContactContextAction.REMOVE_CONTACT,
                profileCalls::incrementAndGet,
                deleteChatCalls::incrementAndGet,
                removeContactCalls::incrementAndGet);
        assertEquals(1, profileCalls.get());
        assertEquals(1, deleteChatCalls.get());
        assertEquals(1, removeContactCalls.get());
    }

    @Test
    void should_show_placeholder_after_removal_when_contacts_are_empty() {
        assertTrue(MainController.shouldShowPlaceholderAfterRemoval(
                "user-1",
                null,
                null,
                true));
    }

    @Test
    void should_show_placeholder_after_removal_when_removed_contact_was_selected() {
        ContactInfo selectedBeforeRemoval = ContactInfo.active("user-1", "Alice", "AS-1000");

        assertTrue(MainController.shouldShowPlaceholderAfterRemoval(
                "user-1",
                selectedBeforeRemoval,
                "other-user",
                false));
    }

    @Test
    void should_show_placeholder_after_removal_when_removed_contact_is_active_chat_recipient() {
        ContactInfo selectedBeforeRemoval = ContactInfo.active("user-2", "Bob", "AS-2000");

        assertTrue(MainController.shouldShowPlaceholderAfterRemoval(
                "user-1",
                selectedBeforeRemoval,
                "user-1",
                false));
    }

    @Test
    void should_not_show_placeholder_after_removal_for_unrelated_contact_when_contacts_remain() {
        ContactInfo selectedBeforeRemoval = ContactInfo.active("user-2", "Bob", "AS-2000");

        assertFalse(MainController.shouldShowPlaceholderAfterRemoval(
                "user-1",
                selectedBeforeRemoval,
                "user-3",
                false));
    }

    @Test
    void incoming_unread_action_resets_when_active_chat_is_open() {
        MainController.UnreadAction action = MainController.resolveUnreadActionOnIncoming(
                MainViewModel.MainTab.MESSAGES,
                "user-1",
                "user-1");

        assertEquals(MainController.UnreadAction.RESET, action);
    }

    @Test
    void incoming_unread_action_increments_when_chat_is_not_open() {
        MainController.UnreadAction actionInSearchTab = MainController.resolveUnreadActionOnIncoming(
                MainViewModel.MainTab.SEARCH,
                "user-1",
                "user-1");
        MainController.UnreadAction actionForDifferentOpenChat = MainController.resolveUnreadActionOnIncoming(
                MainViewModel.MainTab.MESSAGES,
                "user-1",
                "user-2");

        assertEquals(MainController.UnreadAction.INCREMENT, actionInSearchTab);
        assertEquals(MainController.UnreadAction.INCREMENT, actionForDifferentOpenChat);
    }

    @Test
    void reset_unread_on_chat_open_clears_badge_count() {
        MainViewModel viewModel = new MainViewModel(new NoOpContactsGateway());
        viewModel.ensureChatContact("user-1", "Alice", "AS-1000");
        viewModel.incrementUnread("user-1");
        viewModel.incrementUnread("user-1");

        MainController.resetUnreadOnChatOpen(viewModel, "user-1");

        assertEquals(0, viewModel.getContactById("user-1").unreadCount());
    }

    @Test
    void ensure_incoming_contact_auto_adds_unknown_sender_with_placeholder_profile_fields() {
        MainViewModel viewModel = new MainViewModel(new NoOpContactsGateway());

        ContactInfo created = MainController.ensureIncomingContact(viewModel, "sender-42");

        assertEquals("sender-42", created.id());
        assertEquals("Unknown Contact", created.name());
        assertEquals("", created.regNumber());
        assertEquals(0, created.unreadCount());
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

        assertTrue(MainController.isSameContactSelection(clicked, sameIdWithDifferentUnread));
    }

    private static final class NoOpContactsGateway implements MainViewModel.ContactsGateway {
        @Override
        public CompletableFuture<String> fetchContacts() {
            return CompletableFuture.completedFuture("{}");
        }

        @Override
        public CompletableFuture<String> addContact(String userId) {
            return CompletableFuture.completedFuture("{}");
        }

        @Override
        public CompletableFuture<String> removeContact(String userId) {
            return CompletableFuture.completedFuture("{}");
        }
    }
}
