package com.haf.client.controllers;

import com.haf.client.models.SettingsMenuItem;
import com.haf.client.utils.ClientSettings;
import com.haf.client.utils.SettingsRowBuilder;
import com.haf.client.utils.PopupMessageBuilder;
import com.haf.client.utils.UiConstants;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final Runnable NO_OP_RESTART_HANDLER = () -> {
    };

    // Popup window controls
    @FXML
    private BorderPane rootContainer;
    @FXML
    private HBox titleBar;
    @FXML
    private JFXButton minimizeButton;
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

    private final Map<String, VBox> panesById = new LinkedHashMap<>();
    private final List<SettingsMenuItem> menuItems = List.of(
            new SettingsMenuItem("General", "mdi2c-cog-outline", "generalSettingsPane"),
            new SettingsMenuItem("Search", "mdi2m-magnify", "searchSettingsPane"),
            new SettingsMenuItem("Preview & Media", "mdi2i-image-outline", "mediaSettingsPane"),
            new SettingsMenuItem("Chat", "mdi2m-message-text-outline", "chatSettingsPane"),
            new SettingsMenuItem("Notifications", "mdi2b-bell-outline", "notificationsSettingsPane"),
            new SettingsMenuItem("Privacy", "mdi2s-shield-lock-outline", "privacySettingsPane"));

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
        if (generalRowsContainer != null) {
            generalRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSwitchRow(
                            "generalConfirmExitRow",
                            "generalConfirmExitToggle",
                            "Confirm Before Exit",
                            "Show a confirmation dialog before closing the app window.",
                            settings.isGeneralConfirmExit()),
                    SettingsRowBuilder.buildSwitchRow(
                            "generalRememberWindowStateRow",
                            "generalRememberWindowStateToggle",
                            "Remember Window State",
                            "Restore the previous window size and position when reopening the app.",
                            settings.isGeneralRememberWindowState()),
                    SettingsRowBuilder.buildCheckboxRow(
                            "generalRestoreLastTabRow",
                            "generalRestoreLastTabCheck",
                            "Restore Last Tab",
                            "Open the last active toolbar tab after startup.",
                            settings.isGeneralRestoreLastTab()));
        }

        if (searchRowsContainer != null) {
            searchRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSwitchRow(
                            "searchInstantOnTypeRow",
                            "searchInstantOnTypeToggle",
                            "Instant Search While Typing",
                            "Run user search automatically as query text changes.",
                            settings.isSearchInstantOnType()),
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
                    SettingsRowBuilder.buildCheckboxRow(
                            "searchPreserveLastQueryRow",
                            "searchPreserveLastQueryCheck",
                            "Preserve Last Query",
                            "Keep the last search text when returning to the Search tab.",
                            settings.isSearchPreserveLastQuery()));
        }

        if (mediaRowsContainer != null) {
            mediaRowsContainer.getChildren().setAll(
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
                            settings.isMediaShowDownloadButton()),
                    SettingsRowBuilder.buildCheckboxRow(
                            "mediaOpenPreviewOnImageClickRow",
                            "mediaOpenPreviewOnImageClickCheck",
                            "Open Preview On Image Click",
                            "Open the image preview popup when clicking image messages.",
                            settings.isMediaOpenPreviewOnImageClick()));
        }

        if (chatRowsContainer != null) {
            chatRowsContainer.getChildren().setAll(
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
                    SettingsRowBuilder.buildCheckboxRow(
                            "chatShowMessageTimestampsRow",
                            "chatShowMessageTimestampsCheck",
                            "Show Message Timestamps",
                            "Render sent/received times on each chat bubble.",
                            settings.isChatShowMessageTimestamps()));
        }

        if (notificationsRowsContainer != null) {
            notificationsRowsContainer.getChildren().setAll(
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
                    SettingsRowBuilder.buildCheckboxRow(
                            "notificationsShowRuntimePopupsRow",
                            "notificationsShowRuntimePopupsCheck",
                            "Show Runtime Popups",
                            "Show runtime issue popup dialogs for recoverable application errors.",
                            settings.isNotificationsShowRuntimePopups()));
        }

        if (privacyRowsContainer != null) {
            privacyRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSwitchRow(
                            "privacyBlurOnFocusLossRow",
                            "privacyBlurOnFocusLossToggle",
                            "Blur On Focus Loss",
                            "Blur sensitive content when the main window loses focus.",
                            settings.isPrivacyBlurOnFocusLoss()),
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
                    SettingsRowBuilder.buildCheckboxRow(
                            "privacyConfirmAttachmentOpenRow",
                            "privacyConfirmAttachmentOpenCheck",
                            "Confirm Attachment Open",
                            "Ask for confirmation before opening or downloading attachments.",
                            settings.isPrivacyConfirmAttachmentOpen()),
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
        wireSwitch("generalConfirmExitRow", "generalConfirmExitToggle", settings::setGeneralConfirmExit);
        wireSwitch("generalRememberWindowStateRow", "generalRememberWindowStateToggle",
                settings::setGeneralRememberWindowState);
        wireCheckbox("generalRestoreLastTabRow", "generalRestoreLastTabCheck", settings::setGeneralRestoreLastTab);

        wireSwitch("searchInstantOnTypeRow", "searchInstantOnTypeToggle", settings::setSearchInstantOnType);
        wireSwitch("searchAutoOpenFilterOnFirstSearchRow", "searchAutoOpenFilterOnFirstSearchToggle",
                settings::setSearchAutoOpenFilterOnFirstSearch);
        wireSwitch("searchInfiniteScrollRow", "searchInfiniteScrollToggle", settings::setSearchInfiniteScroll);
        wireSlider("searchResultsPerPageSlider", settings::setSearchResultsPerPage);
        wireCheckbox("searchPreserveLastQueryRow", "searchPreserveLastQueryCheck", settings::setSearchPreserveLastQuery);

        wireSwitch("mediaHoverZoomRow", "mediaHoverZoomToggle", settings::setMediaHoverZoom);
        wireSlider("mediaHoverZoomScaleSlider", settings::setMediaHoverZoomScale);
        wireSwitch("mediaShowDownloadButtonRow", "mediaShowDownloadButtonToggle", settings::setMediaShowDownloadButton);
        wireCheckbox("mediaOpenPreviewOnImageClickRow", "mediaOpenPreviewOnImageClickCheck",
                settings::setMediaOpenPreviewOnImageClick);

        wireSwitch("chatSendOnEnterRow", "chatSendOnEnterToggle", settings::setChatSendOnEnter);
        wireSwitch("chatAutoScrollToLatestRow", "chatAutoScrollToLatestToggle", settings::setChatAutoScrollToLatest);
        wireCheckbox("chatShowMessageTimestampsRow", "chatShowMessageTimestampsCheck",
                settings::setChatShowMessageTimestamps);

        wireSwitch("notificationsShowUnreadBadgesRow", "notificationsShowUnreadBadgesToggle",
                settings::setNotificationsShowUnreadBadges);
        wireSlider("notificationsBadgeCapSlider", settings::setNotificationsBadgeCap);
        wireCheckbox("notificationsShowRuntimePopupsRow", "notificationsShowRuntimePopupsCheck",
                settings::setNotificationsShowRuntimePopups);

        wireSwitch("privacyBlurOnFocusLossRow", "privacyBlurOnFocusLossToggle", settings::setPrivacyBlurOnFocusLoss);
        wireSlider("privacyBlurStrengthSlider", settings::setPrivacyBlurStrength);
        wireCheckbox("privacyConfirmAttachmentOpenRow", "privacyConfirmAttachmentOpenCheck",
                settings::setPrivacyConfirmAttachmentOpen);
        wireSwitch("privacyHidePresenceIndicatorsRow", "privacyHidePresenceIndicatorsToggle",
                settings::setPrivacyHidePresenceIndicators);
    }

    private void wireSwitch(String rowId, String controlId, Consumer<Boolean> sink) {
        JFXToggleButton toggle = findById(controlId, JFXToggleButton.class);
        if (toggle == null) {
            return;
        }
        toggle.selectedProperty().addListener((obs, oldValue, newValue) -> sink.accept(Boolean.TRUE.equals(newValue)));
        wireOverlayRowToggle(rowId, () -> toggle.setSelected(!toggle.isSelected()));
    }

    private void wireCheckbox(String rowId, String controlId, Consumer<Boolean> sink) {
        JFXCheckBox checkBox = findById(controlId, JFXCheckBox.class);
        if (checkBox == null) {
            return;
        }
        checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> sink.accept(Boolean.TRUE.equals(newValue)));
        wireOverlayRowToggle(rowId, () -> checkBox.setSelected(!checkBox.isSelected()));
    }

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

    private <T extends Node> T findById(String nodeId, Class<T> nodeType) {
        if (nodeId == null || nodeType == null || rootContainer == null) {
            return null;
        }
        return findByIdRecursive(rootContainer, nodeId, nodeType);
    }

    private static <T extends Node> T findByIdRecursive(Node node, String nodeId, Class<T> nodeType) {
        if (nodeId.equals(node.getId()) && nodeType.isInstance(node)) {
            return nodeType.cast(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = findByIdRecursive(child, nodeId, nodeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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

            if (minimizeButton != null) {
                minimizeButton.setOnAction(e -> stage.setIconified(true));
            }
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

    private void hideStage(Stage stage, AtomicBoolean closingStage) {
        closingStage.set(true);
        try {
            stage.hide();
        } finally {
            closingStage.set(false);
        }
    }

    private void storeRestartRequestHandler(Runnable restartRequestHandler) {
        if (rootContainer == null) {
            return;
        }
        rootContainer.getProperties().put(RESTART_HANDLER_KEY, restartRequestHandler);
    }

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
     * Cell for settings left navigation menu, rendered from settings_item_cell.fxml.
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
                LOGGER.error( "Could not load settings_item_cell.fxml", ex);
            }
        }

        /**
         * Updates cell graphics and menu-item text/icon content.
         *
         * @param item menu item payload
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
