package com.haf.client.controllers;

import com.haf.client.services.DefaultLoginService;
import com.haf.client.services.LoginService;
import com.haf.client.utils.PopupMessageBuilder;
import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.LoginViewModel;
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
import java.util.Objects;
import java.util.prefs.Preferences;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Login view.
 * Handles UI events and delegates validation/business logic to the
 * LoginViewModel.
 */
public class LoginController {

    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());

    private static final String SIGN_IN_TEXT = "Sign In";
    private static final String PREF_EMAIL = "remembered_email";
    private static final String PREF_REMEMBER = "remember_me";

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
    private final LoginService loginService;

    private double xOffset;
    private double yOffset;

    public LoginController() {
        this(new DefaultLoginService());
    }

    LoginController(LoginService loginService) {
        this.loginService = Objects.requireNonNull(loginService, "loginService");
    }

    @FXML
    public void initialize() {
        bindViewModel();
        setupListeners();
        setupPasswordToggle();
        setupWindowControls();
        loadPreferences();
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

        // Disable input fields while loading
        emailField.disableProperty().bind(viewModel.loadingProperty());
        passwordField.disableProperty().bind(viewModel.loadingProperty());
        if (passwordVisibleField != null) {
            passwordVisibleField.disableProperty().bind(viewModel.loadingProperty());
        }

        rememberCheckBox.disableProperty().bind(viewModel.loadingProperty());
        if (togglePasswordButton != null) {
            togglePasswordButton.disableProperty().bind(viewModel.loadingProperty());
        }
        if (gotoRegisterButton != null) {
            gotoRegisterButton.disableProperty().bind(viewModel.loadingProperty());
        }
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

        Thread loginThread = new Thread(this::performLoginTask, "login-thread");
        loginThread.setDaemon(true);
        loginThread.start();
    }

    private void performLoginTask() {
        LoginService.LoginResult result = loginService.login(buildLoginCommand());
        handleLoginResult(result);
    }

    LoginService.LoginCommand buildLoginCommand() {
        return new LoginService.LoginCommand(viewModel.getEmail(), viewModel.getPassword());
    }

    private void handleLoginResult(LoginService.LoginResult result) {
        if (result instanceof LoginService.LoginResult.Success) {
            handleLoginSuccess();
            return;
        }
        if (result instanceof LoginService.LoginResult.Rejected rejected) {
            handleRejectedLoginResponse(rejected.message());
            return;
        }
        if (result instanceof LoginService.LoginResult.Failure failure) {
            handleFailureResult(failure.message());
            return;
        }
        handleLoginError("Connection failed. Please try again.");
    }

    private void handleLoginSuccess() {
        javafx.application.Platform.runLater(() -> {
            viewModel.loadingProperty().set(false);
            signInButton.setText(SIGN_IN_TEXT);
            savePreferences();
            ViewRouter.switchToTransparent(UiConstants.FXML_MAIN);
            LOGGER.log(Level.INFO, () -> "Login successful for: " + viewModel.getEmail() + ", Socket connected.");
        });
    }

    private void handleRejectedLoginResponse(String errorMsg) {
        javafx.application.Platform.runLater(() -> {
            viewModel.loadingProperty().set(false);
            signInButton.setText(SIGN_IN_TEXT);
            viewModel.setLoginError(errorMsg);
            shakeNode(signInButton);
        });
    }

    private void handleFailureResult(String message) {
        if ("Failed to initialize secure session locally.".equals(message)) {
            handleSecureSessionInitializationError();
            return;
        }
        handleLoginError(message);
    }

    private void handleSecureSessionInitializationError() {
        javafx.application.Platform.runLater(() -> {
            viewModel.loadingProperty().set(false);
            signInButton.setText(SIGN_IN_TEXT);
            viewModel.setLoginError("Failed to initialize secure session locally.");
            shakeNode(signInButton);
        });
    }

    private void loadPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(LoginController.class);
        boolean remember = prefs.getBoolean(PREF_REMEMBER, false);
        if (remember) {
            String savedEmail = prefs.get(PREF_EMAIL, "");
            viewModel.setEmail(savedEmail);
            viewModel.rememberCredentialsProperty().set(true);
            javafx.application.Platform.runLater(passwordField::requestFocus);
        }
    }

    private void savePreferences() {
        Preferences prefs = Preferences.userNodeForPackage(LoginController.class);
        if (viewModel.isRememberCredentials()) {
            prefs.put(PREF_EMAIL, viewModel.getEmail());
            prefs.putBoolean(PREF_REMEMBER, true);
        } else {
            prefs.remove(PREF_EMAIL);
            prefs.putBoolean(PREF_REMEMBER, false);
        }
    }

    private void handleLoginError(String message) {
        javafx.application.Platform.runLater(() -> {
            viewModel.loadingProperty().set(false);
            signInButton.setText(SIGN_IN_TEXT);
            viewModel.setLoginError(message);
        });
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
            closeButton.setOnAction(e -> confirmExitApplication());
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

    private void confirmExitApplication() {
        PopupMessageBuilder.create()
                .popupKey(UiConstants.POPUP_CONFIRM_EXIT_APP)
                .title("Exit application")
                .message("Close HAF Messenger now?")
                .actionText("Exit")
                .cancelText("Cancel")
                .dangerAction(true)
                .onAction(() -> {
                    javafx.application.Platform.exit();
                    System.exit(0);
                })
                .show();
    }
}
