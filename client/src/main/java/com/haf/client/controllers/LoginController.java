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
    private static final String LOADING_CHATS_TEXT = "Loading chats...";
    private static final String PREF_EMAIL = "remembered_email";
    private static final String PREF_REMEMBER = "remember_me";

    // Window chrome and layout containers
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

    // Login form controls
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

    // Navigation and feedback
    @FXML
    private Text gotoRegisterButton;
    @FXML
    private Text errorText;

    // Window control buttons
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

    /**
     * Creates a login controller using the default login service.
     */
    public LoginController() {
        this(new DefaultLoginService());
    }

    /**
     * Creates a login controller with an injected login service.
     *
     * @param loginService login service used for authentication calls
     */
    LoginController(LoginService loginService) {
        this.loginService = Objects.requireNonNull(loginService, "loginService");
    }

    /**
     * Initializes UI bindings, listeners, and persisted preference state.
     */
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

    /**
     * Wires keyboard/input interactions and clears validation styling while user
     * edits fields.
     */
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

    /**
     * Observes validation state flags and updates field error styling.
     */
    private void setupValidationErrorListeners() {
        // Apply error styling reactively
        viewModel.emailErrorProperty().addListener((obs, oldVal, newVal) -> handleEmailError(newVal));
        viewModel.passwordErrorProperty().addListener((obs, oldVal, newVal) -> handlePasswordError(newVal));
    }

    /**
     * Applies/removes email error style classes.
     *
     * @param isError whether email field is currently invalid
     */
    private void handleEmailError(Boolean isError) {
        if (Boolean.TRUE.equals(isError)) {
            if (!emailField.getStyleClass().contains(UiConstants.STYLE_TEXT_FIELD_ERROR)) {
                emailField.getStyleClass().add(UiConstants.STYLE_TEXT_FIELD_ERROR);
            }
        } else {
            emailField.getStyleClass().remove(UiConstants.STYLE_TEXT_FIELD_ERROR);
        }
    }

    /**
     * Applies/removes password error style classes on hidden and visible password
     * controls.
     *
     * @param isError whether password field is currently invalid
     */
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

    /**
     * Toggles password-field visibility and keeps caret/focus behavior consistent.
     */
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

    /**
     * Executes the login request on a background thread.
     */
    private void performLoginTask() {
        LoginService.LoginResult result = loginService.login(buildLoginCommand());
        handleLoginResult(result);
    }

    /**
     * Creates a login command from current view-model credentials.
     *
     * @return login command containing email and password input values
     */
    LoginService.LoginCommand buildLoginCommand() {
        return new LoginService.LoginCommand(viewModel.getEmail(), viewModel.getPassword());
    }

    /**
     * Routes login result variants to specialized handlers.
     *
     * @param result login result returned by the service layer
     */
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

    /**
     * Handles successful authentication by navigating to the main screen.
     */
    private void handleLoginSuccess() {
        javafx.application.Platform.runLater(() -> {
            signInButton.setText(LOADING_CHATS_TEXT);
            savePreferences();
            // Queue the view switch on the next UI turn so the loading label can render.
            javafx.application.Platform.runLater(() -> {
                try {
                    ViewRouter.switchToTransparent(UiConstants.FXML_MAIN);
                    LOGGER.log(Level.INFO, () -> "Login successful for: " + viewModel.getEmail() + ", Socket connected.");
                } catch (Exception ex) {
                    viewModel.loadingProperty().set(false);
                    signInButton.setText(SIGN_IN_TEXT);
                    showMainLoadFailurePopup(ex);
                }
            });
        });
    }

    /**
     * Displays a popup when main chat UI fails to load after successful authentication.
     *
     * @param error scene-load error
     */
    private void showMainLoadFailurePopup(Throwable error) {
        String reason = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "Unknown error."
                : error.getMessage();

        PopupMessageBuilder.create()
                .popupKey(UiConstants.POPUP_VIEW_LOAD_ERROR)
                .title("Failed to load chats")
                .message("Login succeeded, but chat loading failed. " + reason)
                .actionText("OK")
                .singleAction(true)
                .show();
    }

    /**
     * Handles explicit credential rejection from backend.
     *
     * @param errorMsg rejection reason text
     */
    private void handleRejectedLoginResponse(String errorMsg) {
        javafx.application.Platform.runLater(() -> {
            viewModel.loadingProperty().set(false);
            signInButton.setText(SIGN_IN_TEXT);
            viewModel.setLoginError(errorMsg);
            shakeNode(signInButton);
        });
    }

    /**
     * Handles service failure results and special-cases secure-session bootstrap
     * failures.
     *
     * @param message failure message returned by the login service
     */
    private void handleFailureResult(String message) {
        if ("Failed to initialize secure session locally.".equals(message)) {
            handleSecureSessionInitializationError();
            return;
        }
        handleLoginError(message);
    }

    /**
     * Updates UI for secure-session initialization failure after successful
     * credential validation.
     */
    private void handleSecureSessionInitializationError() {
        javafx.application.Platform.runLater(() -> {
            viewModel.loadingProperty().set(false);
            signInButton.setText(SIGN_IN_TEXT);
            viewModel.setLoginError("Failed to initialize secure session locally.");
            shakeNode(signInButton);
        });
    }

    /**
     * Loads remember-me preferences and restores saved email when enabled.
     */
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

    /**
     * Persists remember-me choice and associated email.
     */
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

    /**
     * Applies generic login error state to the UI.
     *
     * @param message error message to display
     */
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

    /**
     * Shakes currently invalid input fields for visual validation feedback.
     */
    private void shakeInvalidFields() {
        if (viewModel.emailErrorProperty().get()) {
            shakeNode(emailField.getParent());
        }
        if (viewModel.passwordErrorProperty().get()) {
            shakeNode(passwordField.getParent());
        }
    }

    /**
     * Shows an exit confirmation popup and terminates the process on confirm.
     */
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
