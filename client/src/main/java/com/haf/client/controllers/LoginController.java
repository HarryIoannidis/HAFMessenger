package com.haf.client.controllers;

import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.LoginViewModel;
import java.util.logging.Logger;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Controller for the Login view.
 * Handles UI events and delegates validation/business logic to the
 * LoginViewModel.
 */
public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    private static final String SIGN_IN_TEXT = "Sign In";

    @FXML
    private BorderPane rootContainer;

    @FXML
    private HBox titleBar;

    @FXML
    private HBox ttileHBox;

    @FXML
    private HBox buttonsHBox;

    @FXML
    private StackPane leftPanel;

    @FXML
    private StackPane rightPanel;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField passwordVisibleField;

    @FXML
    private JFXCheckBox rememberCheckBox;

    @FXML
    private JFXButton signInButton;

    @FXML
    private JFXButton togglePasswordButton;

    @FXML
    private FontIcon togglePasswordIcon;

    @FXML
    private Text gotoRegisterButton;

    @FXML
    private Text errorText;

    @FXML
    private Button minimizeButton;

    @FXML
    private Button maximizeButton;

    @FXML
    private Button closeButton;

    private final LoginViewModel viewModel = new LoginViewModel();

    private double xOffset;
    private double yOffset;

    @FXML
    public void initialize() {
        bindViewModel();
        setupListeners();
        setupPasswordToggle();
        setupWindowControls();
    }

    /**
     * Binds ViewModel properties to UI components.
     */
    private void bindViewModel() {
        // Bidirectional binding for input fields
        emailField.textProperty().bindBidirectional(viewModel.emailProperty());
        passwordField.textProperty().bindBidirectional(viewModel.passwordProperty());
        rememberCheckBox.selectedProperty().bindBidirectional(viewModel.rememberCredentialsProperty());

        // Error message binding
        errorText.textProperty().bind(viewModel.errorMessageProperty());

        // Disable sign in button while loading
        signInButton.disableProperty().bind(viewModel.loadingProperty());
    }

    /**
     * Sets up event listeners for user interactions.
     */
    private void setupListeners() {
        // Sign In button click
        signInButton.setOnAction(event -> handleSignIn());

        // Navigate to Register
        gotoRegisterButton.setOnMouseClicked(event -> navigateToRegister());

        setupInputInteractionListeners();
        setupValidationErrorListeners();
    }

    private void setupInputInteractionListeners() {
        // Clear errors when user types
        emailField.textProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.clearErrors();
            emailField.getStyleClass().remove(UiConstants.STYLE_TEXT_FIELD_ERROR);
        });

        passwordField.textProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.clearErrors();
            passwordField.getStyleClass().remove(UiConstants.STYLE_PASSWORD_FIELD_ERROR);
        });

        // Enter key on password field triggers sign in
        passwordField.setOnAction(event -> handleSignIn());
        emailField.setOnAction(event -> passwordField.requestFocus());
    }

    private void setupValidationErrorListeners() {
        // Apply error styling reactively
        viewModel.emailErrorProperty().addListener((obs, oldVal, newVal) -> handleEmailError(newVal));
        viewModel.passwordErrorProperty().addListener((obs, oldVal, newVal) -> handlePasswordError(newVal));
    }

    private void handleEmailError(Boolean isError) {
        if (Boolean.TRUE.equals(isError)) {
            if (!emailField.getStyleClass().contains(UiConstants.STYLE_TEXT_FIELD_ERROR)) {
                emailField.getStyleClass().add(UiConstants.STYLE_TEXT_FIELD_ERROR);
            }
        } else {
            emailField.getStyleClass().remove(UiConstants.STYLE_TEXT_FIELD_ERROR);
        }
    }

    private void handlePasswordError(Boolean isError) {
        if (Boolean.TRUE.equals(isError)) {
            if (!passwordField.getStyleClass().contains(UiConstants.STYLE_PASSWORD_FIELD_ERROR)) {
                passwordField.getStyleClass().add(UiConstants.STYLE_PASSWORD_FIELD_ERROR);
            }
            if (passwordVisibleField != null
                    && !passwordVisibleField.getStyleClass().contains(UiConstants.STYLE_TEXT_FIELD_ERROR)) {
                passwordVisibleField.getStyleClass().add(UiConstants.STYLE_TEXT_FIELD_ERROR);
            }
        } else {
            passwordField.getStyleClass().remove(UiConstants.STYLE_PASSWORD_FIELD_ERROR);
            if (passwordVisibleField != null) {
                passwordVisibleField.getStyleClass().remove(UiConstants.STYLE_TEXT_FIELD_ERROR);
            }
        }
    }

    /**
     * Sets up the password visibility toggle button.
     */
    private void setupPasswordToggle() {
        if (passwordVisibleField != null) {
            // Sync the visible text field with the password field
            passwordVisibleField.textProperty().bindBidirectional(viewModel.passwordProperty());
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
        }

        if (togglePasswordButton != null) {
            togglePasswordButton.setOnAction(event -> handleTogglePassword());
        }
    }

    private void handleTogglePassword() {
        viewModel.togglePasswordVisibility();
        boolean visible = viewModel.passwordVisibleProperty().get();

        if (passwordVisibleField != null) {
            passwordField.setVisible(!visible);
            passwordField.setManaged(!visible);
            passwordVisibleField.setVisible(visible);
            passwordVisibleField.setManaged(visible);

            if (visible) {
                passwordVisibleField.requestFocus();
                passwordVisibleField.positionCaret(passwordVisibleField.getText().length());
            } else {
                passwordField.requestFocus();
                passwordField.positionCaret(passwordField.getText().length());
            }
        }

        // Toggle icon
        if (togglePasswordIcon != null) {
            togglePasswordIcon.setIconLiteral(visible ? "mdi2e-eye-off" : "mdi2e-eye");
        }
    }

    /**
     * Handles the sign in button action.
     * Validates input through the ViewModel, then proceeds with login.
     */
    private void handleSignIn() {
        if (viewModel.loadingProperty().get()) {
            return;
        }

        if (!viewModel.validate()) {
            shakeInvalidFields();
            return;
        }

        // Validation passed — attempt login
        viewModel.loadingProperty().set(true);
        signInButton.setText("Signing in...");

        // TODO: Replace with actual server authentication call
        Thread loginThread = new Thread(() -> {
            try {
                // Simulated network delay
                Thread.sleep(1500);

                // TODO: Perform actual authentication here
                // Example:
                // boolean success = authService.login(viewModel.getEmail(),
                // viewModel.getPassword());

                boolean success = true; // Placeholder — replace with real auth

                javafx.application.Platform.runLater(() -> {
                    viewModel.loadingProperty().set(false);
                    signInButton.setText(SIGN_IN_TEXT);

                    if (success) {
                        // TODO: Navigate to main chat view
                        // ViewRouter.switchTo(UiConstants.FXML_MAIN_CHAT);
                        LOGGER.info("Login successful for: " + viewModel.getEmail());
                    } else {
                        viewModel.setLoginError(LoginViewModel.ERROR_LOGIN_FAILED);
                        shakeNode(signInButton);
                    }
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                javafx.application.Platform.runLater(() -> {
                    viewModel.loadingProperty().set(false);
                    signInButton.setText(SIGN_IN_TEXT);
                    viewModel.setLoginError("Connection was interrupted.");
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    viewModel.loadingProperty().set(false);
                    signInButton.setText(SIGN_IN_TEXT);
                    viewModel.setLoginError(e.getMessage());
                });
            }
        }, "login-thread");

        loginThread.setDaemon(true);
        loginThread.start();
    }

    /**
     * Navigates to the Register screen.
     */
    private void navigateToRegister() {
        ViewRouter.switchToTransparent(UiConstants.FXML_REGISTER);
    }

    /**
     * Sets up the custom window control buttons (minimize, maximize, close)
     * and enables title bar dragging for the undecorated stage.
     */
    private void setupWindowControls() {
        Stage stage = ViewRouter.getMainStage();

        if (minimizeButton != null) {
            minimizeButton.setOnAction(e -> stage.setIconified(true));
        }

        if (maximizeButton != null) {
            maximizeButton.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
        }

        if (closeButton != null) {
            closeButton.setOnAction(e -> stage.close());
        }

        // Enable window dragging via the title bar
        if (titleBar != null) {
            titleBar.setOnMousePressed(event -> {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            });

            titleBar.setOnMouseDragged(event -> {
                // Un-maximize on drag so the window can be moved freely
                if (stage.isMaximized()) {
                    stage.setMaximized(false);
                }
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            });
        }
    }

    /**
     * Applies a horizontal shake animation to a node for visual error feedback.
     *
     * @param node the node to shake
     */
    private void shakeNode(javafx.scene.Node node) {
        javafx.animation.TranslateTransition shake = new javafx.animation.TranslateTransition(
                javafx.util.Duration.millis(50), node);
        shake.setFromX(0);
        shake.setByX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> node.setTranslateX(0));
        shake.play();
    }

    private void shakeInvalidFields() {
        if (viewModel.emailErrorProperty().get()) {
            shakeNode(emailField.getParent());
        }
        if (viewModel.passwordErrorProperty().get()) {
            shakeNode(passwordField.getParent());
        }
    }
}
