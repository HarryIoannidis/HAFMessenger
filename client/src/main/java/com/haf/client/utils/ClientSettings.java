package com.haf.client.utils;

import com.haf.client.core.CurrentUserSession;
import com.haf.client.models.UserProfileInfo;
import com.haf.client.viewmodels.SearchSortViewModel;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;

/**
 * Per-user client settings backed by {@link Preferences}.
 */
public final class ClientSettings {

    /**
     * Apply policy for a setting key.
     */
    public enum ApplyMode {
        IMMEDIATE,
        RESTART_REQUIRED
    }

    /**
     * Setting key metadata (type/default/apply-mode).
     */
    public enum Key {
        GENERAL_CONFIRM_EXIT("general.confirm_exit", true, ApplyMode.IMMEDIATE),
        GENERAL_CONFIRM_LOGOUT("general.confirm_logout", true, ApplyMode.IMMEDIATE),
        GENERAL_CONFIRM_DELETE_CHAT("general.confirm_delete_chat", true, ApplyMode.IMMEDIATE),
        GENERAL_CONFIRM_REMOVE_CONTACT("general.confirm_remove_contact", true, ApplyMode.IMMEDIATE),
        GENERAL_REMEMBER_WINDOW_STATE("general.remember_window_state", true, ApplyMode.RESTART_REQUIRED),
        GENERAL_RESTORE_LAST_TAB("general.restore_last_tab", true, ApplyMode.RESTART_REQUIRED),

        SEARCH_INSTANT_ON_TYPE("search.instant_on_type", false, ApplyMode.IMMEDIATE),
        SEARCH_AUTO_OPEN_FILTER_ON_FIRST_SEARCH("search.auto_open_filter_on_first_search", true, ApplyMode.IMMEDIATE),
        SEARCH_REQUIRE_ENTER_TO_SEARCH("search.require_enter_to_search", true, ApplyMode.IMMEDIATE),
        SEARCH_MINIMUM_QUERY_LENGTH("search.minimum_query_length", 3, ApplyMode.IMMEDIATE),
        SEARCH_INFINITE_SCROLL("search.infinite_scroll", true, ApplyMode.IMMEDIATE),
        SEARCH_RESULTS_PER_PAGE("search.results_per_page", 20, ApplyMode.IMMEDIATE),
        SEARCH_PRESERVE_LAST_QUERY("search.preserve_last_query", false, ApplyMode.IMMEDIATE),
        SEARCH_REMEMBER_SORT_OPTIONS("search.remember_sort_options", true, ApplyMode.IMMEDIATE),

        MEDIA_HOVER_ZOOM("media.hover_zoom", true, ApplyMode.IMMEDIATE),
        MEDIA_HOVER_ZOOM_SCALE("media.hover_zoom_scale", 1.15, ApplyMode.IMMEDIATE),
        MEDIA_SHOW_DOWNLOAD_BUTTON("media.show_download_button", true, ApplyMode.IMMEDIATE),
        MEDIA_OPEN_PREVIEW_ON_IMAGE_CLICK("media.open_preview_on_image_click", true, ApplyMode.IMMEDIATE),

        CHAT_SEND_ON_ENTER("chat.send_on_enter", true, ApplyMode.IMMEDIATE),
        CHAT_AUTO_SCROLL_TO_LATEST("chat.auto_scroll_to_latest", true, ApplyMode.IMMEDIATE),
        CHAT_SHOW_MESSAGE_TIMESTAMPS("chat.show_message_timestamps", true, ApplyMode.IMMEDIATE),
        CHAT_USE_24_HOUR_TIME("chat.use_24_hour_time", true, ApplyMode.IMMEDIATE),

