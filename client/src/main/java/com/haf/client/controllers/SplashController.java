package com.haf.client.controllers;

import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.SplashViewModel;
import com.haf.client.utils.UiConstants;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

public class SplashController {

    @FXML
    private StackPane rootContainer;

    @FXML
    private ImageView logo;

    @FXML
    private Text title;

    @FXML
    private Text subtitle;

    @FXML
    private Text status;

    @FXML
    private Text percentage;

    @FXML
    private Text version;

    @FXML
    private ProgressBar progressBar;

    private final SplashViewModel viewModel = SplashViewModel.createDefault();

    @FXML
    public void initialize() {
        bindViewModel();
        // viewModel.startBootstrap(this::navigateToRegister, this::showFailureDialog);
        viewModel.startBootstrap(this::navigateToLogin, this::showFailureDialog);
    }

    /**
     * Binds ViewModel properties to UI components.
     */
    private void bindViewModel() {
        status.textProperty().bind(viewModel.statusProperty());
        progressBar.progressProperty().bind(viewModel.progressProperty());
        percentage.textProperty().bind(viewModel.percentageProperty());
        version.textProperty().bind(viewModel.versionProperty());

        progressBar.visibleProperty().bind(viewModel.errorProperty().not());
        progressBar.managedProperty().bind(viewModel.errorProperty().not());
        percentage.visibleProperty().bind(viewModel.errorProperty().not());
        percentage.managedProperty().bind(viewModel.errorProperty().not());
    }

    /**
     * Navigates to the Login screen.
     */
    private void navigateToLogin() {
        ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN);
    }

    /**
     * Navigates to the Register screen.
     */
    private void navigateToRegister() {
        ViewRouter.switchToTransparent(UiConstants.FXML_REGISTER);
    }

    /**
     * Displays a failure dialog with retry/cancel options.
     *
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