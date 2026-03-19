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