        NOTIFICATIONS_SHOW_UNREAD_BADGES("notifications.show_unread_badges", true, ApplyMode.IMMEDIATE),
        NOTIFICATIONS_BADGE_CAP("notifications.badge_cap", 10, ApplyMode.IMMEDIATE),
        NOTIFICATIONS_SHOW_OS_NOTIFICATIONS("notifications.show_os_notifications", true, ApplyMode.IMMEDIATE),
        NOTIFICATIONS_SHOW_RUNTIME_POPUPS("notifications.show_runtime_popups", true, ApplyMode.IMMEDIATE),

        PRIVACY_BLUR_ON_FOCUS_LOSS("privacy.blur_on_focus_loss", false, ApplyMode.IMMEDIATE),
        PRIVACY_BLUR_STRENGTH("privacy.blur_strength", 4.0, ApplyMode.IMMEDIATE),
        PRIVACY_BLUR_ON_STARTUP_UNTIL_UNLOCK("privacy.blur_on_startup_until_unlock", false, ApplyMode.IMMEDIATE),
        PRIVACY_CONFIRM_ATTACHMENT_OPEN("privacy.confirm_attachment_open", false, ApplyMode.IMMEDIATE),
        PRIVACY_CONFIRM_EXTERNAL_LINK_OPEN("privacy.confirm_external_link_open", true, ApplyMode.IMMEDIATE),
        PRIVACY_SHOW_NOTIFICATION_MESSAGE_PREVIEW("privacy.show_notification_message_preview", false, ApplyMode.IMMEDIATE),
        PRIVACY_HIDE_PRESENCE_INDICATORS("privacy.hide_presence_indicators", false, ApplyMode.IMMEDIATE);

        private final String preferenceKey;
        private final Object defaultValue;
        private final ApplyMode applyMode;

        /**
         * Creates a new Key instance.
         */
        Key(String preferenceKey, Object defaultValue, ApplyMode applyMode) {
            this.preferenceKey = preferenceKey;
            this.defaultValue = defaultValue;
            this.applyMode = applyMode;
        }

        /**
         * Handles preference key.
         */
        public String preferenceKey() {
            return preferenceKey;
        }

        /**
         * Handles default value.
         */
        public Object defaultValue() {
            return defaultValue;
        }

        /**
         * Applies mode.
         */
        public ApplyMode applyMode() {
            return applyMode;
        }
    }

    /**
     * Listener notified when a setting changes.
     */
    @FunctionalInterface
    public interface Listener {
        /**
         * Handles on setting changed.
         */
        void onSettingChanged(Key key);
    }

    /**
     * Persisted window state values.
     */
    public record WindowState(double x, double y, double width, double height, boolean maximized) {
    }

    private static final String PREF_SEARCH_SORT_FIELD = "search.sort.field";
    private static final String PREF_SEARCH_SORT_DIRECTION = "search.sort.direction";

    private final Preferences prefs;
    private final boolean persistent;
    private final Map<Key, Object> values = new EnumMap<>(Key.class);
    private final Map<Key, Object> restartBaseline = new EnumMap<>(Key.class);
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private SearchSortViewModel.SortOptions searchSortOptions;

    private volatile boolean restartRequiredDirty;

    /**
     * Creates a new ClientSettings instance.
     */
    private ClientSettings(String userId) {
        if (userId == null || userId.isBlank()) {
            this.prefs = null;
            this.persistent = false;
        } else {
            this.prefs = Preferences.userNodeForPackage(ClientSettings.class)
                    .node("client-settings")
                    .node("users")
                    .node(sanitizeNode(userId));
            this.persistent = true;
        }

        for (Key key : Key.values()) {
            Object loaded = loadValue(key);
            values.put(key, loaded);
            if (key.applyMode() == ApplyMode.RESTART_REQUIRED) {
                restartBaseline.put(key, loaded);
            }
        }
        this.searchSortOptions = loadSearchSortOptions();
        this.restartRequiredDirty = false;
    }

    /**
     * Handles defaults.
     */
    public static ClientSettings defaults() {
        return new ClientSettings(null);
    }

    /**
     * Creates settings for user.
     */
    public static ClientSettings forUser(String userId) {
        return new ClientSettings(userId);
    }

