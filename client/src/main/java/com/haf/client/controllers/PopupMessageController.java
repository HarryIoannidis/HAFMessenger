package com.haf.client.controllers;

import com.haf.client.utils.PopupMessageSpec;
import com.jfoenix.controls.JFXButton;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * Controller for reusable popup-message dialogs.
 */
public class PopupMessageController {

    private static final String DANGER_STYLE_CLASS = "button-action-danger";

    @FXML
    private BorderPane rootContainer;
    @FXML
    private HBox titleBar;
    @FXML
    private JFXButton closeButton;
    @FXML
    private Label titleText;
    @FXML
    private Label messageText;
    @FXML
    private JFXButton cancelButton;
    @FXML
    private JFXButton actionButton;

    private double xOffset;
    private double yOffset;
    private PopupMessageSpec currentSpec = new PopupMessageSpec(
            "popup-message-default",
            "",
            "",
            "OK",
            "Cancel",
            true,
            false,
            null,
            null);

    @FXML
    public void initialize() {
        setupDragBehavior();
        if (closeButton != null) {
            closeButton.setOnAction(e -> handleCancel());
        }
        if (cancelButton != null) {
            cancelButton.setOnAction(e -> handleCancel());
        }
        if (actionButton != null) {
            actionButton.setOnAction(e -> handleAction());
        }
    }

    public void showMessage(PopupMessageSpec spec) {
        currentSpec = spec == null ? currentSpec : spec;
        applySpec(currentSpec);
    }

    private void applySpec(PopupMessageSpec spec) {
        if (titleText != null) {
            titleText.setText(spec.title());
        }
        if (messageText != null) {
            messageText.setText(spec.message());
        }
        if (actionButton != null) {
            actionButton.setText(spec.actionText());
            actionButton.getStyleClass().remove(DANGER_STYLE_CLASS);
            if (spec.dangerAction() && !actionButton.getStyleClass().contains(DANGER_STYLE_CLASS)) {
                actionButton.getStyleClass().add(DANGER_STYLE_CLASS);
            }
        }
        if (cancelButton != null) {
            cancelButton.setText(spec.cancelText());
            cancelButton.setVisible(spec.showCancel());
            cancelButton.setManaged(spec.showCancel());
        }
    }

    private void handleAction() {
        hideThenRun(currentSpec.onAction());
    }

    private void handleCancel() {
        hideThenRun(currentSpec.onCancel());
    }

    private void hideThenRun(Runnable action) {
        Stage stage = resolveStage();
        if (stage != null) {
            stage.hide();
        }
        if (action != null) {
            action.run();
        }
    }

    private void setupDragBehavior() {
        if (titleBar == null) {
            return;
        }

        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            Stage stage = resolveStage();
            if (stage == null) {
                return;
            }
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }

    private Stage resolveStage() {
        if (rootContainer == null || rootContainer.getScene() == null || rootContainer.getScene().getWindow() == null) {
            return null;
        }
        return (Stage) rootContainer.getScene().getWindow();
    }
}
