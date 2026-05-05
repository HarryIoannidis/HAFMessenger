package com.haf.client.controllers;

import com.haf.client.builders.PopupMessageBuilder;
import com.haf.client.builders.SettingsRowBuilder;
import com.haf.client.models.SettingsMenuItem;
import com.haf.client.security.RememberedCredentialsStore;
import com.haf.client.utils.ClientSettings;
import com.haf.client.utils.UiConstants;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXToggleButton;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.prefs.Preferences;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the settings popup window.
 */
public class SettingsController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettingsController.class);
    private static final String RESTART_HANDLER_KEY = "settings.restartRequestHandler";
    private static final String DISABLED_ROW_STYLE_CLASS = "settings-row-disabled";
    private static final Runnable NO_OP_RESTART_HANDLER = () -> {
    };

    // Popup window controls
    @FXML
    private BorderPane rootContainer;
    @FXML
    private HBox titleBar;
    @FXML
    private JFXButton closeButton;

    // Settings layout controls
    @FXML
    private ListView<SettingsMenuItem> settingsMenuListView;
    @FXML
    private ScrollPane settingsContentScrollPane;
    @FXML
    private StackPane settingsPanesStack;

    // Settings panes
    @FXML
    private VBox accountSettingsPane;
    @FXML
    private VBox generalSettingsPane;
    @FXML
    private VBox searchSettingsPane;
    @FXML
    private VBox mediaSettingsPane;
    @FXML
    private VBox chatSettingsPane;
    @FXML
    private VBox notificationsSettingsPane;
    @FXML
    private VBox privacySettingsPane;

    // Pane row containers
    @FXML
    private VBox accountRowsContainer;
    @FXML
    private VBox generalRowsContainer;
    @FXML
    private VBox searchRowsContainer;
    @FXML
    private VBox mediaRowsContainer;
    @FXML
    private VBox chatRowsContainer;
    @FXML
    private VBox notificationsRowsContainer;
    @FXML
    private VBox privacyRowsContainer;

    private double xOffset;
    private double yOffset;

    private ClientSettings settings = ClientSettings.defaults();
    private final RememberedCredentialsStore rememberedCredentialsStore;

    private final Map<String, VBox> panesById = new LinkedHashMap<>();
    private final List<SettingsMenuItem> menuItems = List.of(
            new SettingsMenuItem("Account", "mdi2a-account-outline", "accountSettingsPane"),
            new SettingsMenuItem("General", "mdi2c-cog-outline", "generalSettingsPane"),
            new SettingsMenuItem("Search", "mdi2m-magnify", "searchSettingsPane"),
            new SettingsMenuItem("Preview & Media", "mdi2i-image-outline", "mediaSettingsPane"),
            new SettingsMenuItem("Chat", "mdi2m-message-text-outline", "chatSettingsPane"),
            new SettingsMenuItem("Notifications", "mdi2b-bell-outline", "notificationsSettingsPane"),
            new SettingsMenuItem("Privacy", "mdi2s-shield-lock-outline", "privacySettingsPane"));

    /**
     * Creates a settings controller with default remember-credentials backing.
     */
    public SettingsController() {
        this(RememberedCredentialsStore.createDefault(Preferences.userNodeForPackage(LoginController.class)));
    }

    SettingsController(RememberedCredentialsStore rememberedCredentialsStore) {
        this.rememberedCredentialsStore = Objects.requireNonNull(
                rememberedCredentialsStore,
                "rememberedCredentialsStore");
    }

    /**
     * Initializes settings popup controls and generated row content.
     */
    @FXML
    public void initialize() {
        setupWindowControls();
        registerPanes();
        buildRows();
        wireSettingControls();
        setupMenuList();
    }

    /**
     * Injects active per-user settings and rebuilds row state.
     *
     * @param settings active settings instance
     */
    public void setSettings(ClientSettings settings) {
        this.settings = settings == null ? ClientSettings.defaults() : settings;
        if (rootContainer != null) {
            buildRows();
            wireSettingControls();
        }
    }

    /**
     * Injects restart callback used by the restart-required close popup.
     *
     * @param restartRequestHandler callback invoked for "Restart now"
     */
    public void setRestartRequestHandler(Runnable restartRequestHandler) {
        storeRestartRequestHandler(restartRequestHandler == null ? NO_OP_RESTART_HANDLER : restartRequestHandler);
    }

    /**
     * Returns a snapshot of configured menu items for tests.
     *
     * @return immutable menu item snapshot
     */
    List<SettingsMenuItem> menuItemsForTest() {
        return List.copyOf(menuItems);
    }

    /**
     * Returns the currently visible pane id for tests.
     *
     * @return pane id when visible, otherwise empty string
     */
    String visiblePaneIdForTest() {
        for (Map.Entry<String, VBox> entry : panesById.entrySet()) {
            VBox pane = entry.getValue();
            if (pane != null && pane.isVisible()) {
                return entry.getKey();
            }
        }
        return "";
    }

    /**
     * Selects a menu item by pane id for tests.
     *
     * @param paneId pane id mapped from menu item
     */
    void selectMenuByPaneIdForTest(String paneId) {
        if (settingsMenuListView == null || paneId == null || paneId.isBlank()) {
            return;
        }
        for (SettingsMenuItem item : settingsMenuListView.getItems()) {
            if (paneId.equals(item.paneId())) {
                settingsMenuListView.getSelectionModel().select(item);
                return;
            }
        }
        showPane(paneId);
    }

    /**
     * Registers pane ids to pane instances for visibility switching.
     */
    private void registerPanes() {
        panesById.clear();
        panesById.put("accountSettingsPane", accountSettingsPane);
        panesById.put("generalSettingsPane", generalSettingsPane);
        panesById.put("searchSettingsPane", searchSettingsPane);
        panesById.put("mediaSettingsPane", mediaSettingsPane);
        panesById.put("chatSettingsPane", chatSettingsPane);
        panesById.put("notificationsSettingsPane", notificationsSettingsPane);
        panesById.put("privacySettingsPane", privacySettingsPane);
    }

    /**
     * Creates and appends all settings rows through the shared row builder.
     */
    private void buildRows() {
        if (accountRowsContainer != null) {
            accountRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSectionHeader(
                            "accountSessionSection",
                            "Session"),
                    SettingsRowBuilder.buildSwitchRow(
                            "accountAutoRefreshTokenRow",
                            "accountAutoRefreshTokenToggle",
                            "Auto-refresh Token",
                            "Automatically rotate your session token before it expires.",
                            settings.isAccountAutoRefreshToken()));
        }

        if (generalRowsContainer != null) {
            generalRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSectionHeader(
                            "generalConfirmationsSection",
                            "Confirmations"),
                    SettingsRowBuilder.buildSwitchRow(
                            "generalConfirmExitRow",
                            "generalConfirmExitToggle",
                            "Confirm Before Exit",
                            "Show a confirmation dialog before closing the app window.",
                            settings.isGeneralConfirmExit()),
                    SettingsRowBuilder.buildSwitchRow(
                            "generalConfirmLogoutRow",
                            "generalConfirmLogoutToggle",
                            "Confirm Before Logout",
                            "Show a confirmation dialog before logging out.",
                            settings.isGeneralConfirmLogout()),
                    SettingsRowBuilder.buildSwitchRow(
                            "generalConfirmDeleteChatRow",
                            "generalConfirmDeleteChatToggle",
                            "Confirm Before Deleting Chat",
                            "Show a confirmation dialog before deleting local chat history.",
                            settings.isGeneralConfirmDeleteChat()),
                    SettingsRowBuilder.buildSwitchRow(
                            "generalConfirmRemoveContactRow",
                            "generalConfirmRemoveContactToggle",
                            "Confirm Before Removing Contact",
                            "Show a confirmation dialog before removing a contact.",
                            settings.isGeneralConfirmRemoveContact()),
                    SettingsRowBuilder.buildSectionSpacer("generalRememberSectionSpacer"),
                    SettingsRowBuilder.buildSectionHeader(
                            "generalRememberSection",
                            "Remember"),
                    SettingsRowBuilder.buildSwitchRow(
                            "generalRememberWindowStateRow",
                            "generalRememberWindowStateToggle",
                            "Remember Window State",
                            "Restore the previous window size and position when reopening the app.",
                            settings.isGeneralRememberWindowState()),
                    SettingsRowBuilder.buildSwitchRow(
                            "generalRememberCredentialsRow",
                            "generalRememberCredentialsToggle",
                            "Remember Credentials",
                            "Keep login email prefilled and password in OS secure storage between launches.",
                            isRememberCredentialsEnabled()),
                    SettingsRowBuilder.buildSwitchRow(
                            "generalRestoreLastTabRow",
                            "generalRestoreLastTabToggle",
                            "Restore Last Tab",
                            "Open the last active toolbar tab after startup.",
                            settings.isGeneralRestoreLastTab()));
        }

        if (searchRowsContainer != null) {
            searchRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSectionHeader(
                            "searchTriggerSection",
                            "Trigger & Query"),
                    SettingsRowBuilder.buildSwitchRow(
                            "searchInstantOnTypeRow",
                            "searchInstantOnTypeToggle",
                            "Instant Search While Typing",
                            "Run user search automatically as query text changes.",
                            settings.isSearchInstantOnType()),
                    SettingsRowBuilder.buildSwitchRow(
                            "searchRequireEnterToSearchRow",
                            "searchRequireEnterToSearchToggle",
                            "Require Enter To Search",
                            "Start search only when pressing Enter.",
                            settings.isSearchRequireEnterToSearch()),
                    SettingsRowBuilder.buildSliderRow(
                            "searchMinimumQueryLengthRow",
                            "searchMinimumQueryLengthSlider",
                            "Minimum Query Length",
                            "Set the minimum number of characters needed before search runs.",
                            3.0,
                            5.0,
                            settings.getSearchMinimumQueryLength(),
                            1.0,
                            true,
                            true),
                    SettingsRowBuilder.buildSectionSpacer("searchFlowSectionSpacer"),
                    SettingsRowBuilder.buildSectionHeader(
                            "searchFlowSection",
                            "Flow & Results"),
                    SettingsRowBuilder.buildSwitchRow(
                            "searchAutoOpenFilterOnFirstSearchRow",
                            "searchAutoOpenFilterOnFirstSearchToggle",
                            "Auto Open Filter On First Search",
                            "Open the filter popup before executing the first search in the Search tab.",
                            settings.isSearchAutoOpenFilterOnFirstSearch()),
                    SettingsRowBuilder.buildSwitchRow(
                            "searchInfiniteScrollRow",
                            "searchInfiniteScrollToggle",
                            "Enable Infinite Scroll",
                            "Load additional results when reaching the bottom of the results area.",
                            settings.isSearchInfiniteScroll()),
                    SettingsRowBuilder.buildSliderRow(
                            "searchResultsPerPageRow",
                            "searchResultsPerPageSlider",
                            "Results Per Page",
                            "Set how many search results are requested per page.",
                            10.0,
                            100.0,
                            settings.getSearchResultsPerPage(),
                            10.0,
                            true,
                            true),
                    SettingsRowBuilder.buildSwitchRow(
                            "searchAutoClearOnTabExitRow",
                            "searchAutoClearOnTabExitToggle",
                            "Auto Clear Search On Tab Exit",
                            "Clear query and results when leaving the Search tab.",
                            !settings.isSearchPreserveLastQuery()),
                    SettingsRowBuilder.buildSwitchRow(
                            "searchRememberSortOptionsRow",
                            "searchRememberSortOptionsToggle",
                            "Remember Search Sort",
                            "Keep selected search sort options between app restarts for your account.",
                            settings.isSearchRememberSortOptions()));
        }

        if (mediaRowsContainer != null) {
            mediaRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSectionHeader(
                            "mediaUploadSection",
                            "Upload"),
                    SettingsRowBuilder.buildSwitchRow(
                            "mediaSendInMaxQualityRow",
                            "mediaSendInMaxQualityToggle",
                            "Send In Max Quality",
                            "Send images using original quality without client-side optimization.",
                            settings.isMediaSendInMaxQuality()),
                    SettingsRowBuilder.buildSliderRow(
                            "mediaImageSendQualityRow",
                            "mediaImageSendQualitySlider",
                            "Image Send Quality",
                            "Lower values optimize images before sending.",
                            60.0,
                            100.0,
                            settings.getMediaImageSendQuality(),
                            5.0,
                            true,
                            true),
                    SettingsRowBuilder.buildSectionSpacer("mediaPreviewSectionSpacer"),
                    SettingsRowBuilder.buildSectionHeader(
                            "mediaPreviewSection",
                            "Preview"),
                    SettingsRowBuilder.buildSwitchRow(
                            "mediaOpenPreviewOnImageClickRow",
                            "mediaOpenPreviewOnImageClickToggle",
                            "Open Preview On Image Click",
                            "Open the image preview popup when clicking image messages.",
                            settings.isMediaOpenPreviewOnImageClick()),
                    SettingsRowBuilder.buildSwitchRow(
                            "mediaHoverZoomRow",
                            "mediaHoverZoomToggle",
                            "Enable Hover Zoom",
                            "Apply subtle zoom and cursor-based panning inside image preview.",
                            settings.isMediaHoverZoom()),
                    SettingsRowBuilder.buildSliderRow(
                            "mediaHoverZoomScaleRow",
                            "mediaHoverZoomScaleSlider",
                            "Hover Zoom Scale",
                            "Control how strong the preview zoom effect should be.",
                            1.05,
                            1.50,
                            settings.getMediaHoverZoomScale(),
                            0.05,
                            true,
                            true),
                    SettingsRowBuilder.buildSwitchRow(
                            "mediaShowDownloadButtonRow",
                            "mediaShowDownloadButtonToggle",
                            "Show Download Button",
                            "Display the download action in the preview popup toolbar.",
                            settings.isMediaShowDownloadButton()));
        }

        if (chatRowsContainer != null) {
            chatRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSectionHeader(
                            "chatSection",
                            "Chat"),
                    SettingsRowBuilder.buildSwitchRow(
                            "chatSendOnEnterRow",
                            "chatSendOnEnterToggle",
                            "Send On Enter",
                            "Send the current draft when pressing Enter in the message field.",
                            settings.isChatSendOnEnter()),
                    SettingsRowBuilder.buildSwitchRow(
                            "chatAutoScrollToLatestRow",
                            "chatAutoScrollToLatestToggle",
                            "Auto Scroll To Latest",
                            "Keep the message view anchored to the newest message.",
                            settings.isChatAutoScrollToLatest()),
                    SettingsRowBuilder.buildSwitchRow(
                            "chatUse24HourTimeRow",
                            "chatUse24HourTimeToggle",
                            "Use 24-Hour Time",
                            "When disabled, timestamps are shown in 12-hour format with AM/PM.",
                            settings.isChatUse24HourTime()),
                    SettingsRowBuilder.buildSwitchRow(
                            "chatShowMessageTimestampsRow",
                            "chatShowMessageTimestampsToggle",
                            "Show Message Timestamps",
                            "Render sent/received times on each chat bubble.",
                            settings.isChatShowMessageTimestamps()));
        }

        if (notificationsRowsContainer != null) {
            notificationsRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSectionHeader(
                            "notificationsSection",
                            "Notifications"),
                    SettingsRowBuilder.buildSwitchRow(
                            "notificationsShowUnreadBadgesRow",
                            "notificationsShowUnreadBadgesToggle",
                            "Show Unread Badges",
                            "Display unread counters on contact rows in the left list.",
                            settings.isNotificationsShowUnreadBadges()),
                    SettingsRowBuilder.buildSliderRow(
                            "notificationsBadgeCapRow",
                            "notificationsBadgeCapSlider",
                            "Unread Badge Cap",
                            "Set the maximum count displayed before switching to a capped badge value.",
                            10.0,
                            100.0,
                            settings.getNotificationsBadgeCap(),
                            10.0,
                            true,
                            true),
                    SettingsRowBuilder.buildSwitchRow(
                            "notificationsShowOsNotificationsRow",
                            "notificationsShowOsNotificationsToggle",
                            "Show OS Notifications",
                            "Display incoming-message alerts through your operating system notification center.",
                            settings.isNotificationsShowOsNotifications()),
                    SettingsRowBuilder.buildSwitchRow(
                            "notificationsShowRuntimePopupsRow",
                            "notificationsShowRuntimePopupsToggle",
                            "Show Runtime Popups",
                            "Show runtime issue popup dialogs for recoverable application errors.",
                            settings.isNotificationsShowRuntimePopups()));
        }

        if (privacyRowsContainer != null) {
            privacyRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSectionHeader(
                            "privacyBlurSection",
                            "Blur Protection"),
                    SettingsRowBuilder.buildSwitchRow(
                            "privacyBlurOnFocusLossRow",
                            "privacyBlurOnFocusLossToggle",
                            "Blur On Focus Loss",
                            "Blur sensitive content when the main window loses focus.",
                            settings.isPrivacyBlurOnFocusLoss()),
                    SettingsRowBuilder.buildSwitchRow(
                            "privacyBlurOnStartupUntilUnlockRow",
                            "privacyBlurOnStartupUntilUnlockToggle",
                            "Blur On Startup Until Unlock",
                            "Blur content when app opens, until you explicitly unlock it.",
                            settings.isPrivacyBlurOnStartupUntilUnlock()),
                    SettingsRowBuilder.buildSliderRow(
                            "privacyBlurStrengthRow",
                            "privacyBlurStrengthSlider",
                            "Blur Strength",
                            "Control the intensity of the privacy blur overlay.",
                            1.0,
                            10.0,
                            settings.getPrivacyBlurStrength(),
                            1.0,
                            true,
                            true),
                    SettingsRowBuilder.buildSectionSpacer("privacySafetySectionSpacer"),
                    SettingsRowBuilder.buildSectionHeader(
                            "privacySafetySection",
                            "Safety Prompts"),
                    SettingsRowBuilder.buildSwitchRow(
                            "privacyConfirmAttachmentOpenRow",
                            "privacyConfirmAttachmentOpenToggle",
                            "Confirm Attachment Open",
                            "Ask for confirmation before opening or downloading attachments.",
                            settings.isPrivacyConfirmAttachmentOpen()),
                    SettingsRowBuilder.buildSwitchRow(
                            "privacyConfirmExternalLinkOpenRow",
                            "privacyConfirmExternalLinkOpenToggle",
                            "Confirm External Links",
                            "Ask for confirmation before opening external links in your browser.",
                            settings.isPrivacyConfirmExternalLinkOpen()),
                    SettingsRowBuilder.buildSwitchRow(
                            "privacyShowNotificationMessagePreviewRow",
                            "privacyShowNotificationMessagePreviewToggle",
                            "Show Message Preview In Notifications",
                            "When enabled, text notifications can include a preview of message content.",
                            settings.isPrivacyShowNotificationMessagePreview()),
                    SettingsRowBuilder.buildSectionSpacer("privacyPresenceSectionSpacer"),
                    SettingsRowBuilder.buildSectionHeader(
                            "privacyPresenceSection",
                            "Presence"),
                    SettingsRowBuilder.buildSwitchRow(
                            "privacyHidePresenceIndicatorsRow",
                            "privacyHidePresenceIndicatorsToggle",
                            "Hide Presence Indicators",
                            "Hide online/offline indicators and related activity status badges.",
                            settings.isPrivacyHidePresenceIndicators()));
        }
    }

    /**
     * Wires row controls directly into the active {@link ClientSettings} instance.
     */
    private void wireSettingControls() {
        wireSwitch("accountAutoRefreshTokenRow", "accountAutoRefreshTokenToggle",
                settings::setAccountAutoRefreshToken);

        wireSwitch("generalConfirmExitRow", "generalConfirmExitToggle", settings::setGeneralConfirmExit);
        wireSwitch("generalConfirmLogoutRow", "generalConfirmLogoutToggle", settings::setGeneralConfirmLogout);
        wireSwitch("generalConfirmDeleteChatRow", "generalConfirmDeleteChatToggle",
                settings::setGeneralConfirmDeleteChat);
        wireSwitch("generalConfirmRemoveContactRow", "generalConfirmRemoveContactToggle",
                settings::setGeneralConfirmRemoveContact);
        wireSwitch("generalRememberWindowStateRow", "generalRememberWindowStateToggle",
                settings::setGeneralRememberWindowState);
        wireSwitch("generalRememberCredentialsRow", "generalRememberCredentialsToggle",
                this::setRememberCredentialsEnabled);
        wireSwitch("generalRestoreLastTabRow", "generalRestoreLastTabToggle", settings::setGeneralRestoreLastTab);

        wireSwitch("searchInstantOnTypeRow", "searchInstantOnTypeToggle", settings::setSearchInstantOnType);
        wireSwitch("searchRequireEnterToSearchRow", "searchRequireEnterToSearchToggle",
                settings::setSearchRequireEnterToSearch);
        wireSlider("searchMinimumQueryLengthSlider", settings::setSearchMinimumQueryLength);
        wireSwitch("searchAutoOpenFilterOnFirstSearchRow", "searchAutoOpenFilterOnFirstSearchToggle",
                settings::setSearchAutoOpenFilterOnFirstSearch);
        wireSwitch("searchInfiniteScrollRow", "searchInfiniteScrollToggle", settings::setSearchInfiniteScroll);
        wireSlider("searchResultsPerPageSlider", settings::setSearchResultsPerPage);
        wireSwitch("searchAutoClearOnTabExitRow", "searchAutoClearOnTabExitToggle",
                enabled -> settings.setSearchPreserveLastQuery(!enabled));
        wireSwitch("searchRememberSortOptionsRow", "searchRememberSortOptionsToggle",
                settings::setSearchRememberSortOptions);

        wireSwitch("mediaSendInMaxQualityRow", "mediaSendInMaxQualityToggle", settings::setMediaSendInMaxQuality);
        wireSwitch("mediaHoverZoomRow", "mediaHoverZoomToggle", settings::setMediaHoverZoom);
        wireSlider("mediaHoverZoomScaleSlider", settings::setMediaHoverZoomScale);
        wireSlider("mediaImageSendQualitySlider", settings::setMediaImageSendQuality);
        wireSwitch("mediaShowDownloadButtonRow", "mediaShowDownloadButtonToggle", settings::setMediaShowDownloadButton);
        wireSwitch("mediaOpenPreviewOnImageClickRow", "mediaOpenPreviewOnImageClickToggle",
                settings::setMediaOpenPreviewOnImageClick);

        wireSwitch("chatSendOnEnterRow", "chatSendOnEnterToggle", settings::setChatSendOnEnter);
        wireSwitch("chatAutoScrollToLatestRow", "chatAutoScrollToLatestToggle", settings::setChatAutoScrollToLatest);
        wireSwitch("chatShowMessageTimestampsRow", "chatShowMessageTimestampsToggle",
                settings::setChatShowMessageTimestamps);
        wireSwitch("chatUse24HourTimeRow", "chatUse24HourTimeToggle", settings::setChatUse24HourTime);

        wireSwitch("notificationsShowUnreadBadgesRow", "notificationsShowUnreadBadgesToggle",
                settings::setNotificationsShowUnreadBadges);
        wireSlider("notificationsBadgeCapSlider", settings::setNotificationsBadgeCap);
        wireSwitch("notificationsShowOsNotificationsRow", "notificationsShowOsNotificationsToggle",
                settings::setNotificationsShowOsNotifications);
        wireSwitch("notificationsShowRuntimePopupsRow", "notificationsShowRuntimePopupsToggle",
                settings::setNotificationsShowRuntimePopups);

        wireSwitch("privacyBlurOnFocusLossRow", "privacyBlurOnFocusLossToggle", settings::setPrivacyBlurOnFocusLoss);
        wireSlider("privacyBlurStrengthSlider", settings::setPrivacyBlurStrength);
        wireSwitch("privacyBlurOnStartupUntilUnlockRow", "privacyBlurOnStartupUntilUnlockToggle",
                settings::setPrivacyBlurOnStartupUntilUnlock);
        wireSwitch("privacyConfirmAttachmentOpenRow", "privacyConfirmAttachmentOpenToggle",
                settings::setPrivacyConfirmAttachmentOpen);
        wireSwitch("privacyConfirmExternalLinkOpenRow", "privacyConfirmExternalLinkOpenToggle",
                settings::setPrivacyConfirmExternalLinkOpen);
        wireSwitch("privacyShowNotificationMessagePreviewRow", "privacyShowNotificationMessagePreviewToggle",
                settings::setPrivacyShowNotificationMessagePreview);
        wireSwitch("privacyHidePresenceIndicatorsRow", "privacyHidePresenceIndicatorsToggle",
                settings::setPrivacyHidePresenceIndicators);

        wireSearchModeMutualExclusivity();
        wireDependentToggleRowState("privacyBlurOnFocusLossToggle", "privacyBlurStrengthRow");
        wireInverseDependentToggleRowState("mediaSendInMaxQualityToggle", "mediaImageSendQualityRow");
        wireDependentToggleRowState("mediaHoverZoomToggle", "mediaHoverZoomScaleRow");
        wireDependentToggleRowState("notificationsShowUnreadBadgesToggle", "notificationsBadgeCapRow");
        wireDependentToggleRowState("chatShowMessageTimestampsToggle", "chatUse24HourTimeRow");
        wireDependentToggleRowState("searchInfiniteScrollToggle", "searchResultsPerPageRow");
    }

    /**
     * Wires a toggle row to a boolean sink and row-overlay click behavior.
     *
     * @param rowId     row node id containing the toggle
     * @param controlId toggle control id
     * @param sink      consumer receiving the selected state
     */
    private void wireSwitch(String rowId, String controlId, Consumer<Boolean> sink) {
        JFXToggleButton toggle = findById(controlId, JFXToggleButton.class);
        if (toggle == null) {
            return;
        }
        toggle.selectedProperty().addListener((obs, oldValue, newValue) -> sink.accept(Boolean.TRUE.equals(newValue)));
        wireOverlayRowToggle(rowId, () -> toggle.setSelected(!toggle.isSelected()));
    }

    /**
     * Wires a slider control to a numeric sink callback.
     *
     * @param controlId slider node id
     * @param sink      consumer receiving current slider value
     */
    private void wireSlider(String controlId, DoubleConsumer sink) {
        JFXSlider slider = findById(controlId, JFXSlider.class);
        if (slider == null) {
            return;
        }
        slider.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                sink.accept(newValue.doubleValue());
            }
        });
    }

    /**
     * Reads whether login credential persistence is enabled.
     *
     * @return {@code true} when remember-me is enabled
     */
    private boolean isRememberCredentialsEnabled() {
        return rememberedCredentialsStore.isRememberCredentialsEnabled();
    }

    /**
     * Stores remember-me preference and clears remembered credentials when
     * disabled.
     *
     * @param enabled desired remember-me state
     */
    private void setRememberCredentialsEnabled(boolean enabled) {
        rememberedCredentialsStore.setRememberCredentialsEnabled(enabled);
    }

    /**
     * Enforces mutual exclusivity between instant-search and require-enter modes.
     */
    private void wireSearchModeMutualExclusivity() {
        JFXToggleButton instantSearchToggle = findById("searchInstantOnTypeToggle", JFXToggleButton.class);
        JFXToggleButton requireEnterToggle = findById("searchRequireEnterToSearchToggle", JFXToggleButton.class);
        if (instantSearchToggle == null || requireEnterToggle == null) {
            return;
        }

        AtomicBoolean syncing = new AtomicBoolean(false);
        instantSearchToggle.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (syncing.get() || !Boolean.TRUE.equals(newValue)) {
                return;
            }
            syncing.set(true);
            try {
                if (requireEnterToggle.isSelected()) {
                    requireEnterToggle.setSelected(false);
                }
            } finally {
                syncing.set(false);
            }
        });
        requireEnterToggle.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (syncing.get() || !Boolean.TRUE.equals(newValue)) {
                return;
            }
            syncing.set(true);
            try {
                if (instantSearchToggle.isSelected()) {
                    instantSearchToggle.setSelected(false);
                }
            } finally {
                syncing.set(false);
            }
        });
    }

    /**
     * Enables/disables a dependent row based on a toggle control state.
     *
     * @param toggleId       toggle control id
     * @param dependentRowId row id to enable/disable
     */
    private void wireDependentToggleRowState(String toggleId, String dependentRowId) {
        Node dependentRow = findById(dependentRowId, Node.class);
        JFXToggleButton toggle = findById(toggleId, JFXToggleButton.class);
        if (dependentRow == null || toggle == null) {
            return;
        }

        applyDependentRowState(dependentRow, toggle.isSelected());
        toggle.selectedProperty().addListener(
                (obs, oldValue, newValue) -> applyDependentRowState(dependentRow, Boolean.TRUE.equals(newValue)));
    }

    /**
     * Enables/disables a dependent row based on the inverse toggle state.
     *
     * @param toggleId       toggle control id
     * @param dependentRowId row id to enable/disable
     */
    private void wireInverseDependentToggleRowState(String toggleId, String dependentRowId) {
        Node dependentRow = findById(dependentRowId, Node.class);
        JFXToggleButton toggle = findById(toggleId, JFXToggleButton.class);
        if (dependentRow == null || toggle == null) {
            return;
        }

        applyDependentRowState(dependentRow, !toggle.isSelected());
        toggle.selectedProperty().addListener(
                (obs, oldValue, newValue) -> applyDependentRowState(dependentRow, !Boolean.TRUE.equals(newValue)));
    }

    /**
     * Applies disabled-row presentation and interactivity state.
     *
     * @param row     target row node
     * @param enabled whether row should be enabled
     */
    private static void applyDependentRowState(Node row, boolean enabled) {
        row.setDisable(!enabled);
        if (enabled) {
            row.getStyleClass().remove(DISABLED_ROW_STYLE_CLASS);
            return;
        }
        if (!row.getStyleClass().contains(DISABLED_ROW_STYLE_CLASS)) {
            row.getStyleClass().add(DISABLED_ROW_STYLE_CLASS);
        }
    }

    /**
     * Wires row overlay button clicks to toggle the underlying control.
     *
     * @param rowId        row node id
     * @param toggleAction action that flips the target control state
     */
    private void wireOverlayRowToggle(String rowId, Runnable toggleAction) {
        Node rowNode = findById(rowId, Node.class);
        if (!(rowNode instanceof Parent parent)) {
            return;
        }
        JFXButton overlayButton = findRowOverlayButton(parent);
        if (overlayButton != null) {
            overlayButton.setOnAction(event -> toggleAction.run());
        }
    }

    /**
     * Finds the overlay button used to make entire settings rows clickable.
     *
     * @param root row root node to search recursively
     * @return overlay button when found, otherwise {@code null}
     */
    private JFXButton findRowOverlayButton(Parent root) {
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof JFXButton button
                    && button.getStyleClass().contains("settings-row-overlay-button")) {
                return button;
            }
            if (child instanceof Parent childParent) {
                JFXButton nested = findRowOverlayButton(childParent);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    /**
     * Resolves a node by id and type within the settings scene graph.
     *
     * @param nodeId   node id to find
     * @param nodeType expected node type
     * @param <T>      expected node subtype
     * @return matching node instance or {@code null} when absent
     */
    private <T extends Node> T findById(String nodeId, Class<T> nodeType) {
        if (nodeId == null || nodeType == null || rootContainer == null) {
            return null;
        }
        return findByIdRecursive(rootContainer, nodeId, nodeType);
    }

    /**
     * Recursively searches for a node by id and expected type.
     *
     * @param node     current root node
     * @param nodeId   node id to match
     * @param nodeType expected node type
     * @param <T>      expected node subtype
     * @return matching node instance or {@code null} when absent
     */
    private static <T extends Node> T findByIdRecursive(Node node, String nodeId, Class<T> nodeType) {
        if (nodeId.equals(node.getId()) && nodeType.isInstance(node)) {
            return nodeType.cast(node);
        }
        for (Node child : childNodesOf(node)) {
            T found = findByIdRecursive(child, nodeId, nodeType);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Returns child nodes participating in recursive lookup for a node.
     *
     * @param node source node
     * @return flattened child list for supported container types
     */
    private static List<Node> childNodesOf(Node node) {
        if (node == null) {
            return List.of();
        }
        java.util.ArrayList<Node> children = new java.util.ArrayList<>();
        if (node instanceof Parent parent) {
            children.addAll(parent.getChildrenUnmodifiable());
        }
        if (node instanceof SplitPane splitPane) {
            children.addAll(splitPane.getItems());
        }
        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            children.add(scrollPane.getContent());
        }
        return children;
    }

    /**
     * Configures the left menu list and pane-selection behavior.
     */
    private void setupMenuList() {
        if (settingsMenuListView == null) {
            return;
        }

        settingsMenuListView.setItems(FXCollections.observableArrayList(menuItems));
        settingsMenuListView.setCellFactory(ignored -> new SettingsMenuCell());
        settingsMenuListView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                showPane(newItem.paneId());
            }
        });

        if (!settingsMenuListView.getItems().isEmpty()) {
            settingsMenuListView.getSelectionModel().selectFirst();
        }
    }

    /**
     * Shows one pane and hides all others.
     *
     * @param paneId pane id to show
     */
    private void showPane(String paneId) {
        for (Map.Entry<String, VBox> entry : panesById.entrySet()) {
            VBox pane = entry.getValue();
            if (pane == null) {
                continue;
            }
            boolean shouldShow = entry.getKey().equals(paneId);
            pane.setVisible(shouldShow);
            pane.setManaged(shouldShow);
        }

        if (settingsContentScrollPane != null) {
            settingsContentScrollPane.setVvalue(0.0);
        }
    }

    /**
     * Wires draggable title bar and popup window controls.
     */
    private void setupWindowControls() {
        Platform.runLater(() -> {
            Stage stage = resolveStage();
            if (stage == null) {
                return;
            }
            AtomicBoolean closingStage = new AtomicBoolean(false);

            if (closeButton != null) {
                closeButton.setOnAction(e -> requestClose(stage, closingStage));
            }
            stage.setOnCloseRequest(event -> {
                if (closingStage.get()) {
                    return;
                }
                event.consume();
                requestClose(stage, closingStage);
            });
            if (titleBar != null) {
                titleBar.setOnMousePressed(event -> {
                    xOffset = event.getSceneX();
                    yOffset = event.getSceneY();
                });
                titleBar.setOnMouseDragged(event -> {
                    stage.setX(event.getScreenX() - xOffset);
                    stage.setY(event.getScreenY() - yOffset);
                });
            }
        });
    }

    /**
     * Closes the settings popup, optionally prompting when restart-required
     * settings are dirty.
     *
     * @param stage        settings popup stage
     * @param closingStage re-entry guard for close handling
     */
    private void requestClose(Stage stage, AtomicBoolean closingStage) {
        if (stage == null) {
            return;
        }
        if (!settings.isRestartRequiredDirty()) {
            hideStage(stage, closingStage);
            return;
        }

        PopupMessageBuilder.create()
                .popupKey("popup-settings-restart-required")
                .title("Restart required")
                .message("Some changes will apply after restart. Restart now?")
                .actionText("Restart now")
                .cancelText("Later")
                .showCancel(true)
                .onAction(() -> {
                    resolveRestartRequestHandler().run();
                    hideStage(stage, closingStage);
                })
                .onCancel(() -> hideStage(stage, closingStage))
                .show();
    }

    /**
     * Hides the settings stage while guarding against recursive close handlers.
     *
     * @param stage        settings popup stage
     * @param closingStage re-entry guard for close handling
     */
    private void hideStage(Stage stage, AtomicBoolean closingStage) {
        closingStage.set(true);
        try {
            stage.hide();
        } finally {
            closingStage.set(false);
        }
    }

    /**
     * Stores restart-request callback on root container properties.
     *
     * @param restartRequestHandler callback that requests app restart
     */
    private void storeRestartRequestHandler(Runnable restartRequestHandler) {
        if (rootContainer == null) {
            return;
        }
        rootContainer.getProperties().put(RESTART_HANDLER_KEY, restartRequestHandler);
    }

    /**
     * Resolves restart-request callback from root container properties.
     *
     * @return configured callback, or a safe no-op fallback
     */
    private Runnable resolveRestartRequestHandler() {
        if (rootContainer == null) {
            return NO_OP_RESTART_HANDLER;
        }
        Object value = rootContainer.getProperties().get(RESTART_HANDLER_KEY);
        if (value instanceof Runnable runnable) {
            return runnable;
        }
        return NO_OP_RESTART_HANDLER;
    }

    /**
     * Resolves the popup stage hosting this controller.
     *
     * @return host stage, or {@code null} when scene/window is unavailable
     */
    private Stage resolveStage() {
        if (rootContainer == null || rootContainer.getScene() == null || rootContainer.getScene().getWindow() == null) {
            return null;
        }
        return (Stage) rootContainer.getScene().getWindow();
    }

    /**
     * Cell for settings left navigation menu, rendered from
     * settings_item_cell.fxml.
     */
    private static final class SettingsMenuCell extends ListCell<SettingsMenuItem> {

        private StackPane cellRoot;
        private FontIcon menuIcon;
        private Text nameText;
        private JFXButton overlayButton;

        /**
         * Ensures cell FXML is loaded exactly once per cell instance.
         */
        private void ensureLoaded() {
            if (cellRoot != null) {
                return;
            }

            try {
                var resource = getClass().getResource(UiConstants.FXML_SETTINGS_ITEM_CELL);
                FXMLLoader loader = new FXMLLoader(resource);
                cellRoot = loader.load();

                Object iconNode = loader.getNamespace().get("menuIcon");
                if (iconNode instanceof FontIcon icon) {
                    menuIcon = icon;
                }
                Object nameNode = loader.getNamespace().get("nameText");
                if (nameNode instanceof Text textNode) {
                    nameText = textNode;
                }
                Object overlayNode = loader.getNamespace().get("overlayButton");
                if (overlayNode instanceof JFXButton button) {
                    overlayButton = button;
                }
            } catch (IOException ex) {
                LOGGER.error("Could not load settings_item_cell.fxml", ex);
            }
        }

        /**
         * Updates cell graphics and menu-item text/icon content.
         *
         * @param item  menu item payload
         * @param empty JavaFX empty flag
         */
        @Override
        protected void updateItem(SettingsMenuItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            ensureLoaded();
            if (cellRoot == null) {
                setGraphic(null);
                setText(item.label());
                return;
            }

            if (menuIcon != null) {
                menuIcon.setIconLiteral(item.iconLiteral());
            }
            if (nameText != null) {
                nameText.setText(item.label());
            }
            if (overlayButton != null) {
                overlayButton.setOnAction(e -> {
                    if (getListView() != null) {
                        getListView().getSelectionModel().select(getItem());
                    }
                });
            }

            setGraphic(cellRoot);
            setText(null);
        }
    }
}