    /**
     * Resolves settings for current session user, or defaults when not logged in.
     */
    public static ClientSettings forCurrentUserOrDefaults() {
        UserProfileInfo profile = CurrentUserSession.get();
        if (profile == null || profile.userId() == null || profile.userId().isBlank()) {
            return defaults();
        }
        return forUser(profile.userId());
    }

    /**
     * Adds listener.
     */
    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Removes listener.
     */
    public void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Returns whether restart required dirty.
     */
    public boolean isRestartRequiredDirty() {
        return restartRequiredDirty;
    }

    /**
     * Clears restart required dirty.
     */
    public void clearRestartRequiredDirty() {
        for (Key key : Key.values()) {
            if (key.applyMode() == ApplyMode.RESTART_REQUIRED) {
                restartBaseline.put(key, values.get(key));
            }
        }
        restartRequiredDirty = false;
    }

    /**
     * Returns whether general confirm exit.
     */
    public boolean isGeneralConfirmExit() {
        return getBoolean(Key.GENERAL_CONFIRM_EXIT);
    }

    /**
     * Updates general confirm exit.
     */
    public void setGeneralConfirmExit(boolean enabled) {
        setBoolean(Key.GENERAL_CONFIRM_EXIT, enabled);
    }

    /**
     * Returns whether general confirm logout.
     */
    public boolean isGeneralConfirmLogout() {
        return getBoolean(Key.GENERAL_CONFIRM_LOGOUT);
    }

    /**
     * Updates general confirm logout.
     */
    public void setGeneralConfirmLogout(boolean enabled) {
        setBoolean(Key.GENERAL_CONFIRM_LOGOUT, enabled);
    }

    /**
     * Returns whether general confirm delete chat.
     */
    public boolean isGeneralConfirmDeleteChat() {
        return getBoolean(Key.GENERAL_CONFIRM_DELETE_CHAT);
    }

    /**
     * Updates general confirm delete chat.
     */
    public void setGeneralConfirmDeleteChat(boolean enabled) {
        setBoolean(Key.GENERAL_CONFIRM_DELETE_CHAT, enabled);
    }

    /**
     * Returns whether general confirm remove contact.
     */
    public boolean isGeneralConfirmRemoveContact() {
        return getBoolean(Key.GENERAL_CONFIRM_REMOVE_CONTACT);
    }

    /**
     * Updates general confirm remove contact.
     */
    public void setGeneralConfirmRemoveContact(boolean enabled) {
        setBoolean(Key.GENERAL_CONFIRM_REMOVE_CONTACT, enabled);
    }

    /**
     * Returns whether general remember window state.
     */
    public boolean isGeneralRememberWindowState() {
        return getBoolean(Key.GENERAL_REMEMBER_WINDOW_STATE);
    }

    /**
     * Updates general remember window state.
     */
    public void setGeneralRememberWindowState(boolean enabled) {
        setBoolean(Key.GENERAL_REMEMBER_WINDOW_STATE, enabled);
    }

    /**
     * Returns whether general restore last tab.
     */
    public boolean isGeneralRestoreLastTab() {
        return getBoolean(Key.GENERAL_RESTORE_LAST_TAB);
    }

    /**
     * Updates general restore last tab.
     */
    public void setGeneralRestoreLastTab(boolean enabled) {
        setBoolean(Key.GENERAL_RESTORE_LAST_TAB, enabled);
    }

    /**
     * Returns whether search instant on type.
     */
    public boolean isSearchInstantOnType() {
        return getBoolean(Key.SEARCH_INSTANT_ON_TYPE);
    }

    /**
     * Updates search instant on type.
     */
    public void setSearchInstantOnType(boolean enabled) {
        setBoolean(Key.SEARCH_INSTANT_ON_TYPE, enabled);
    }

