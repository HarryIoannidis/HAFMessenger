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
        viewModel.startBootstrap(this::navigateToLogin, this::showFailureDialog);
    }

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

    private void applyProgressBarClip() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(progressBar.widthProperty());
        clip.heightProperty().bind(progressBar.heightProperty());

        clip.setArcWidth(24);
        clip.setArcHeight(24);
        progressBar.setClip(clip);
    }

    private void navigateToLogin() {
        ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN);
    }

    private void showFailureDialog(Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Initialization failed");
        alert.setHeaderText("Startup could not complete");
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

