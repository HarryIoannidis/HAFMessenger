package com.haf.client.utils;

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
    void defaults_match_expected_values() {
        ClientSettings defaults = ClientSettings.defaults();

        assertTrue(defaults.isGeneralConfirmExit());
        assertTrue(defaults.isGeneralRememberWindowState());
        assertTrue(defaults.isGeneralRestoreLastTab());
        assertFalse(defaults.isSearchInstantOnType());
        assertTrue(defaults.isSearchAutoOpenFilterOnFirstSearch());
        assertTrue(defaults.isSearchInfiniteScroll());
        assertEquals(20, defaults.getSearchResultsPerPage());
        assertFalse(defaults.isSearchPreserveLastQuery());
        assertTrue(defaults.isMediaHoverZoom());
        assertEquals(1.15, defaults.getMediaHoverZoomScale(), 0.0001);
        assertTrue(defaults.isMediaShowDownloadButton());
        assertTrue(defaults.isMediaOpenPreviewOnImageClick());
        assertTrue(defaults.isChatSendOnEnter());
        assertTrue(defaults.isChatAutoScrollToLatest());
        assertTrue(defaults.isChatShowMessageTimestamps());
        assertTrue(defaults.isNotificationsShowUnreadBadges());
        assertEquals(10, defaults.getNotificationsBadgeCap());
        assertTrue(defaults.isNotificationsShowRuntimePopups());
        assertFalse(defaults.isPrivacyBlurOnFocusLoss());
        assertEquals(4.0, defaults.getPrivacyBlurStrength(), 0.0001);
        assertFalse(defaults.isPrivacyConfirmAttachmentOpen());
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
        a.setSearchResultsPerPage(80);
        a.setPrivacyHidePresenceIndicators(true);

        assertFalse(a.isGeneralConfirmExit());
        assertEquals(80, a.getSearchResultsPerPage());
        assertTrue(a.isPrivacyHidePresenceIndicators());

        assertTrue(b.isGeneralConfirmExit());
        assertEquals(20, b.getSearchResultsPerPage());
        assertFalse(b.isPrivacyHidePresenceIndicators());
    }

    @Test
    void persisted_values_reload_and_slider_values_are_clamped() throws Exception {
        Assumptions.assumeTrue(preferencesWritable(), "Preferences storage unavailable in this environment.");

        String userId = registerTestUser("settings-clamp");
        ClientSettings settings = ClientSettings.forUser(userId);

        settings.setSearchResultsPerPage(7);
        assertEquals(10, settings.getSearchResultsPerPage());
        settings.setSearchResultsPerPage(46);
        assertEquals(50, settings.getSearchResultsPerPage());

        settings.setNotificationsBadgeCap(2);
        assertEquals(10, settings.getNotificationsBadgeCap());
        settings.setNotificationsBadgeCap(95);
        assertEquals(100, settings.getNotificationsBadgeCap());

        settings.setMediaHoverZoomScale(1.12);
        assertEquals(1.10, settings.getMediaHoverZoomScale(), 0.0001);
        settings.setMediaHoverZoomScale(2.0);
        assertEquals(1.50, settings.getMediaHoverZoomScale(), 0.0001);

        settings.setPrivacyBlurStrength(0.1);
        assertEquals(1.0, settings.getPrivacyBlurStrength(), 0.0001);
        settings.setPrivacyBlurStrength(8.6);
        assertEquals(9.0, settings.getPrivacyBlurStrength(), 0.0001);

        settings.setGeneralConfirmExit(false);
        settings.setChatSendOnEnter(false);
        settings.setSearchAutoOpenFilterOnFirstSearch(false);

        ClientSettings reloaded = ClientSettings.forUser(userId);
        assertFalse(reloaded.isGeneralConfirmExit());
        assertFalse(reloaded.isChatSendOnEnter());
        assertFalse(reloaded.isSearchAutoOpenFilterOnFirstSearch());
        assertEquals(50, reloaded.getSearchResultsPerPage());
        assertEquals(100, reloaded.getNotificationsBadgeCap());
        assertEquals(1.50, reloaded.getMediaHoverZoomScale(), 0.0001);
        assertEquals(9.0, reloaded.getPrivacyBlurStrength(), 0.0001);
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

    private String registerTestUser(String prefix) throws Exception {
        String userId = prefix + "-" + UUID.randomUUID();
        createdUsers.add(userId);
        removeUserNode(userId);
        return userId;
    }

    private static void removeUserNode(String userId) throws Exception {
        Preferences usersNode = Preferences.userNodeForPackage(ClientSettings.class)
                .node("client-settings")
                .node("users");
        if (usersNode.nodeExists(userId)) {
            usersNode.node(userId).removeNode();
            usersNode.flush();
        }
    }

    private static boolean preferencesWritable() {
        Preferences usersNode = Preferences.userNodeForPackage(ClientSettings.class)
                .node("client-settings")
                .node("users");
        String probeNode = "probe-" + UUID.randomUUID();
        try {
            Preferences probe = usersNode.node(probeNode);
            probe.putBoolean("writable", true);
            probe.flush();
            probe.removeNode();
            usersNode.flush();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
