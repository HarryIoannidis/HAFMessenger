package com.haf.client.controllers;

import com.haf.client.utils.RuntimeIssue;
import com.haf.client.viewmodels.MainViewModel;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainControllerTest {
    private static final Path CONTROLLER_SOURCE = Path.of("src/main/java/com/haf/client/controllers/MainController.java");

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

    @Test
    void messaging_runtime_issue_detection_matches_expected_namespace() {
        RuntimeIssue messagingIssue = new RuntimeIssue("messaging.send.failed", "x", "y", () -> {
        });
        RuntimeIssue searchIssue = new RuntimeIssue("search.request.failed", "x", "y", () -> {
        });

        assertTrue(MainController.isMessagingRuntimeIssue(messagingIssue));
        assertFalse(MainController.isMessagingRuntimeIssue(searchIssue));
        assertFalse(MainController.isMessagingRuntimeIssue(null));
    }

    @Test
    void auto_retry_excludes_retry_failed_issue_key() {
        RuntimeIssue sendIssue = new RuntimeIssue("messaging.send.failed", "x", "y", () -> {
        });
        RuntimeIssue retryFailedIssue = new RuntimeIssue("messaging.retry.failed", "x", "y", () -> {
        });

        assertTrue(MainController.shouldAutoRetryMessagingIssue(sendIssue));
        assertFalse(MainController.shouldAutoRetryMessagingIssue(retryFailedIssue));
    }

    @Test
    void runtime_popup_flow_is_gated_by_settings_toggle() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("if (!settings.isNotificationsShowRuntimePopups()) {"));
        assertTrue(source.contains("if (!runtimeIssuePopupGate.shouldShow(issue.dedupeKey())) {"));
    }

    @Test
    void restart_request_flow_logs_out_then_relaunches_launcher_process() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("private void requestAppRestart() {"));
        assertTrue(source.contains("mainSessionService.logout().whenComplete"));
        assertTrue(source.contains("boolean relaunched = relaunchClientProcess();"));
        assertTrue(source.contains("Launcher.class.getName()"));
        assertTrue(source.contains("Platform.exit();"));
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
