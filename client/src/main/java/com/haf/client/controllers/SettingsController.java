package com.haf.client.controllers;

import com.haf.client.models.SettingsMenuItem;
import com.haf.client.utils.SettingsRowBuilder;
import com.haf.client.utils.UiConstants;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the settings popup window.
 */
public class SettingsController {

    private static final Logger LOGGER = Logger.getLogger(SettingsController.class.getName());

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
        setupMenuList();
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
                            true),
                    SettingsRowBuilder.buildSwitchRow(
                            "generalRememberWindowStateRow",
                            "generalRememberWindowStateToggle",
                            "Remember Window State",
                            "Restore the previous window size and position when reopening the app.",
                            true),
                    SettingsRowBuilder.buildCheckboxRow(
                            "generalRestoreLastTabRow",
                            "generalRestoreLastTabCheck",
                            "Restore Last Tab",
                            "Open the last active toolbar tab after startup.",
                            true));
        }

        if (searchRowsContainer != null) {
            searchRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSwitchRow(
                            "searchInstantOnTypeRow",
                            "searchInstantOnTypeToggle",
                            "Instant Search While Typing",
                            "Run user search automatically as query text changes.",
                            false),
                    SettingsRowBuilder.buildSwitchRow(
                            "searchInfiniteScrollRow",
                            "searchInfiniteScrollToggle",
                            "Enable Infinite Scroll",
                            "Load additional results when reaching the bottom of the results area.",
                            true),
                    SettingsRowBuilder.buildSliderRow(
                            "searchResultsPerPageRow",
                            "searchResultsPerPageSlider",
                            "Results Per Page",
                            "Set how many search results are requested per page.",
                            10.0,
                            100.0,
                            20.0,
                            10.0,
                            true,
                            true),
                    SettingsRowBuilder.buildCheckboxRow(
                            "searchPreserveLastQueryRow",
                            "searchPreserveLastQueryCheck",
                            "Preserve Last Query",
                            "Keep the last search text when returning to the Search tab.",
                            false));
        }

        if (mediaRowsContainer != null) {
            mediaRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSwitchRow(
                            "mediaHoverZoomRow",
                            "mediaHoverZoomToggle",
                            "Enable Hover Zoom",
                            "Apply subtle zoom and cursor-based panning inside image preview.",
                            true),
                    SettingsRowBuilder.buildSliderRow(
                            "mediaHoverZoomScaleRow",
                            "mediaHoverZoomScaleSlider",
                            "Hover Zoom Scale",
                            "Control how strong the preview zoom effect should be.",
                            1.05,
                            1.50,
                            1.15,
                            0.05,
                            true,
                            true),
                    SettingsRowBuilder.buildSwitchRow(
                            "mediaShowDownloadButtonRow",
                            "mediaShowDownloadButtonToggle",
                            "Show Download Button",
                            "Display the download action in the preview popup toolbar.",
                            true),
                    SettingsRowBuilder.buildCheckboxRow(
                            "mediaOpenPreviewOnImageClickRow",
                            "mediaOpenPreviewOnImageClickCheck",
                            "Open Preview On Image Click",
                            "Open the image preview popup when clicking image messages.",
                            true));
        }

        if (chatRowsContainer != null) {
            chatRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSwitchRow(
                            "chatSendOnEnterRow",
                            "chatSendOnEnterToggle",
                            "Send On Enter",
                            "Send the current draft when pressing Enter in the message field.",
                            true),
                    SettingsRowBuilder.buildSwitchRow(
                            "chatAutoScrollToLatestRow",
                            "chatAutoScrollToLatestToggle",
                            "Auto Scroll To Latest",
                            "Keep the message view anchored to the newest message.",
                            true),
                    SettingsRowBuilder.buildCheckboxRow(
                            "chatShowMessageTimestampsRow",
                            "chatShowMessageTimestampsCheck",
                            "Show Message Timestamps",
                            "Render sent/received times on each chat bubble.",
                            true));
        }

        if (notificationsRowsContainer != null) {
            notificationsRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSwitchRow(
                            "notificationsShowUnreadBadgesRow",
                            "notificationsShowUnreadBadgesToggle",
                            "Show Unread Badges",
                            "Display unread counters on contact rows in the left list.",
                            true),
                    SettingsRowBuilder.buildSliderRow(
                            "notificationsBadgeCapRow",
                            "notificationsBadgeCapSlider",
                            "Unread Badge Cap",
                            "Set the maximum count displayed before switching to a capped badge value.",
                            9.0,
                            99.0,
                            9.0,
                            9.0,
                            true,
                            true),
                    SettingsRowBuilder.buildCheckboxRow(
                            "notificationsShowRuntimePopupsRow",
                            "notificationsShowRuntimePopupsCheck",
                            "Show Runtime Popups",
                            "Show runtime issue popup dialogs for recoverable application errors.",
                            true));
        }

        if (privacyRowsContainer != null) {
            privacyRowsContainer.getChildren().setAll(
                    SettingsRowBuilder.buildSwitchRow(
                            "privacyBlurOnFocusLossRow",
                            "privacyBlurOnFocusLossToggle",
                            "Blur On Focus Loss",
                            "Blur sensitive content when the main window loses focus.",
                            false),
                    SettingsRowBuilder.buildSliderRow(
                            "privacyBlurStrengthRow",
                            "privacyBlurStrengthSlider",
                            "Blur Strength",
                            "Control the intensity of the privacy blur overlay.",
                            1.0,
                            10.0,
                            4.0,
                            1.0,
                            true,
                            true),
                    SettingsRowBuilder.buildCheckboxRow(
                            "privacyConfirmAttachmentOpenRow",
                            "privacyConfirmAttachmentOpenCheck",
                            "Confirm Attachment Open",
                            "Ask for confirmation before opening or downloading attachments.",
                            false),
                    SettingsRowBuilder.buildSwitchRow(
                            "privacyHidePresenceIndicatorsRow",
                            "privacyHidePresenceIndicatorsToggle",
                            "Hide Presence Indicators",
                            "Hide online/offline indicators and related activity status badges.",
                            false));
        }
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

            if (minimizeButton != null) {
                minimizeButton.setOnAction(e -> stage.setIconified(true));
            }
            if (closeButton != null) {
                closeButton.setOnAction(e -> stage.hide());
            }
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
                LOGGER.log(Level.SEVERE, "Could not load settings_item_cell.fxml", ex);
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
