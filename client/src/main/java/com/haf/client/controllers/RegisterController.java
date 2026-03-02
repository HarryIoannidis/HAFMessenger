package com.haf.client.controllers;

import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.RegisterViewModel;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
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
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RegisterController {

    private static final Logger LOGGER = Logger.getLogger(RegisterController.class.getName());

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
    private JFXButton backButton;

    @FXML
    private StackPane registerButtonContainer;

    @FXML
    private Text gotoSignInButton;

    @FXML
    private Text errorText;

    @FXML
    private ComboBox<String> rankComboBox;

    @FXML
    private FontIcon dropDownButton;

    @FXML
    private VBox credentialsVBox;

    @FXML
    private VBox photosVBox;

    @FXML
    private Text photoTitleText;

    @FXML
    private StackPane dropZoneContainer;

    @FXML
    private VBox previewSateZoneId;

    @FXML
    private ImageView previewThumbnail;

    @FXML
    private Text previewNameText;

    @FXML
    private Text previewSizeText;

    @FXML
    private JFXButton previewReplaceButton;

    @FXML
    private JFXButton previewRemoveButton;

    @FXML
    private VBox emptyStateZoneSelfie;

    @FXML
    private VBox emptyStateZoneId;

    @FXML
    private VBox errorStateZoneId;

    @FXML
    private Text dropZoneErrorText;

    @FXML
    private Button minimizeButton;

    @FXML
    private Button maximizeButton;

    @FXML
    private Button closeButton;

    private enum RegistrationStep {
        CREDENTIALS,
        ID_PHOTO,
        SELFIE_PHOTO
    }

    private RegistrationStep currentStep = RegistrationStep.CREDENTIALS;

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
        setupDragAndDrop();
        initializeStep();
    }

    private void initializeStep() {
        // Initial State: Credentials
        currentStep = RegistrationStep.CREDENTIALS;
        credentialsVBox.setVisible(true);
        credentialsVBox.setManaged(true);
        photosVBox.setVisible(false);
        photosVBox.setManaged(false);
        registerButton.setText("Next Step");

        // Back button is hidden on the first step
        if (backButton != null) {
            backButton.setVisible(false);
            backButton.setManaged(false);
            HBox.setMargin(backButton, Insets.EMPTY);
        }
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

        // Back button click
        if (backButton != null) {
            backButton.setOnAction(event -> handleBack());
        }

        // Dropdown button click to open rank ComboBox popup
        if (dropDownButton != null && rankComboBox != null) {
            dropDownButton.setOnMouseClicked(event -> {
                // Ensure focus then show popup
                rankComboBox.requestFocus();
                rankComboBox.show();
            });
        }

        // Navigate to Sign In
        gotoSignInButton.setOnMouseClicked(event -> navigateToLogin());

        // Enter key on last field triggers register
        passwordConfField.setOnAction(event -> handleRegister());

        setupInputInteractionListeners();
        setupValidationErrorListeners();
    }

    private void setupInputInteractionListeners() {
        // Clear errors when user types in any field
        addClearErrorListener(nameField, UiConstants.STYLE_TEXT_FIELD_ERROR);
        addClearErrorListener(regNumField, UiConstants.STYLE_TEXT_FIELD_ERROR);
        addClearErrorListener(idNumField, UiConstants.STYLE_TEXT_FIELD_ERROR);
        addClearErrorListener(phoneNumField, UiConstants.STYLE_TEXT_FIELD_ERROR);
        addClearErrorListener(emailField, UiConstants.STYLE_TEXT_FIELD_ERROR);
        addClearErrorListener(passwordField, UiConstants.STYLE_PASSWORD_FIELD_ERROR);
        addClearErrorListener(passwordConfField, UiConstants.STYLE_PASSWORD_FIELD_ERROR);

        if (passwordVisibleField != null)
            addClearErrorListener(passwordVisibleField, UiConstants.STYLE_TEXT_FIELD_ERROR);
        if (passwordConfVisibleField != null)
            addClearErrorListener(passwordConfVisibleField, UiConstants.STYLE_TEXT_FIELD_ERROR);

        // Clear errors when rank is selected
        rankComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.clearErrors();
            rankComboBox.getStyleClass().remove(UiConstants.STYLE_TEXT_FIELD_ERROR);
        });

        // Sync rank ComboBox value to ViewModel
        rankComboBox.valueProperty().addListener((obs, oldVal, newVal) -> viewModel.rankProperty().set(newVal));
    }

    private void setupValidationErrorListeners() {
        // Apply error styling reactively for each field
        addErrorStyleListener(viewModel.nameErrorProperty(), nameField, UiConstants.STYLE_TEXT_FIELD_ERROR);
        addErrorStyleListener(viewModel.regNumErrorProperty(), regNumField, UiConstants.STYLE_TEXT_FIELD_ERROR);
        addErrorStyleListener(viewModel.idNumErrorProperty(), idNumField, UiConstants.STYLE_TEXT_FIELD_ERROR);
        addErrorStyleListener(viewModel.phoneNumErrorProperty(), phoneNumField, UiConstants.STYLE_TEXT_FIELD_ERROR);
        addErrorStyleListener(viewModel.emailErrorProperty(), emailField, UiConstants.STYLE_TEXT_FIELD_ERROR);
        addErrorStyleListener(viewModel.passwordErrorProperty(), passwordField, UiConstants.STYLE_PASSWORD_FIELD_ERROR);
        addErrorStyleListener(viewModel.passwordConfErrorProperty(), passwordConfField,
                UiConstants.STYLE_PASSWORD_FIELD_ERROR);

        if (passwordVisibleField != null)
            addErrorStyleListener(viewModel.passwordErrorProperty(), passwordVisibleField,
                    UiConstants.STYLE_TEXT_FIELD_ERROR);
        if (passwordConfVisibleField != null)
            addErrorStyleListener(viewModel.passwordConfErrorProperty(), passwordConfVisibleField,
                    UiConstants.STYLE_TEXT_FIELD_ERROR);

        // Rank ComboBox error styling
        addErrorStyleListener(viewModel.rankErrorProperty(), rankComboBox, UiConstants.STYLE_TEXT_FIELD_ERROR);
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
    private void addErrorStyleListener(BooleanProperty errorProp, Control field, String errorStyleClass) {
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
     * Validates input based on the current step and proceeds.
     */
    private void handleRegister() {
        if (viewModel.loadingProperty().get()) {
            return;
        }

        switch (currentStep) {
            case CREDENTIALS:
                if (viewModel.validateCredentials()) {
                    transitionToIdPhoto();
                } else {
                    shakeInvalidFields();
                }
                break;

            case ID_PHOTO:
                if (viewModel.validateIdPhoto()) {
                    transitionToSelfiePhoto();
                } else {
                    handlePhotoError();
                }
                break;

            case SELFIE_PHOTO:
                if (viewModel.validateSelfiePhoto()) {
                    performRegistration();
                } else {
                    handlePhotoError();
                }
                break;
        }
    }

    private void handlePhotoError() {
        dropZoneContainer.setStyle(UiConstants.STYLE_BORDER_ERROR);
        shakeNode(dropZoneContainer);

        // Determine if it's a missing file scenario
        boolean isMissingFile = (currentStep == RegistrationStep.ID_PHOTO
                && viewModel.idPhotoFileProperty().get() == null) ||
                (currentStep == RegistrationStep.SELFIE_PHOTO && viewModel.selfiePhotoFileProperty().get() == null);

        if (!isMissingFile) {
            // If not missing, it means invalid file (type/size), so we show the error
            // state.
            if (viewModel.getErrorMessage() != null && !viewModel.getErrorMessage().isEmpty()
                    && dropZoneErrorText != null) {
                dropZoneErrorText.setText(viewModel.getErrorMessage());
            } else if (dropZoneErrorText != null) {
                // Reset to default if no specific message
                dropZoneErrorText.setText("Only images up to 10MB are allowed");
            }
            showErrorState();
        }

        if (isMissingFile) {
            String message = (currentStep == RegistrationStep.ID_PHOTO)
                    ? "Please upload your ID photo"
                    : "Please upload your photo";
            if (dropZoneErrorText != null) {
                dropZoneErrorText.setText(message);
            }
            showErrorState();
        }
    }

    private void shakeInvalidFields() {
        if (viewModel.nameErrorProperty().get())
            shakeNode(nameField.getParent());
        if (viewModel.regNumErrorProperty().get())
            shakeNode(regNumField.getParent());
        if (viewModel.idNumErrorProperty().get())
            shakeNode(idNumField.getParent());
        if (viewModel.rankErrorProperty().get())
            shakeNode(rankComboBox.getParent());
        if (viewModel.phoneNumErrorProperty().get())
            shakeNode(phoneNumField.getParent());
        if (viewModel.emailErrorProperty().get())
            shakeNode(emailField.getParent());
        if (viewModel.passwordErrorProperty().get()) {
            shakeNode(passwordField.getParent());
        }
        if (viewModel.passwordConfErrorProperty().get()) {
            shakeNode(passwordConfField.getParent());
        }
    }

    private void performRegistration() {
        // Validation passed — attempt registration
        viewModel.loadingProperty().set(true);
        viewModel.errorMessageProperty().set("");
        if (dropZoneErrorText != null)
            dropZoneErrorText.setText("");

        // Store original text to restore if needed
        String originalText = registerButton.getText();
        registerButton.setText("Register...");

        Thread registrationThread = new Thread(() -> {
            try {
                // Simulate network delay
                Thread.sleep(1500);

                // TODO: Replace with actual server registration call

                javafx.application.Platform.runLater(() -> {
                    viewModel.loadingProperty().set(false);
                    // On success, we might navigate away. For now, we restore text or show success.
                    LOGGER.log(Level.INFO, "Registration validation passed for: {0}", viewModel.getEmail());
                    LOGGER.log(Level.INFO, "Registration simulated successfully.");

                    // If we stay on this screen:
                    registerButton.setText(originalText);
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                javafx.application.Platform.runLater(() -> {
                    viewModel.loadingProperty().set(false);
                    registerButton.setText(originalText);
                    viewModel.setRegistrationError("Registration interrupted.");
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    viewModel.loadingProperty().set(false);
                    registerButton.setText(originalText);
                    viewModel.setRegistrationError("Registration failed.");
                });
            }
        });
        registrationThread.setDaemon(true);
        registrationThread.start();
    }

    private void transitionToIdPhoto() {
        currentStep = RegistrationStep.ID_PHOTO;

        // Hide Credentials, Show Photos
        credentialsVBox.setVisible(false);
        credentialsVBox.setManaged(false);
        photosVBox.setVisible(true);
        photosVBox.setManaged(true);

        // Show back button
        if (backButton != null) {
            backButton.setVisible(true);
            backButton.setManaged(true);
            HBox.setMargin(backButton, new Insets(0, 20, 0, 0));
        }

        // Setup ID View
        photoTitleText.setText("ID Photo");
        registerButton.setText("Last Step");

        updateDropZoneView();
    }

    private void transitionToSelfiePhoto() {
        currentStep = RegistrationStep.SELFIE_PHOTO;

        // Back button stays visible on the last step

        // Setup Selfie View
        photoTitleText.setText("Your Picture"); // Or Selfie
        registerButton.setText("Register");

        updateDropZoneView();
    }

    /**
     * Handles the back button action.
     * Returns to the previous step in the registration flow.
     */
    private void handleBack() {
        viewModel.clearErrors();

        switch (currentStep) {
            case ID_PHOTO:
                // Go back to credentials
                currentStep = RegistrationStep.CREDENTIALS;
                credentialsVBox.setVisible(true);
                credentialsVBox.setManaged(true);
                photosVBox.setVisible(false);
                photosVBox.setManaged(false);
                registerButton.setText("Next Step");

                // Hide back button on the first step
                if (backButton != null) {
                    backButton.setVisible(false);
                    backButton.setManaged(false);
                    HBox.setMargin(backButton, Insets.EMPTY);
                }
                break;

            case SELFIE_PHOTO:
                // Go back to ID photo
                transitionToIdPhoto();
                break;

            default:
                break;
        }
    }

    private void updateDropZoneView() {
        // Reset Error Style
        dropZoneContainer.setStyle("");
        dropZoneErrorText.setText("");
        viewModel.errorMessageProperty().set("");

        // Hide all inner VBoxes first
        previewSateZoneId.setVisible(false);
        emptyStateZoneId.setVisible(false);
        emptyStateZoneSelfie.setVisible(false);
        errorStateZoneId.setVisible(false);

        File currentParamsFile = (currentStep == RegistrationStep.ID_PHOTO)
                ? viewModel.idPhotoFileProperty().get()
                : viewModel.selfiePhotoFileProperty().get();

        if (currentParamsFile != null) {
            // Show Preview
            showPreview(currentParamsFile);
        } else {
            // Show Empty State
            if (currentStep == RegistrationStep.ID_PHOTO) {
                emptyStateZoneId.setVisible(true);
            } else {
                emptyStateZoneSelfie.setVisible(true);
            }
        }
    }

    private void showPreview(File file) {
        previewSateZoneId.setVisible(true);
        emptyStateZoneId.setVisible(false);
        emptyStateZoneSelfie.setVisible(false);
        errorStateZoneId.setVisible(false);

        previewNameText.setText(file.getName());

        // Calculate size in KB or MB
        double bytes = file.length();
        if (bytes < 1024 * 1024) {
            double kb = bytes / 1024.0;
            previewSizeText.setText(String.format("%.2f KB", kb));
        } else {
            double mb = bytes / (1024.0 * 1024.0);
            previewSizeText.setText(String.format("%.2f MB", mb));
        }

        try {
            Image image = new Image(file.toURI().toString());
            previewThumbnail.setImage(image);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load image preview", e);
        }
    }

    private void setupDragAndDrop() {
        dropZoneContainer.setOnDragOver(event -> {
            if (event.getGestureSource() != dropZoneContainer && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        dropZoneContainer.setOnDragDropped(event -> {
            boolean success = false;
            // Clear error text and style immediately on drop
            dropZoneContainer.setStyle("");
            if (dropZoneErrorText != null) {
                dropZoneErrorText.setText("");
            }
            if (event.getDragboard().hasFiles()) {
                List<File> files = event.getDragboard().getFiles();
                if (!files.isEmpty()) {
                    handleFileSelection(files.getFirst());
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        // Click to browse
        dropZoneContainer.setOnMouseClicked(event -> {
            if (currentStep == RegistrationStep.CREDENTIALS)
                return;

            if (!previewSateZoneId.isVisible()) {
                browseFile();
            }
        });

        // Initialize buttons in preview
        previewReplaceButton.setOnAction(e -> browseFile());
        previewRemoveButton.setOnAction(e -> removeFile());
    }

    private void browseFile() {
        // Reset UI to clean state (clears errors, resets borders)
        updateDropZoneView();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = fileChooser.showOpenDialog(rootContainer.getScene().getWindow());
        if (file != null) {
            handleFileSelection(file);
        }
    }

    private void removeFile() {
        if (dropZoneErrorText != null)
            dropZoneErrorText.setText("");
        if (currentStep == RegistrationStep.ID_PHOTO) {
            viewModel.idPhotoFileProperty().set(null);
        } else {
            viewModel.selfiePhotoFileProperty().set(null);
        }
        updateDropZoneView();
    }

    private void handleFileSelection(File file) {
        // Reset styles and text
        dropZoneContainer.setStyle("");
        if (dropZoneErrorText != null)
            dropZoneErrorText.setText("");

        if (viewModel.validateFile(file)) {
            if (currentStep == RegistrationStep.ID_PHOTO) {
                viewModel.idPhotoFileProperty().set(file);
            } else {
                viewModel.selfiePhotoFileProperty().set(file);
            }
            updateDropZoneView();
        } else {
            // Show Error State
            if (viewModel.getErrorMessage() != null && !viewModel.getErrorMessage().isEmpty()
                    && dropZoneErrorText != null) {
                dropZoneErrorText.setText(viewModel.getErrorMessage());
            }
            showErrorState();
        }
    }

    private void showErrorState() {
        previewSateZoneId.setVisible(false);
        emptyStateZoneId.setVisible(false);
        emptyStateZoneSelfie.setVisible(false);
        errorStateZoneId.setVisible(true);

        // Also red border
        dropZoneContainer.setStyle(UiConstants.STYLE_BORDER_ERROR);
        shakeNode(dropZoneContainer);
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
            closeButton.setOnAction(e -> {
                javafx.application.Platform.exit();
                System.exit(0);
            });
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
                UiConstants.RANK_SMINAGOS,
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
    private static class RankListCell extends ListCell<String> {
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
                case UiConstants.RANK_SMINAGOS -> UiConstants.ICON_RANK_SMINAGOS;
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

    // Get the selected rank
    public String getSelectedRank() {
        return rankComboBox.getValue();
    }
}