    /**
     * Returns whether search auto open filter on first search.
     */
    public boolean isSearchAutoOpenFilterOnFirstSearch() {
        return getBoolean(Key.SEARCH_AUTO_OPEN_FILTER_ON_FIRST_SEARCH);
    }

    /**
     * Updates search auto open filter on first search.
     */
    public void setSearchAutoOpenFilterOnFirstSearch(boolean enabled) {
        setBoolean(Key.SEARCH_AUTO_OPEN_FILTER_ON_FIRST_SEARCH, enabled);
    }

    /**
     * Returns whether search require enter to search.
     */
    public boolean isSearchRequireEnterToSearch() {
        return getBoolean(Key.SEARCH_REQUIRE_ENTER_TO_SEARCH);
    }

    /**
     * Updates search require enter to search.
     */
    public void setSearchRequireEnterToSearch(boolean enabled) {
        setBoolean(Key.SEARCH_REQUIRE_ENTER_TO_SEARCH, enabled);
    }

    /**
     * Returns whether search infinite scroll.
     */
    public boolean isSearchInfiniteScroll() {
        return getBoolean(Key.SEARCH_INFINITE_SCROLL);
    }

    /**
     * Updates search infinite scroll.
     */
    public void setSearchInfiniteScroll(boolean enabled) {
        setBoolean(Key.SEARCH_INFINITE_SCROLL, enabled);
    }

    /**
     * Returns search minimum query length.
     */
    public int getSearchMinimumQueryLength() {
        return getInt(Key.SEARCH_MINIMUM_QUERY_LENGTH);
    }

    /**
     * Updates search minimum query length.
     */
    public void setSearchMinimumQueryLength(double value) {
        setInt(Key.SEARCH_MINIMUM_QUERY_LENGTH, clampToStep(value, 3, 5, 1));
    }

    /**
     * Returns search results per page.
     */
    public int getSearchResultsPerPage() {
        return getInt(Key.SEARCH_RESULTS_PER_PAGE);
    }

    /**
     * Updates search results per page.
     */
    public void setSearchResultsPerPage(double value) {
        setInt(Key.SEARCH_RESULTS_PER_PAGE, clampToStep(value, 10, 100, 10));
    }

    /**
     * Returns whether search preserve last query.
     */
    public boolean isSearchPreserveLastQuery() {
        return getBoolean(Key.SEARCH_PRESERVE_LAST_QUERY);
    }

    /**
     * Updates search preserve last query.
     */
    public void setSearchPreserveLastQuery(boolean enabled) {
        setBoolean(Key.SEARCH_PRESERVE_LAST_QUERY, enabled);
    }

    /**
     * Returns whether search remember sort options.
     */
    public boolean isSearchRememberSortOptions() {
        return getBoolean(Key.SEARCH_REMEMBER_SORT_OPTIONS);
    }

    /**
     * Updates search remember sort options.
     */
    public void setSearchRememberSortOptions(boolean enabled) {
        setBoolean(Key.SEARCH_REMEMBER_SORT_OPTIONS, enabled);
        if (!enabled) {
            clearSearchSortOptions();
        }
    }

    /**
     * Returns search sort options.
     */
    public SearchSortViewModel.SortOptions getSearchSortOptions() {
        return SearchSortViewModel.normalize(searchSortOptions);
    }

    /**
     * Updates search sort options.
     */
    public void setSearchSortOptions(SearchSortViewModel.SortOptions options) {
        SearchSortViewModel.SortOptions normalized = SearchSortViewModel.normalize(options);
        searchSortOptions = normalized;
        persistSearchSortOptions(normalized);
    }

    /**
     * Clears search sort options.
     */
    public void clearSearchSortOptions() {
        searchSortOptions = SearchSortViewModel.SortOptions.DEFAULT;
        if (!persistent) {
            return;
        }
        prefs.remove(PREF_SEARCH_SORT_FIELD);
        prefs.remove(PREF_SEARCH_SORT_DIRECTION);
    }

