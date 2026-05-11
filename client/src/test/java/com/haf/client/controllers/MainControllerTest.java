package com.haf.client.controllers;

import com.haf.client.models.ContactInfo;
import com.haf.client.models.MessageType;
import com.haf.client.models.MessageVM;
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
    private static final Path CONTROLLER_SOURCE = Path
            .of("src/main/java/com/haf/client/controllers/MainController.java");
    private static final Path MAIN_FXML = Path.of("src/main/resources/fxml/main.fxml");

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
    void connection_runtime_issue_flow_uses_dedicated_popup_and_mandatory_branch() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("if (issue.connectionIssue()) {"));
        assertTrue(source.contains("handleConnectionLossRuntimeIssue(issue);"));
        assertTrue(source.contains(".popupKey(UiConstants.POPUP_CONNECTION_LOSS)"));
        assertTrue(source.contains(".actionText(\"Retry\")"));
        assertTrue(source.contains(".cancelText(\"Close and Exit\")"));
        assertTrue(source.contains(".movable(false)"));
        assertTrue(source.contains(".onCancel(this::exitApplication)"));
        assertTrue(source.contains("boolean messagingChannelIssue = isMessagingChannelIssue(issue);"));
        assertTrue(source.contains(".cancelText(messagingChannelIssue ? \"Close and Exit\" : \"Dismiss\")"));
    }

    @Test
    void connection_runtime_retry_loop_runs_every_five_seconds_and_supports_manual_retry() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("private static final long CONNECTION_RECOVERY_RETRY_SECONDS = 5L;"));
        assertTrue(source.contains("scheduleWithFixedDelay("));
        assertTrue(source.contains("CONNECTION_RECOVERY_RETRY_SECONDS"));
        assertFalse(source.contains("connectionRecoveryDismissed"));
        assertTrue(source.contains("triggerImmediateConnectionRecoveryAttempt();"));
        assertTrue(source.contains("runRuntimeIssueRetry(currentIssue.retryAction())"));
    }

    @Test
    void typing_indicator_is_rendered_next_to_active_profile_name() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);
        String fxml = Files.readString(MAIN_FXML);

        assertTrue(source.contains("registerTypingListener();"));
        assertTrue(source.contains("typingContactIds"));
        assertTrue(source.contains("profileTypingText.setText(visible ? \"• Typing...\" : \"\");"));
        assertTrue(fxml.contains("fx:id=\"profileTypingText\""));
        assertTrue(fxml.contains("text=\"• Typing...\""));
    }

    @Test
    void runtime_popup_flow_is_gated_by_settings_toggle() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("if (!settings.isNotificationsShowRuntimePopups()) {"));
        assertTrue(source.contains("return runtimeIssuePopupGate.shouldShow(issue.dedupeKey());"));
    }

    @Test
    void logout_confirmation_flow_respects_general_confirm_logout_setting() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("if (!settings.isGeneralConfirmLogout()) {"));
        assertTrue(source.contains(".popupKey(UiConstants.POPUP_CONFIRM_LOGOUT)"));
        assertTrue(source.contains(".onAction(this::handleLogout)"));
    }

    @Test
    void destructive_contact_actions_respect_individual_confirmation_settings() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("if (!settings.isGeneralConfirmDeleteChat()) {"));
        assertTrue(source.contains("if (!settings.isGeneralConfirmRemoveContact()) {"));
        assertTrue(source.contains(".popupKey(UiConstants.POPUP_CONFIRM_DELETE_CHAT)"));
        assertTrue(source.contains(".popupKey(UiConstants.POPUP_CONFIRM_REMOVE_CONTACT)"));
    }

    @Test
    void incoming_os_notification_decision_matrix_matches_expected_behavior() {
        assertFalse(MainController.shouldShowIncomingOsNotification(
                false,
                MainViewModel.IncomingUnreadAction.INCREMENT,
                false));

        assertFalse(MainController.shouldShowIncomingOsNotification(
                true,
                MainViewModel.IncomingUnreadAction.RESET,
                true));

        assertTrue(MainController.shouldShowIncomingOsNotification(
                true,
                MainViewModel.IncomingUnreadAction.INCREMENT,
                true));

        assertTrue(MainController.shouldShowIncomingOsNotification(
                true,
                MainViewModel.IncomingUnreadAction.RESET,
                false));
    }

    @Test
    void incoming_os_notification_body_respects_preview_toggle_and_type_mapping() {
        MessageVM text = new MessageVM(false, MessageType.TEXT, "  Body with\npreview  ", null, null, null, null,
                false);
        MessageVM image = new MessageVM(false, MessageType.IMAGE, null, null, "a.png", null, null, false);
        MessageVM file = new MessageVM(false, MessageType.FILE, null, "/tmp/a", "a.pdf", "10 KB", null, false);

        assertEquals("sent a text", MainController.resolveIncomingOsNotificationBody(text, false));
        assertEquals("sent an image", MainController.resolveIncomingOsNotificationBody(image, false));
        assertEquals("sent a file", MainController.resolveIncomingOsNotificationBody(file, false));
        assertEquals("sent a message", MainController.resolveIncomingOsNotificationBody(null, false));

        assertEquals("Body with preview", MainController.resolveIncomingOsNotificationBody(text, true));
        assertEquals("sent an image", MainController.resolveIncomingOsNotificationBody(image, true));
        assertEquals("sent a file", MainController.resolveIncomingOsNotificationBody(file, true));
    }

    @Test
    void incoming_os_notification_title_uses_contact_name_or_unknown_fallback() {
        ContactInfo named = ContactInfo.online("u-1", "Alice Pilot", "R1");
        ContactInfo blank = ContactInfo.online("u-2", "  ", "R2");

        assertEquals("Alice Pilot", MainController.resolveIncomingOsNotificationTitle(named));
        assertEquals("Unknown Contact", MainController.resolveIncomingOsNotificationTitle(blank));
        assertEquals("Unknown Contact", MainController.resolveIncomingOsNotificationTitle(null));
    }

    @Test
    void incoming_os_notification_flow_uses_setting_gate_and_click_navigation_path() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("settings.isNotificationsShowOsNotifications()"));
        assertTrue(source.contains("settings.isPrivacyShowNotificationMessagePreview()"));
        assertTrue(source.contains("unreadAction == MainViewModel.IncomingUnreadAction.INCREMENT || !windowFocused"));
        assertTrue(source.contains("desktopNotificationService.showNotification("));
        assertTrue(source.contains("() -> Platform.runLater(() -> focusAppAndOpenSenderChat(senderId))"));
        assertTrue(source.contains("stage.toFront();"));
        assertTrue(source.contains("selectContactAndOpenChat(target);"));
    }

    @Test
    void search_trigger_modes_follow_enter_and_instant_search_settings() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("if (settings.isSearchRequireEnterToSearch()) {"));
        assertTrue(source.contains("if (settings.isSearchRequireEnterToSearch()"));
        assertTrue(source.contains("normalized.length() < settings.getSearchMinimumQueryLength()"));
        assertTrue(source.contains("isSearchQueryTooShort("));
    }

    @Test
    void remember_search_sort_setting_is_loaded_saved_and_reset() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("if (settings.isSearchRememberSortOptions()) {"));
        assertTrue(source.contains("settings.setSearchSortOptions(sortOptions);"));
        assertTrue(source.contains("settings.clearSearchSortOptions();"));
        assertTrue(source.contains("searchFilterUi.setSelectedSortOptions(settings.getSearchSortOptions());"));
        assertTrue(source.contains("searchFilterUi.setSelectedSortOptions(SearchSortViewModel.SortOptions.DEFAULT);"));
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

    @Test
    void startup_blur_lock_and_external_link_opening_are_gated_by_settings() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("syncStartupBlurLockFromSetting();"));
        assertTrue(source
                .contains("String blurActionText = blurLocked ? \"Unlock Privacy Blur\" : \"Lock Privacy Blur\";"));
        assertTrue(source.contains(
                "Runnable blurAction = blurLocked ? this::unlockStartupPrivacyBlur : this::lockStartupPrivacyBlur;"));
        assertTrue(source.contains("if (startupBlurLocked) {"));
        assertTrue(source.contains("settings.isPrivacyBlurOnStartupUntilUnlock()"));
        assertTrue(source.contains("scheduleStartupBlurUnlockPopupAfterMainRender()"));
        assertTrue(source.contains(".movable(false)"));
        assertTrue(source.contains("requestExternalLinkOpen("));
        assertTrue(source.contains("if (!settings.isPrivacyConfirmExternalLinkOpen()) {"));
        assertTrue(source.contains("Desktop.getDesktop().browse(new URI(url));"));
    }

    @Test
    void hide_presence_setting_is_applied_locally_on_contact_cells_and_profile_pane() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains(
                "ContactCellController.setHidePresenceIndicators(settings.isPrivacyHidePresenceIndicators())"));
        assertTrue(source.contains("private static final String HIDDEN_ACTIVITY_LABEL = \"Hidden Activity\";"));
        assertTrue(source.contains("""
                settings.isPrivacyHidePresenceIndicators()
                                ? HIDDEN_ACTIVITY_LABEL
                                : activenessLabel"""));
        assertTrue(source.contains("hasActivenessLabel && !settings.isPrivacyHidePresenceIndicators()"));
    }

    @Test
    void manual_refresh_failure_message_uses_default_for_empty_or_generic_errors() {
        assertEquals(
                "Could not refresh your session. Please log in again.",
                MainController.resolveManualRefreshFailureMessage(null));
        assertEquals(
                "Could not refresh your session. Please log in again.",
                MainController.resolveManualRefreshFailureMessage(""));
        assertEquals(
                "Could not refresh your session. Please log in again.",
                MainController.resolveManualRefreshFailureMessage("token refresh failed"));
    }

    @Test
    void manual_refresh_failure_message_includes_specific_error_details() {
        assertEquals(
                "Could not refresh your session (internal server error). Please log in again.",
                MainController.resolveManualRefreshFailureMessage(" internal server error "));
    }

    @Test
    void successful_refresh_after_expired_session_restores_messaging_transport() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("boolean recoveredExpiredSession = sessionExpired.get();"));
        assertTrue(source.contains("if (recoveredExpiredSession) {"));
        assertTrue(source.contains("restoreMessagingTransportAfterSessionRefresh();"));
        assertTrue(source.contains("chatViewModel.restoreReceiverTransportAfterSessionRefresh();"));
    }

    @Test
    void takeover_session_flow_uses_forced_logout_popup_without_refresh_option() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("handleConfirmedInvalidSession(result.errorMessage())"));
        assertTrue(source.contains("private void handleSessionTakeoverIssue() {"));
        assertTrue(source.contains(".popupKey(POPUP_SESSION_TAKEOVER)"));
        assertTrue(source.contains(".title(\"Logged out\")"));
        assertTrue(source.contains(".message(\"A new device logged into this account. You have been logged out.\")"));
        assertTrue(source.contains(".actionText(\"Go to Login\")"));
        assertTrue(source.contains(".singleAction(true)"));
    }

    @Test
    void revoked_session_issue_attempts_silent_recovery_when_auto_refresh_is_enabled() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("if (autoRefreshTokenEnabled.get()) {"));
        assertTrue(source.contains("attemptSilentRecoveryForRevokedSessionIssue();"));
        assertTrue(source.contains("private void attemptSilentRecoveryForRevokedSessionIssue() {"));
        assertTrue(source.contains("handleConnectionLossRuntimeIssue(new RuntimeIssue("));
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
