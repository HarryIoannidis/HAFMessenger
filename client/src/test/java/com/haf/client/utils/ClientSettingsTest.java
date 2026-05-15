package com.haf.client.utils;

import com.haf.client.viewmodels.SearchSortViewModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSettingsTest {

    private final List<String> createdUsers = new ArrayList<>();

    @AfterEach
    void cleanupUsers() {
        for (String userId : createdUsers) {
            try {
                removeUserNode(userId);
            } catch (Exception ignored) {
                // Ignore cleanup failures in restricted sandboxed environments.
            }
        }
        createdUsers.clear();
    }

    @Test
    void defaults_match_expected_values_general() {
        ClientSettings defaults = ClientSettings.defaults();

        assertGeneralDefaults(defaults);
    }

    @Test
    void defaults_match_expected_values_search() {
        ClientSettings defaults = ClientSettings.defaults();

        assertFalse(defaults.isSearchInstantOnType());
        assertFalse(defaults.isSearchAutoOpenFilterOnFirstSearch());
        assertTrue(defaults.isSearchRequireEnterToSearch());
        assertEquals(3, defaults.getSearchMinimumQueryLength());
        assertTrue(defaults.isSearchInfiniteScroll());
        assertEquals(100, defaults.getSearchResultsPerPage());
        assertTrue(defaults.isSearchPreserveLastQuery());
        assertTrue(defaults.isSearchRememberSortOptions());
        assertEquals(SearchSortViewModel.SortOptions.DEFAULT, defaults.getSearchSortOptions());
    }

    @Test
    void defaults_match_expected_values_media() {
        ClientSettings defaults = ClientSettings.defaults();

        assertTrue(defaults.isMediaHoverZoom());
        assertEquals(1.15, defaults.getMediaHoverZoomScale(), 0.0001);
        assertEquals(100, defaults.getMediaImageSendQuality());
        assertTrue(defaults.isMediaSendInMaxQuality());
        assertTrue(defaults.isMediaShowDownloadButton());
        assertTrue(defaults.isMediaOpenPreviewOnImageClick());
        assertTrue(defaults.isMediaShowImageFallbackPopup());
    }

    @Test
    void defaults_match_expected_values_chat_and_notifications() {
        ClientSettings defaults = ClientSettings.defaults();

        assertTrue(defaults.isChatSendOnEnter());
        assertTrue(defaults.isChatAutoScrollToLatest());
        assertTrue(defaults.isChatShowMessageTimestamps());
        assertTrue(defaults.isChatUse24HourTime());
        assertTrue(defaults.isNotificationsShowUnreadBadges());
        assertEquals(10, defaults.getNotificationsBadgeCap());
        assertTrue(defaults.isNotificationsShowOsNotifications());
        assertTrue(defaults.isNotificationsShowRuntimePopups());
    }

    @Test
    void defaults_match_expected_values_privacy() {
        ClientSettings defaults = ClientSettings.defaults();

        assertFalse(defaults.isPrivacyBlurOnFocusLoss());
        assertEquals(4.0, defaults.getPrivacyBlurStrength(), 0.0001);
        assertFalse(defaults.isPrivacyBlurOnStartupUntilUnlock());
        assertFalse(defaults.isPrivacyConfirmAttachmentOpen());
        assertTrue(defaults.isPrivacyConfirmExternalLinkOpen());
        assertFalse(defaults.isPrivacyShowNotificationMessagePreview());
        assertFalse(defaults.isPrivacyHidePresenceIndicators());
    }

    @Test
    void settings_are_isolated_per_user() throws Exception {
        Assumptions.assumeTrue(preferencesWritable(), "Preferences storage unavailable in this environment.");

        String userA = registerTestUser("settings-test-a");
        String userB = registerTestUser("settings-test-b");

        ClientSettings a = ClientSettings.forUser(userA);
        ClientSettings b = ClientSettings.forUser(userB);

        a.setGeneralConfirmExit(false);
        a.setAccountAutoRefreshToken(false);
        a.setGeneralConfirmLogout(false);
        a.setGeneralConfirmDeleteChat(false);
        a.setGeneralConfirmRemoveContact(false);
        a.setSearchResultsPerPage(80);
        a.setSearchMinimumQueryLength(5);
        a.setPrivacyHidePresenceIndicators(true);
        a.setPrivacyBlurOnStartupUntilUnlock(true);
        a.setPrivacyConfirmExternalLinkOpen(false);
        a.setSearchSortOptions(new SearchSortViewModel.SortOptions(
                SearchSortViewModel.Field.RANK,
                SearchSortViewModel.Direction.DESC));
        a.setSearchRememberSortOptions(false);

        assertCustomizedUserSettings(a);
        assertDefaultUserSettings(b);
    }

    @Test
    void slider_values_are_clamped() throws Exception {
        Assumptions.assumeTrue(preferencesWritable(), "Preferences storage unavailable in this environment.");

        String userId = registerTestUser("settings-clamp");
        ClientSettings settings = ClientSettings.forUser(userId);

        settings.setSearchResultsPerPage(7);
        assertEquals(10, settings.getSearchResultsPerPage());
        settings.setSearchResultsPerPage(46);
        assertEquals(50, settings.getSearchResultsPerPage());

        settings.setSearchMinimumQueryLength(0.1);
        assertEquals(3, settings.getSearchMinimumQueryLength());
        settings.setSearchMinimumQueryLength(5.9);
        assertEquals(5, settings.getSearchMinimumQueryLength());

        settings.setNotificationsBadgeCap(2);
        assertEquals(10, settings.getNotificationsBadgeCap());
        settings.setNotificationsBadgeCap(95);
        assertEquals(100, settings.getNotificationsBadgeCap());

        settings.setMediaHoverZoomScale(1.12);
        assertEquals(1.10, settings.getMediaHoverZoomScale(), 0.0001);
        settings.setMediaHoverZoomScale(6.0);
        assertEquals(5.00, settings.getMediaHoverZoomScale(), 0.0001);

        settings.setMediaImageSendQuality(59);
        assertEquals(60, settings.getMediaImageSendQuality());
        settings.setMediaImageSendQuality(93);
        assertEquals(95, settings.getMediaImageSendQuality());
        settings.setMediaImageSendQuality(101);
        assertEquals(100, settings.getMediaImageSendQuality());

        settings.setPrivacyBlurStrength(0.1);
        assertEquals(1.0, settings.getPrivacyBlurStrength(), 0.0001);
        settings.setPrivacyBlurStrength(8.6);
        assertEquals(9.0, settings.getPrivacyBlurStrength(), 0.0001);
    }

    @Test
    void persisted_values_reload() throws Exception {
        Assumptions.assumeTrue(preferencesWritable(), "Preferences storage unavailable in this environment.");

        String userId = registerTestUser("settings-reload");
        ClientSettings settings = ClientSettings.forUser(userId);

        settings.setGeneralConfirmExit(false);
        settings.setAccountAutoRefreshToken(false);
        settings.setGeneralConfirmLogout(false);
        settings.setGeneralConfirmDeleteChat(false);
        settings.setGeneralConfirmRemoveContact(false);
        settings.setChatSendOnEnter(false);
        settings.setChatUse24HourTime(false);
        settings.setSearchAutoOpenFilterOnFirstSearch(false);
        settings.setSearchRequireEnterToSearch(true);
        settings.setSearchRememberSortOptions(false);
        settings.setNotificationsShowOsNotifications(false);
        settings.setMediaShowImageFallbackPopup(false);
        settings.setMediaSendInMaxQuality(false);
        settings.setPrivacyBlurOnStartupUntilUnlock(true);
        settings.setPrivacyConfirmExternalLinkOpen(false);
        settings.setPrivacyShowNotificationMessagePreview(true);

        settings.setSearchResultsPerPage(46);
        settings.setSearchMinimumQueryLength(5.9);
        settings.setNotificationsBadgeCap(95);
        settings.setMediaHoverZoomScale(6.0);
        settings.setMediaImageSendQuality(63);
        settings.setPrivacyBlurStrength(8.6);

        ClientSettings reloaded = ClientSettings.forUser(userId);
        assertFalse(reloaded.isGeneralConfirmExit());
        assertFalse(reloaded.isAccountAutoRefreshToken());
        assertFalse(reloaded.isGeneralConfirmLogout());
        assertFalse(reloaded.isGeneralConfirmDeleteChat());
        assertFalse(reloaded.isGeneralConfirmRemoveContact());
        assertFalse(reloaded.isChatSendOnEnter());
        assertFalse(reloaded.isChatUse24HourTime());
        assertFalse(reloaded.isSearchAutoOpenFilterOnFirstSearch());
        assertTrue(reloaded.isSearchRequireEnterToSearch());
        assertFalse(reloaded.isSearchRememberSortOptions());
        assertFalse(reloaded.isNotificationsShowOsNotifications());
        assertFalse(reloaded.isMediaShowImageFallbackPopup());
        assertFalse(reloaded.isMediaSendInMaxQuality());
        assertEquals(50, reloaded.getSearchResultsPerPage());
        assertEquals(5, reloaded.getSearchMinimumQueryLength());
        assertEquals(100, reloaded.getNotificationsBadgeCap());
        assertEquals(5.00, reloaded.getMediaHoverZoomScale(), 0.0001);
        assertEquals(65, reloaded.getMediaImageSendQuality());
        assertEquals(9.0, reloaded.getPrivacyBlurStrength(), 0.0001);
        assertTrue(reloaded.isPrivacyBlurOnStartupUntilUnlock());
        assertFalse(reloaded.isPrivacyConfirmExternalLinkOpen());
        assertTrue(reloaded.isPrivacyShowNotificationMessagePreview());
    }

    @Test
    void search_sort_options_round_trip_and_invalid_values_fallback_to_default() throws Exception {
        Assumptions.assumeTrue(preferencesWritable(), "Preferences storage unavailable in this environment.");

        String userId = registerTestUser("settings-sort-options");
        ClientSettings settings = ClientSettings.forUser(userId);

        SearchSortViewModel.SortOptions selected = new SearchSortViewModel.SortOptions(
                SearchSortViewModel.Field.REG_NUMBER,
                SearchSortViewModel.Direction.DESC);
        settings.setSearchSortOptions(selected);
        settings.setSearchRememberSortOptions(true);

        ClientSettings reloaded = ClientSettings.forUser(userId);
        assertTrue(reloaded.isSearchRememberSortOptions());
        assertEquals(selected, reloaded.getSearchSortOptions());

        Preferences userNode = userNode(userId);
        userNode.put("search.sort.field", "not-a-field");
        userNode.put("search.sort.direction", "not-a-direction");
        userNode.flush();

        ClientSettings fallback = ClientSettings.forUser(userId);
        assertEquals(SearchSortViewModel.SortOptions.DEFAULT, fallback.getSearchSortOptions());

        fallback.setSearchSortOptions(new SearchSortViewModel.SortOptions(
                SearchSortViewModel.Field.RANK,
                SearchSortViewModel.Direction.DESC));
        fallback.setSearchRememberSortOptions(false);

        ClientSettings afterDisable = ClientSettings.forUser(userId);
        assertFalse(afterDisable.isSearchRememberSortOptions());
        assertEquals(SearchSortViewModel.SortOptions.DEFAULT, afterDisable.getSearchSortOptions());
        assertEquals(null, userNode.get("search.sort.field", null));
        assertEquals(null, userNode.get("search.sort.direction", null));
    }

    @Test
    void restart_required_dirty_tracking_only_toggles_for_restart_keys() throws Exception {
        Assumptions.assumeTrue(preferencesWritable(), "Preferences storage unavailable in this environment.");

        String userId = registerTestUser("settings-restart");
        ClientSettings settings = ClientSettings.forUser(userId);

        assertFalse(settings.isRestartRequiredDirty());

        settings.setChatSendOnEnter(false);
        assertFalse(settings.isRestartRequiredDirty());

        settings.setGeneralRememberWindowState(false);
        assertTrue(settings.isRestartRequiredDirty());

        settings.clearRestartRequiredDirty();
        assertFalse(settings.isRestartRequiredDirty());

        settings.setGeneralRestoreLastTab(false);
        assertTrue(settings.isRestartRequiredDirty());

        settings.setGeneralRestoreLastTab(true);
        assertFalse(settings.isRestartRequiredDirty());
    }

    private static void assertGeneralDefaults(ClientSettings defaults) {
        assertTrue(defaults.isAccountAutoRefreshToken());
        assertTrue(defaults.isGeneralConfirmExit());
        assertTrue(defaults.isGeneralConfirmLogout());
        assertTrue(defaults.isGeneralConfirmDeleteChat());
        assertTrue(defaults.isGeneralConfirmRemoveContact());
        assertTrue(defaults.isGeneralRememberWindowState());
        assertTrue(defaults.isGeneralRestoreLastTab());
    }

    private static void assertCustomizedUserSettings(ClientSettings settings) {
        assertFalse(settings.isAccountAutoRefreshToken());
        assertFalse(settings.isGeneralConfirmExit());
        assertFalse(settings.isGeneralConfirmLogout());
        assertFalse(settings.isGeneralConfirmDeleteChat());
        assertFalse(settings.isGeneralConfirmRemoveContact());
        assertEquals(80, settings.getSearchResultsPerPage());
        assertEquals(5, settings.getSearchMinimumQueryLength());
        assertTrue(settings.isPrivacyHidePresenceIndicators());
        assertTrue(settings.isPrivacyBlurOnStartupUntilUnlock());
        assertFalse(settings.isPrivacyConfirmExternalLinkOpen());
        assertFalse(settings.isSearchRememberSortOptions());
        assertEquals(SearchSortViewModel.SortOptions.DEFAULT, settings.getSearchSortOptions());
    }

    private static void assertDefaultUserSettings(ClientSettings settings) {
        assertTrue(settings.isAccountAutoRefreshToken());
        assertTrue(settings.isGeneralConfirmExit());
        assertTrue(settings.isGeneralConfirmLogout());
        assertTrue(settings.isGeneralConfirmDeleteChat());
        assertTrue(settings.isGeneralConfirmRemoveContact());
        assertEquals(100, settings.getSearchResultsPerPage());
        assertEquals(3, settings.getSearchMinimumQueryLength());
        assertTrue(settings.isSearchPreserveLastQuery());
        assertFalse(settings.isPrivacyHidePresenceIndicators());
        assertFalse(settings.isPrivacyBlurOnStartupUntilUnlock());
        assertTrue(settings.isPrivacyConfirmExternalLinkOpen());
        assertTrue(settings.isSearchRememberSortOptions());
        assertEquals(SearchSortViewModel.SortOptions.DEFAULT, settings.getSearchSortOptions());
    }

    private String registerTestUser(String prefix) throws Exception {
        String userId = prefix + "-" + UUID.randomUUID();
        createdUsers.add(userId);
        removeUserNode(userId);
        return userId;
    }

    private static void removeUserNode(String userId) throws Exception {
        Preferences usersNode = usersRootNode();
        String nodeName = sanitizeNodeForTest(userId);
        if (usersNode.nodeExists(nodeName)) {
            usersNode.node(nodeName).removeNode();
            usersNode.flush();
        }
    }

    private static boolean preferencesWritable() {
        Preferences usersNode = usersRootNode();
        String probeNode = "probe-" + UUID.randomUUID();
        try {
            Preferences probe = usersNode.node(probeNode);
            probe.putBoolean("writable", true);
            probe.flush();
            probe.removeNode();
            usersNode.flush();
            return true;
        } catch (Exception _) {
            return false;
        }
    }

    private static Preferences usersRootNode() {
        return Preferences.userNodeForPackage(ClientSettings.class)
                .node("client-settings")
                .node("users");
    }

    private static Preferences userNode(String userId) {
        return usersRootNode().node(sanitizeNodeForTest(userId));
    }

    private static String sanitizeNodeForTest(String userId) {
        if (userId == null) {
            return "user";
        }
        String sanitized = userId.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isBlank()) {
            return "user";
        }
        return sanitized;
    }
}
