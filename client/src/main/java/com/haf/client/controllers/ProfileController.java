package com.haf.client.controllers;

import com.haf.client.models.UserProfileInfo;
import com.haf.client.utils.PopupMessageBuilder;
import com.haf.client.utils.UiConstants;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
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

    @FXML
    private BorderPane rootContainer;
    @FXML
    private HBox titleBar;
    @FXML
    private JFXButton minimizeButton;
    @FXML
    private JFXButton closeButton;
    @FXML
    private Text userIdText;
    @FXML
    private Text fullNameValueText;
    @FXML
    private Text rankValueText;
    @FXML
    private Text regNumberValueText;
    @FXML
    private Text joinedDateValueText;
    @FXML
    private Text emailValueText;
    @FXML
    private Text telephoneValueText;
    @FXML
    private JFXButton requestEditButton;
    @FXML
    private JFXButton requestDeletionButton;

    private double xOffset;
    private double yOffset;
    private UserProfileInfo profile;

    @FXML
    public void initialize() {
        setupWindowControls();
    }

    public void showProfile(UserProfileInfo profile) {
        this.profile = profile;
        applyProfile();
    }

    private void applyProfile() {
        if (profile == null) {
            return;
        }

        userIdText.setText(formatUserId(profile.userId()));
        fullNameValueText.setText(formatValue(profile.fullName()));
        rankValueText.setText(formatValue(profile.rank()));
        regNumberValueText.setText(formatValue(profile.regNumber()));
        joinedDateValueText.setText(formatValue(profile.joinedDate()));
        emailValueText.setText(formatValue(profile.email()));
        telephoneValueText.setText(formatValue(profile.telephone()));

        boolean showSelfActions = profile.selfProfile();
        configureSelfActionVisibility(showSelfActions);
    }

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

    private Stage resolveStage() {
        if (rootContainer == null || rootContainer.getScene() == null || rootContainer.getScene().getWindow() == null) {
            return null;
        }
        return (Stage) rootContainer.getScene().getWindow();
    }

    private static String formatUserId(String value) {
        if (value == null || value.isBlank()) {
            return MISSING_VALUE;
        }
        return "#" + value;
    }

    private static String formatValue(String value) {
        if (value == null || value.isBlank()) {
            return MISSING_VALUE;
        }
        return value;
    }
}
