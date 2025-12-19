package com.haf.client.controllers;

import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.SplashViewModel;
import com.haf.client.utils.UiConstants;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

public class SplashController {

    @FXML private StackPane rootContainer;
    @FXML private ImageView logo;
    @FXML private Label title;
    @FXML private Label subtitle;
    @FXML private Label status;
    @FXML private Label percentage;
    @FXML private ProgressBar progressBar;
    @FXML private Label version;
    private final SplashViewModel viewModel = SplashViewModel.createDefault();

    /**
     * Initializes the controller class.
     * This method is automatically called after the FXML file has been loaded.
     */
    @FXML public void initialize() {
        bindViewModel();
        applyProgressBarClip();

        viewModel.startBootstrap(this::navigateToRegister, this::showFailureDialog);
//        viewModel.startBootstrap(this::navigateToLogin, this::showFailureDialog);
    }

    /**
     * Binds the ViewModel properties to the UI components.
     */
    private void bindViewModel() {
        status.textProperty().bind(viewModel.statusProperty());
        progressBar.progressProperty().bind(viewModel.progressProperty());
        percentage.textProperty().bind(viewModel.percentageProperty());
        version.textProperty().bind(viewModel.versionProperty());

        // Hide progress visuals on error state
        progressBar.visibleProperty().bind(viewModel.errorProperty().not());
        progressBar.managedProperty().bind(viewModel.errorProperty().not());
        percentage.visibleProperty().bind(viewModel.errorProperty().not());
        percentage.managedProperty().bind(viewModel.errorProperty().not());
    }

    /**
     * Applies a rounded rectangle clip to the progress bar for rounded corners.
     */
    private void applyProgressBarClip() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(progressBar.widthProperty());
        clip.heightProperty().bind(progressBar.heightProperty());

        clip.setArcWidth(UiConstants.SPLASH_PROGRESS_BAR_ARC);
        clip.setArcHeight(UiConstants.SPLASH_PROGRESS_BAR_ARC);
        progressBar.setClip(clip);
    }

    /**
     * Navigates to the login screen.
     */
    private void navigateToLogin() {
        ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN);
    }

    /**
     * Navigates to the register screen.
     */
    private void navigateToRegister() {
        ViewRouter.switchToTransparent(UiConstants.FXML_REGISTER);
    }

    /**
     * Displays a failure dialog with the given error message.
     * @param error the Throwable containing the error details
     */
    private void showFailureDialog(Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(UiConstants.DIALOG_INIT_FAILED_TITLE);
        alert.setHeaderText(UiConstants.DIALOG_INIT_FAILED_HEADER);
        String message = (error != null && error.getMessage() != null) ? error.getMessage() : String.valueOf(error);
        alert.setContentText(message);
        alert.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        alert.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                viewModel.startBootstrap(this::navigateToLogin, this::showFailureDialog);
            } else {
                ViewRouter.close();
            }
        });
    }
}

