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


    // Popup window chrome
    @FXML
    private BorderPane rootContainer;
    @FXML
    private HBox titleBar;
    @FXML
    private JFXButton closeButton;

    // Popup content and action buttons
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

    /**
     * Initializes popup controls, drag behavior, and button event wiring.
     */
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

    /**
     * Applies a popup specification to the currently displayed popup instance.
     *
     * @param spec popup specification to display; falls back to current spec when null
     */
    public void showMessage(PopupMessageSpec spec) {
        currentSpec = spec == null ? currentSpec : spec;
        applySpec(currentSpec);
    }

    /**
     * Pushes popup spec values into the UI controls.
     *
     * @param spec popup specification to render
     */
    private void applySpec(PopupMessageSpec spec) {
        if (titleText != null) {
            titleText.setText(spec.title());
        }
        if (messageText != null) {
            messageText.setText(spec.message());
        }
        if (actionButton != null) {
            actionButton.setText(spec.actionText());
            actionButton.getStyleClass().remove("button-action-danger");
            if (spec.dangerAction() && !actionButton.getStyleClass().contains("button-action-danger")) {
                actionButton.getStyleClass().add("button-action-danger");
            }
        }
        if (cancelButton != null) {
            cancelButton.setText(spec.cancelText());
            cancelButton.setVisible(spec.showCancel());
            cancelButton.setManaged(spec.showCancel());
        }
    }

    /**
     * Handles primary action clicks and executes the configured action callback.
     */
    private void handleAction() {
        hideThenRun(currentSpec.onAction());
    }

    /**
     * Handles cancel/close actions and executes the configured cancel callback.
     */
    private void handleCancel() {
        hideThenRun(currentSpec.onCancel());
    }

    /**
     * Hides the popup window and then executes a callback if provided.
     *
     * @param action callback to execute after the popup is hidden
     */
    private void hideThenRun(Runnable action) {
        Stage stage = resolveStage();
        if (stage != null) {
            stage.hide();
        }
        if (action != null) {
            action.run();
        }
    }

    /**
     * Enables drag-to-move behavior using the popup title bar.
     */
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

    /**
     * Resolves the hosting popup stage from the current root container.
     *
     * @return hosting {@link Stage}, or {@code null} when scene/window is not available
     */
    private Stage resolveStage() {
        if (rootContainer == null || rootContainer.getScene() == null || rootContainer.getScene().getWindow() == null) {
            return null;
        }
        return (Stage) rootContainer.getScene().getWindow();
    }
}