    /**
     * Returns whether media hover zoom.
     */
    public boolean isMediaHoverZoom() {
        return getBoolean(Key.MEDIA_HOVER_ZOOM);
    }

    /**
     * Updates media hover zoom.
     */
    public void setMediaHoverZoom(boolean enabled) {
        setBoolean(Key.MEDIA_HOVER_ZOOM, enabled);
    }

    /**
     * Returns media hover zoom scale.
     */
    public double getMediaHoverZoomScale() {
        return getDouble(Key.MEDIA_HOVER_ZOOM_SCALE);
    }

    /**
     * Updates media hover zoom scale.
     */
    public void setMediaHoverZoomScale(double value) {
        setDouble(Key.MEDIA_HOVER_ZOOM_SCALE, clampDecimal(value, 1.05, 1.50, 0.05));
    }

    /**
     * Returns whether media show download button.
     */
    public boolean isMediaShowDownloadButton() {
        return getBoolean(Key.MEDIA_SHOW_DOWNLOAD_BUTTON);
    }

    /**
     * Updates media show download button.
     */
    public void setMediaShowDownloadButton(boolean enabled) {
        setBoolean(Key.MEDIA_SHOW_DOWNLOAD_BUTTON, enabled);
    }

    /**
     * Returns whether media open preview on image click.
     */
    public boolean isMediaOpenPreviewOnImageClick() {
        return getBoolean(Key.MEDIA_OPEN_PREVIEW_ON_IMAGE_CLICK);
    }

    /**
     * Updates media open preview on image click.
     */
    public void setMediaOpenPreviewOnImageClick(boolean enabled) {
        setBoolean(Key.MEDIA_OPEN_PREVIEW_ON_IMAGE_CLICK, enabled);
    }

    /**
     * Returns whether chat send on enter.
     */
    public boolean isChatSendOnEnter() {
        return getBoolean(Key.CHAT_SEND_ON_ENTER);
    }

    /**
     * Updates chat send on enter.
     */
    public void setChatSendOnEnter(boolean enabled) {
        setBoolean(Key.CHAT_SEND_ON_ENTER, enabled);
    }

    /**
     * Returns whether chat auto scroll to latest.
     */
    public boolean isChatAutoScrollToLatest() {
        return getBoolean(Key.CHAT_AUTO_SCROLL_TO_LATEST);
    }

    /**
     * Updates chat auto scroll to latest.
     */
    public void setChatAutoScrollToLatest(boolean enabled) {
        setBoolean(Key.CHAT_AUTO_SCROLL_TO_LATEST, enabled);
    }

    /**
     * Returns whether chat show message timestamps.
     */
    public boolean isChatShowMessageTimestamps() {
        return getBoolean(Key.CHAT_SHOW_MESSAGE_TIMESTAMPS);
    }

    /**
     * Updates chat show message timestamps.
     */
    public void setChatShowMessageTimestamps(boolean enabled) {
        setBoolean(Key.CHAT_SHOW_MESSAGE_TIMESTAMPS, enabled);
    }

    /**
     * Returns whether chat use24 hour time.
     */
    public boolean isChatUse24HourTime() {
        return getBoolean(Key.CHAT_USE_24_HOUR_TIME);
    }

    /**
     * Updates chat use24 hour time.
     */
    public void setChatUse24HourTime(boolean enabled) {
        setBoolean(Key.CHAT_USE_24_HOUR_TIME, enabled);
    }

    /**
     * Returns whether notifications show unread badges.
     */
    public boolean isNotificationsShowUnreadBadges() {
        return getBoolean(Key.NOTIFICATIONS_SHOW_UNREAD_BADGES);
    }

    /**
     * Updates notifications show unread badges.
     */
    public void setNotificationsShowUnreadBadges(boolean enabled) {
        setBoolean(Key.NOTIFICATIONS_SHOW_UNREAD_BADGES, enabled);
    }

