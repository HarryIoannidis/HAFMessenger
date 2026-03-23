package com.haf.client.controllers;

import com.haf.client.viewmodels.MainViewModel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void contact_context_destructive_actions_route_through_confirmation_gate() {
        AtomicInteger profileCalls = new AtomicInteger();
        AtomicInteger confirmDeleteCalls = new AtomicInteger();
        AtomicInteger confirmRemoveCalls = new AtomicInteger();

        MainController.applyContactContextActionWithConfirmation(
                MainController.ContactContextAction.PROFILE,
                profileCalls::incrementAndGet,
                confirmDeleteCalls::incrementAndGet,
                confirmRemoveCalls::incrementAndGet);
        assertEquals(1, profileCalls.get());
        assertEquals(0, confirmDeleteCalls.get());
        assertEquals(0, confirmRemoveCalls.get());

        MainController.applyContactContextActionWithConfirmation(
                MainController.ContactContextAction.DELETE_CHAT,
                profileCalls::incrementAndGet,
                confirmDeleteCalls::incrementAndGet,
                confirmRemoveCalls::incrementAndGet);
        assertEquals(1, profileCalls.get());
        assertEquals(1, confirmDeleteCalls.get());
        assertEquals(0, confirmRemoveCalls.get());

        MainController.applyContactContextActionWithConfirmation(
                MainController.ContactContextAction.REMOVE_CONTACT,
                profileCalls::incrementAndGet,
                confirmDeleteCalls::incrementAndGet,
                confirmRemoveCalls::incrementAndGet);
        assertEquals(1, profileCalls.get());
        assertEquals(1, confirmDeleteCalls.get());
        assertEquals(1, confirmRemoveCalls.get());
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
