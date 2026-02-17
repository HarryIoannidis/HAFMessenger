package com.haf.client.controllers;

import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.RegisterViewModel;
import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import com.jfoenix.controls.JFXButton;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RegisterController {

    private static final Logger LOGGER = Logger.getLogger(RegisterController.class.getName());

    private static final String STYLE_TEXT_FIELD_ERROR = "text-field-error";
    private static final String STYLE_PASSWORD_FIELD_ERROR = "password-field-error";

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
    private TextField nameField;

    @FXML
    private TextField regNumField;

    @FXML
    private TextField idNumField;

    @FXML
    private TextField phoneNumField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField passwordConfField;

    @FXML
    private TextField passwordVisibleField;

    @FXML
    private TextField passwordConfVisibleField;

    @FXML
    private JFXButton togglePasswordButton;

    @FXML
    private FontIcon togglePasswordIcon;

    @FXML
    private JFXButton togglePasswordConfButton;

    @FXML
    private FontIcon togglePasswordConfIcon;

    @FXML
    private JFXButton registerButton;

    @FXML
    private StackPane registerButtonContainer;

    @FXML
    private Text gotoSignInButton;

    @FXML
    private Text errorText;

    @FXML
    private ComboBox<String> rankComboBox;

    @FXML
    private Button minimizeButton;

    @FXML
    private Button maximizeButton;

    @FXML
    private Button closeButton;

    private final RegisterViewModel viewModel = new RegisterViewModel();

    private double xOffset;
    private double yOffset;

    @FXML
    public void initialize() {
        setupRankComboBox();
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
        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        regNumField.textProperty().bindBidirectional(viewModel.regNumProperty());
        idNumField.textProperty().bindBidirectional(viewModel.idNumProperty());
        phoneNumField.textProperty().bindBidirectional(viewModel.phoneNumProperty());
        emailField.textProperty().bindBidirectional(viewModel.emailProperty());
        passwordField.textProperty().bindBidirectional(viewModel.passwordProperty());
        passwordConfField.textProperty().bindBidirectional(viewModel.passwordConfProperty());

        // Error message binding
        errorText.textProperty().bind(viewModel.errorMessageProperty());

        // Disable register button while loading
        registerButton.disableProperty().bind(viewModel.loadingProperty());
    }

    /**
     * Sets up event listeners for user interactions.
     */
    private void setupListeners() {
        // Register button click
        registerButton.setOnAction(event -> handleRegister());

        // Navigate to Sign In
        gotoSignInButton.setOnMouseClicked(event -> navigateToLogin());

        // Enter key on last field triggers register
        passwordConfField.setOnAction(event -> handleRegister());

        setupInputInteractionListeners();
        setupValidationErrorListeners();
    }

    private void setupInputInteractionListeners() {
        // Clear errors when user types in any field
        addClearErrorListener(nameField, STYLE_TEXT_FIELD_ERROR);
        addClearErrorListener(regNumField, STYLE_TEXT_FIELD_ERROR);
        addClearErrorListener(idNumField, STYLE_TEXT_FIELD_ERROR);
        addClearErrorListener(phoneNumField, STYLE_TEXT_FIELD_ERROR);
        addClearErrorListener(emailField, STYLE_TEXT_FIELD_ERROR);
        addClearErrorListener(passwordField, STYLE_PASSWORD_FIELD_ERROR);
        addClearErrorListener(passwordConfField, STYLE_PASSWORD_FIELD_ERROR);

        if (passwordVisibleField != null)
            addClearErrorListener(passwordVisibleField, STYLE_TEXT_FIELD_ERROR);
        if (passwordConfVisibleField != null)
            addClearErrorListener(passwordConfVisibleField, STYLE_TEXT_FIELD_ERROR);

        // Clear errors when rank is selected
        rankComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.clearErrors();
            rankComboBox.getStyleClass().remove(STYLE_TEXT_FIELD_ERROR);
        });

        // Sync rank ComboBox value to ViewModel
        rankComboBox.valueProperty().addListener((obs, oldVal, newVal) -> viewModel.rankProperty().set(newVal));
    }

    private void setupValidationErrorListeners() {
        // Apply error styling reactively for each field
        addErrorStyleListener(viewModel.nameErrorProperty(), nameField, STYLE_TEXT_FIELD_ERROR);
        addErrorStyleListener(viewModel.regNumErrorProperty(), regNumField, STYLE_TEXT_FIELD_ERROR);
        addErrorStyleListener(viewModel.idNumErrorProperty(), idNumField, STYLE_TEXT_FIELD_ERROR);
        addErrorStyleListener(viewModel.phoneNumErrorProperty(), phoneNumField, STYLE_TEXT_FIELD_ERROR);
        addErrorStyleListener(viewModel.emailErrorProperty(), emailField, STYLE_TEXT_FIELD_ERROR);
        addErrorStyleListener(viewModel.passwordErrorProperty(), passwordField, STYLE_PASSWORD_FIELD_ERROR);
        addErrorStyleListener(viewModel.passwordConfErrorProperty(), passwordConfField, STYLE_PASSWORD_FIELD_ERROR);

        if (passwordVisibleField != null)
            addErrorStyleListener(viewModel.passwordErrorProperty(), passwordVisibleField, STYLE_TEXT_FIELD_ERROR);
        if (passwordConfVisibleField != null)
            addErrorStyleListener(viewModel.passwordConfErrorProperty(), passwordConfVisibleField,
                    STYLE_TEXT_FIELD_ERROR);

        // Rank ComboBox error styling
        addErrorStyleListener(viewModel.rankErrorProperty(), rankComboBox, STYLE_TEXT_FIELD_ERROR);
    }

    private void setupPasswordToggle() {
        // Password Field Toggle
        if (passwordVisibleField != null) {
            passwordVisibleField.textProperty().bindBidirectional(viewModel.passwordProperty());
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
        }
        if (togglePasswordButton != null) {
            togglePasswordButton.setOnAction(event -> handleTogglePassword());
        }

        // Confirm Password Field Toggle
        if (passwordConfVisibleField != null) {
            passwordConfVisibleField.textProperty().bindBidirectional(viewModel.passwordConfProperty());
            passwordConfVisibleField.setVisible(false);
            passwordConfVisibleField.setManaged(false);
        }
        if (togglePasswordConfButton != null) {
            togglePasswordConfButton.setOnAction(event -> handleTogglePasswordConf());
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

        if (togglePasswordIcon != null) {
            togglePasswordIcon.setIconLiteral(visible ? "mdi2e-eye-off" : "mdi2e-eye");
        }
    }

    private void handleTogglePasswordConf() {
        viewModel.togglePasswordConfVisibility();
        boolean visible = viewModel.passwordConfVisibleProperty().get();

        if (passwordConfVisibleField != null) {
            passwordConfField.setVisible(!visible);
            passwordConfField.setManaged(!visible);
            passwordConfVisibleField.setVisible(visible);
            passwordConfVisibleField.setManaged(visible);

            if (visible) {
                passwordConfVisibleField.requestFocus();
                passwordConfVisibleField.positionCaret(passwordConfVisibleField.getText().length());
            } else {
                passwordConfField.requestFocus();
                passwordConfField.positionCaret(passwordConfField.getText().length());
            }
        }

        if (togglePasswordConfIcon != null) {
            togglePasswordConfIcon.setIconLiteral(visible ? "mdi2e-eye-off" : "mdi2e-eye");
        }
    }

    /**
     * Adds a listener that clears errors and removes error styling when the user
     * types.
     */
    private void addClearErrorListener(TextInputControl field, String errorStyleClass) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.clearErrors();
            field.getStyleClass().remove(errorStyleClass);
        });
    }

    /**
     * Adds a listener that applies/removes error styling based on a
     * BooleanProperty.
     */
    private void addErrorStyleListener(BooleanProperty errorProp,
            Control field,
            String errorStyleClass) {
        errorProp.addListener((obs, oldVal, newVal) -> {
            if (Boolean.TRUE.equals(newVal)) {
                if (!field.getStyleClass().contains(errorStyleClass)) {
                    field.getStyleClass().add(errorStyleClass);
                }
            } else {
                field.getStyleClass().remove(errorStyleClass);
            }
        });
    }

    /**
     * Handles the register button action.
     * Validates input through the ViewModel, then proceeds with registration.
     */
    private void handleRegister() {
        if (viewModel.loadingProperty().get()) {
            return;
        }

        if (!viewModel.validate()) {
            // Shake animation on register button for feedback
            if (registerButtonContainer != null) {
                shakeNode(registerButtonContainer);
            } else {
                shakeNode(registerButton);
            }
            return;
        }

        // Validation passed — attempt registration
        viewModel.loadingProperty().set(true);

        // TODO: Replace with actual server registration call
        LOGGER.log(Level.INFO, "Registration validation passed for: {0}", viewModel.getEmail());
        viewModel.loadingProperty().set(false);
    }

    /**
     * Navigates to the Login screen.
     */
    private void navigateToLogin() {
        ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN);
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
    private void shakeNode(Node node) {
        TranslateTransition shake = new TranslateTransition(
                Duration.millis(50), node);
        shake.setFromX(0);
        shake.setByX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> node.setTranslateX(0));
        shake.play();
    }

    /**
     * Sets up the rank ComboBox with items and custom cell rendering.
     */
    private void setupRankComboBox() {
        rankComboBox.getItems().addAll(
                UiConstants.RANK_YPOSMINIAS,
                UiConstants.RANK_SMINIAS,
                UiConstants.RANK_EPISMINIAS,
                UiConstants.RANK_ARCHISMINIAS,
                UiConstants.RANK_ANTHYPASPISTIS,
                UiConstants.RANK_ANTHYPOSMINAGOS,
                UiConstants.RANK_YPOSMINAGOS,
                UiConstants.RANK_EPISMINAGOS,
                UiConstants.RANK_ANTISMINARCHOS,
                UiConstants.RANK_SMINARCHOS,
                UiConstants.RANK_TAKSIARCOS,
                UiConstants.RANK_YPOPTERARCHOS,
                UiConstants.RANK_ANTIPTERARCHOS);

        // Set cell factory for rank icons in the dropdown list
        rankComboBox.setCellFactory(listView -> new RankListCell());

        // Set button cell for rank icon on the selected item
        rankComboBox.setButtonCell(new RankListCell());
    }

    /**
     * Custom ListCell to Display Rank with Icon.
     */
    private class RankListCell extends ListCell<String> {
        private final ImageView imageView = new ImageView();

        @Override
        protected void updateItem(String rank, boolean empty) {
            super.updateItem(rank, empty);

            if (empty || rank == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(rank);
                try {
                    String iconPath = getRankIconPath(rank);
                    Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(iconPath)));
                    imageView.setImage(image);
                    imageView.setFitWidth(UiConstants.RANK_ICON_SIZE);
                    imageView.setFitHeight(UiConstants.RANK_ICON_SIZE);
                    imageView.setPreserveRatio(true);
                    setGraphic(imageView);
                } catch (Exception e) {
                    setGraphic(null);
                }
            }
        }

        /**
         * Returns the rank's image path.
         *
         * @param rank the rank
         * @return the rank's corresponding image path
         */
        private String getRankIconPath(String rank) {
            return switch (rank) {
                case UiConstants.RANK_YPOSMINIAS -> UiConstants.ICON_RANK_YPOSMINIAS;
                case UiConstants.RANK_SMINIAS -> UiConstants.ICON_RANK_SMINIAS;
                case UiConstants.RANK_EPISMINIAS -> UiConstants.ICON_RANK_EPISMINIAS;
                case UiConstants.RANK_ARCHISMINIAS -> UiConstants.ICON_RANK_ARCHISMINIAS;
                case UiConstants.RANK_ANTHYPASPISTIS -> UiConstants.ICON_RANK_ANTHYPASPISTIS;
                case UiConstants.RANK_ANTHYPOSMINAGOS -> UiConstants.ICON_RANK_ANTHYPOSMINAGOS;
                case UiConstants.RANK_YPOSMINAGOS -> UiConstants.ICON_RANK_YPOSMINAGOS;
                case UiConstants.RANK_EPISMINAGOS -> UiConstants.ICON_RANK_EPISMINAGOS;
                case UiConstants.RANK_ANTISMINARCHOS -> UiConstants.ICON_RANK_ANTISMINARCHOS;
                case UiConstants.RANK_SMINARCHOS -> UiConstants.ICON_RANK_SMINARCHOS;
                case UiConstants.RANK_TAKSIARCOS -> UiConstants.ICON_RANK_TAKSIARCOS;
                case UiConstants.RANK_YPOPTERARCHOS -> UiConstants.ICON_RANK_YPOPTERARCHOS;
                case UiConstants.RANK_ANTIPTERARCHOS -> UiConstants.ICON_RANK_ANTIPTERARCHOS;
                case null, default -> UiConstants.ICON_RANK_DEFAULT;
            };
        }
    }

    // Get selected rank
    public String getSelectedRank() {
        return rankComboBox.getValue();
    }
}