    /**
     * Returns notifications badge cap.
     */
    public int getNotificationsBadgeCap() {
        return getInt(Key.NOTIFICATIONS_BADGE_CAP);
    }

    /**
     * Updates notifications badge cap.
     */
    public void setNotificationsBadgeCap(double value) {
        setInt(Key.NOTIFICATIONS_BADGE_CAP, clampToStep(value, 10, 100, 10));
    }

    /**
     * Returns whether notifications show runtime popups.
     */
    public boolean isNotificationsShowRuntimePopups() {
        return getBoolean(Key.NOTIFICATIONS_SHOW_RUNTIME_POPUPS);
    }

    /**
     * Updates notifications show runtime popups.
     */
    public void setNotificationsShowRuntimePopups(boolean enabled) {
        setBoolean(Key.NOTIFICATIONS_SHOW_RUNTIME_POPUPS, enabled);
    }

    /**
     * Returns whether notifications show os notifications.
     */
    public boolean isNotificationsShowOsNotifications() {
        return getBoolean(Key.NOTIFICATIONS_SHOW_OS_NOTIFICATIONS);
    }

    /**
     * Updates notifications show os notifications.
     */
    public void setNotificationsShowOsNotifications(boolean enabled) {
        setBoolean(Key.NOTIFICATIONS_SHOW_OS_NOTIFICATIONS, enabled);
    }

    /**
     * Returns whether privacy blur on focus loss.
     */
    public boolean isPrivacyBlurOnFocusLoss() {
        return getBoolean(Key.PRIVACY_BLUR_ON_FOCUS_LOSS);
    }

    /**
     * Updates privacy blur on focus loss.
     */
    public void setPrivacyBlurOnFocusLoss(boolean enabled) {
        setBoolean(Key.PRIVACY_BLUR_ON_FOCUS_LOSS, enabled);
    }

    /**
     * Returns privacy blur strength.
     */
    public double getPrivacyBlurStrength() {
        return getDouble(Key.PRIVACY_BLUR_STRENGTH);
    }

    /**
     * Updates privacy blur strength.
     */
    public void setPrivacyBlurStrength(double value) {
        setDouble(Key.PRIVACY_BLUR_STRENGTH, clampDecimal(value, 1.0, 10.0, 1.0));
    }

    /**
     * Returns whether privacy confirm attachment open.
     */
    public boolean isPrivacyConfirmAttachmentOpen() {
        return getBoolean(Key.PRIVACY_CONFIRM_ATTACHMENT_OPEN);
    }

    /**
     * Updates privacy confirm attachment open.
     */
    public void setPrivacyConfirmAttachmentOpen(boolean enabled) {
        setBoolean(Key.PRIVACY_CONFIRM_ATTACHMENT_OPEN, enabled);
    }

    /**
     * Returns whether privacy blur on startup until unlock.
     */
    public boolean isPrivacyBlurOnStartupUntilUnlock() {
        return getBoolean(Key.PRIVACY_BLUR_ON_STARTUP_UNTIL_UNLOCK);
    }

    /**
     * Updates privacy blur on startup until unlock.
     */
    public void setPrivacyBlurOnStartupUntilUnlock(boolean enabled) {
        setBoolean(Key.PRIVACY_BLUR_ON_STARTUP_UNTIL_UNLOCK, enabled);
    }

    /**
     * Returns whether privacy confirm external link open.
     */
    public boolean isPrivacyConfirmExternalLinkOpen() {
        return getBoolean(Key.PRIVACY_CONFIRM_EXTERNAL_LINK_OPEN);
    }

    /**
     * Updates privacy confirm external link open.
     */
    public void setPrivacyConfirmExternalLinkOpen(boolean enabled) {
        setBoolean(Key.PRIVACY_CONFIRM_EXTERNAL_LINK_OPEN, enabled);
    }

    /**
     * Returns whether privacy show notification message preview.
     */
    public boolean isPrivacyShowNotificationMessagePreview() {
        return getBoolean(Key.PRIVACY_SHOW_NOTIFICATION_MESSAGE_PREVIEW);
    }

