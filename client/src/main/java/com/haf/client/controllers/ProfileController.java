package com.haf.client.controllers;

import com.haf.client.models.UserProfileInfo;
import com.haf.client.utils.PopupMessageBuilder;
import com.haf.client.utils.RankIconResolver;
import com.haf.client.utils.UiConstants;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for profile popup window.
 */
public class ProfileController {

    private static final Logger LOGGER = Logger.getLogger(ProfileController.class.getName());
    private static final String MISSING_VALUE = "\u2014";

    // Popup window controls
    @FXML
    private BorderPane rootContainer;
    @FXML
    private HBox titleBar;
    @FXML
    private JFXButton minimizeButton;
    @FXML
    private JFXButton closeButton;

    // Profile detail text nodes
    @FXML
    private Text userIdText;
    @FXML
    private Text fullNameValueText;
    @FXML
    private Text rankValueText;
    @FXML
    private ImageView rankImageView;
    @FXML
    private Text regNumberValueText;
    @FXML
    private Text joinedDateValueText;
    @FXML
    private Text emailValueText;
    @FXML
    private Text telephoneValueText;

    // Profile action buttons
    @FXML
    private JFXButton requestEditButton;
    @FXML
    private JFXButton requestDeletionButton;

    private double xOffset;
    private double yOffset;
    private UserProfileInfo profile;

    /**
     * Initializes profile popup window controls.
     */
    @FXML
    public void initialize() {
        setupWindowControls();
    }

    /**
     * Displays profile details in the popup.
     *
     * @param profile profile information to render
     */
    public void showProfile(UserProfileInfo profile) {
        this.profile = profile;
        applyProfile();
    }

    /**
     * Applies loaded profile data into UI fields and action visibility.
     */
    private void applyProfile() {
        if (profile == null) {
            return;
        }

        userIdText.setText(formatUserId(profile.userId()));
        fullNameValueText.setText(formatValue(profile.fullName()));
        rankValueText.setText(formatValue(profile.rank()));
        applyRankIcon(profile.rank());
        regNumberValueText.setText(formatValue(profile.regNumber()));
        joinedDateValueText.setText(formatValue(profile.joinedDate()));
        emailValueText.setText(formatValue(profile.email()));
        telephoneValueText.setText(formatValue(profile.telephone()));

        boolean showSelfActions = profile.selfProfile();
        configureSelfActionVisibility(showSelfActions);
    }

    /**
     * Toggles visibility of self-profile action buttons.
     *
     * @param visible whether self action buttons should be visible
     */
    private void configureSelfActionVisibility(boolean visible) {
        if (requestEditButton != null) {
            requestEditButton.setVisible(visible);
            requestEditButton.setManaged(visible);
            requestEditButton.setOnAction(visible ? e -> showStubDialog("Request edit") : null);
        }
        if (requestDeletionButton != null) {
            requestDeletionButton.setVisible(visible);
            requestDeletionButton.setManaged(visible);
            requestDeletionButton.setOnAction(visible ? e -> showStubDialog("Request deletion") : null);
        }
    }

    /**
     * Updates profile rank image using the rank-specific icon.
     *
     * @param rank rank label shown in profile details
     */
    private void applyRankIcon(String rank) {
        if (rankImageView == null) {
            return;
        }

        String iconPath = RankIconResolver.resolve(rank);
        var resource = getClass().getResource(iconPath);
        if (resource == null) {
            resource = getClass().getResource(UiConstants.ICON_RANK_DEFAULT);
        }

        if (resource == null) {
            rankImageView.setImage(null);
            return;
        }

        rankImageView.setImage(new Image(resource.toExternalForm(), true));
    }

    /**
     * Displays a placeholder popup for not-yet-implemented profile actions.
     *
     * @param actionLabel action name displayed in popup/log output
     */
    private void showStubDialog(String actionLabel) {
        LOGGER.log(Level.INFO, "{0} clicked.", actionLabel);
        PopupMessageBuilder.create()
                .popupKey(UiConstants.POPUP_PROFILE_STUB)
                .title(actionLabel)
                .message("This action is not implemented yet.")
                .actionText("OK")
                .singleAction(true)
                .show();
    }

    /**
     * Wires stage controls and draggable title bar behavior.
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
     * Formats user id values for display.
     *
     * @param value raw user id
     * @return formatted id (prefixed with {@code #}) or a placeholder when blank
     */
    private static String formatUserId(String value) {
        if (value == null || value.isBlank()) {
            return MISSING_VALUE;
        }
        return "#" + value;
    }

    /**
     * Formats optional text values for display.
     *
     * @param value raw value
     * @return original value, or placeholder when blank
     */
    private static String formatValue(String value) {
        if (value == null || value.isBlank()) {
            return MISSING_VALUE;
        }
        return value;
    }
}