    /**
     * Updates privacy show notification message preview.
     */
    public void setPrivacyShowNotificationMessagePreview(boolean enabled) {
        setBoolean(Key.PRIVACY_SHOW_NOTIFICATION_MESSAGE_PREVIEW, enabled);
    }

    /**
     * Returns whether privacy hide presence indicators.
     */
    public boolean isPrivacyHidePresenceIndicators() {
        return getBoolean(Key.PRIVACY_HIDE_PRESENCE_INDICATORS);
    }

    /**
     * Updates privacy hide presence indicators.
     */
    public void setPrivacyHidePresenceIndicators(boolean enabled) {
        setBoolean(Key.PRIVACY_HIDE_PRESENCE_INDICATORS, enabled);
    }

    /**
     * Handles read window state.
     */
    public WindowState readWindowState() {
        if (!persistent) {
            return null;
        }
        double x = prefs.getDouble("window.x", Double.NaN);
        double y = prefs.getDouble("window.y", Double.NaN);
        double width = prefs.getDouble("window.width", Double.NaN);
        double height = prefs.getDouble("window.height", Double.NaN);
        boolean maximized = prefs.getBoolean("window.maximized", false);
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(width) || Double.isNaN(height)) {
            return null;
        }
        return new WindowState(x, y, width, height, maximized);
    }

    /**
     * Handles write window state.
     */
    public void writeWindowState(double x, double y, double width, double height, boolean maximized) {
        if (!persistent) {
            return;
        }
        prefs.putDouble("window.x", x);
        prefs.putDouble("window.y", y);
        prefs.putDouble("window.width", width);
        prefs.putDouble("window.height", height);
        prefs.putBoolean("window.maximized", maximized);
    }

    /**
     * Returns last active tab.
     */
    public String getLastActiveTab() {
        if (!persistent) {
            return "messages";
        }
        String stored = prefs.get("main.last_active_tab", "messages");
        return normalizeTab(stored);
    }

    /**
     * Updates last active tab.
     */
    public void setLastActiveTab(String tab) {
        if (!persistent) {
            return;
        }
        prefs.put("main.last_active_tab", normalizeTab(tab));
    }

    /**
     * Normalizes tab.
     */
    private static String normalizeTab(String tab) {
        if (Objects.equals("search", tab)) {
            return "search";
        }
        return "messages";
    }

    /**
     * Loads search sort options.
     */
    private SearchSortViewModel.SortOptions loadSearchSortOptions() {
        if (!persistent) {
            return SearchSortViewModel.SortOptions.DEFAULT;
        }

        String storedField = prefs.get(PREF_SEARCH_SORT_FIELD, null);
        String storedDirection = prefs.get(PREF_SEARCH_SORT_DIRECTION, null);
        if (storedField == null || storedDirection == null) {
            return SearchSortViewModel.SortOptions.DEFAULT;
        }

        SearchSortViewModel.Field field = parseSortField(storedField);
        SearchSortViewModel.Direction direction = parseSortDirection(storedDirection);
        if (field == null || direction == null) {
            return SearchSortViewModel.SortOptions.DEFAULT;
        }
        return new SearchSortViewModel.SortOptions(field, direction);
    }

    /**
     * Persists search sort options.
     */
    private void persistSearchSortOptions(SearchSortViewModel.SortOptions options) {
        if (!persistent) {
            return;
        }
        SearchSortViewModel.SortOptions safe = SearchSortViewModel.normalize(options);
        prefs.put(PREF_SEARCH_SORT_FIELD, safe.field().name());
        prefs.put(PREF_SEARCH_SORT_DIRECTION, safe.direction().name());
    }

    /**
     * Parses sort field.
     */
    private static SearchSortViewModel.Field parseSortField(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SearchSortViewModel.Field.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    /**
     * Parses sort direction.
     */
    private static SearchSortViewModel.Direction parseSortDirection(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SearchSortViewModel.Direction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    /**
     * Returns boolean.
     */
    private boolean getBoolean(Key key) {
        Object value = values.get(key);
        return value instanceof Boolean b ? b : (Boolean) key.defaultValue();
    }

    /**
     * Returns int.
     */
    private int getInt(Key key) {
        Object value = values.get(key);
        return value instanceof Integer i ? i : (Integer) key.defaultValue();
    }

    /**
     * Returns double.
     */
    private double getDouble(Key key) {
        Object value = values.get(key);
        return value instanceof Double d ? d : (Double) key.defaultValue();
    }

    /**
     * Updates boolean.
     */
    private void setBoolean(Key key, boolean value) {
        setValue(key, value);
    }

    /**
     * Updates int.
     */
    private void setInt(Key key, int value) {
        setValue(key, value);
    }

    /**
     * Updates double.
     */
    private void setDouble(Key key, double value) {
        setValue(key, value);
    }

    /**
     * Updates value.
     */
    private void setValue(Key key, Object value) {
        Object previous = values.put(key, value);
        if (Objects.equals(previous, value)) {
            return;
        }

        persistValue(key, value);
        refreshRestartDirtyFlag();
        notifyListeners(key);
    }

    /**
     * Refreshes restart dirty flag.
     */
    private void refreshRestartDirtyFlag() {
        for (Key key : Key.values()) {
            if (key.applyMode() != ApplyMode.RESTART_REQUIRED) {
                continue;
            }
            if (!Objects.equals(restartBaseline.get(key), values.get(key))) {
                restartRequiredDirty = true;
                return;
            }
        }
        restartRequiredDirty = false;
    }

    /**
     * Loads value.
     */
    private Object loadValue(Key key) {
        if (!persistent) {
            return key.defaultValue();
        }

        Object defaultValue = key.defaultValue();
        if (defaultValue instanceof Boolean boolDefault) {
            return prefs.getBoolean(key.preferenceKey(), boolDefault);
        }
        if (defaultValue instanceof Integer intDefault) {
            return prefs.getInt(key.preferenceKey(), intDefault);
        }
        if (defaultValue instanceof Double doubleDefault) {
            return prefs.getDouble(key.preferenceKey(), doubleDefault);
        }
        return defaultValue;
    }

    /**
     * Persists value.
     */
    private void persistValue(Key key, Object value) {
        if (!persistent) {
            return;
        }

        if (value instanceof Boolean boolValue) {
            prefs.putBoolean(key.preferenceKey(), boolValue);
            return;
        }
        if (value instanceof Integer intValue) {
            prefs.putInt(key.preferenceKey(), intValue);
            return;
        }
        if (value instanceof Double doubleValue) {
            prefs.putDouble(key.preferenceKey(), doubleValue);
        }
    }

    /**
     * Notifies listeners.
     */
    private void notifyListeners(Key key) {
        for (Listener listener : listeners) {
            try {
                listener.onSettingChanged(key);
            } catch (Exception ignored) {
                // Listener failures must not affect setting writes.
            }
        }
    }

    /**
     * Handles clamp to step.
     */
    private static int clampToStep(double value, int min, int max, int step) {
        int rounded = (int) Math.round(value);
        int clamped = Math.clamp(rounded, min, max);
        int steps = Math.round((clamped - min) / (float) step);
        int result = min + (steps * step);
        return Math.clamp(result, min, max);
    }

    /**
     * Handles clamp decimal.
     */
    private static double clampDecimal(double value, double min, double max, double step) {
        double clamped = Math.clamp(value, min, max);
        double steps = Math.round((clamped - min) / step);
        double result = min + (steps * step);
        return Math.clamp(result, min, max);
    }

    /**
     * Handles sanitize node.
     */
    private static String sanitizeNode(String userId) {
        String sanitized = userId.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isBlank()) {
            return "user";
        }
        return sanitized;
    }
}
