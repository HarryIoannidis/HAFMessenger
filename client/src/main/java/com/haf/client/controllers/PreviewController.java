package com.haf.client.controllers;

import com.haf.client.builders.PopupMessageBuilder;
import com.haf.client.utils.ClientSettings;
import com.haf.client.utils.ImageSaveSupport;
import com.haf.client.utils.PopupMessageSpec;
import com.haf.client.utils.UiConstants;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for image preview popup window.
 */
public class PreviewController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreviewController.class);
    private static final double PREVIEW_MIN_WINDOW_WIDTH = 560.0;
    private static final double PREVIEW_MIN_WINDOW_HEIGHT = 420.0;
    private static final double PREVIEW_MAX_WINDOW_WIDTH_RATIO = 0.90;
    private static final double PREVIEW_MAX_WINDOW_HEIGHT_RATIO = 0.88;
    private static final double PREVIEW_CHROME_WIDTH = 64.0;
    private static final double PREVIEW_CHROME_HEIGHT = 170.0;
    private static final double PREVIEW_FALLBACK_IMAGE_WIDTH = 700.0;
    private static final double PREVIEW_FALLBACK_IMAGE_HEIGHT = 520.0;

    // Popup window controls
    @FXML
    private BorderPane rootContainer;
    @FXML
    private HBox titleBar;
    @FXML
    private JFXButton closeButton;

    // Preview content and actions
    @FXML
    private StackPane previewImageContainer;
    @FXML
    private ImageView previewImageView;
    @FXML
    private ProgressIndicator loadingSpinner;
    @FXML
    private JFXButton downloadButton;

    private double xOffset;
    private double yOffset;
    private String imageUriOrPath;
    private String suggestedFileName;
    private boolean downloadAllowed;
    private long activePreviewLoadId;
    private ClientSettings settings = ClientSettings.defaults();

    /**
     * Initializes window controls and download button wiring.
     */
    @FXML
    public void initialize() {
        setupWindowControls();
        setupPreviewHoverZoom();
        if (downloadButton != null) {
            downloadButton.setOnAction(e -> handleDownloadClick());
        }
    }

    /**
     * Displays an image preview and configures optional download support.
     *
     * @param imageUriOrPath    image URI/path to preview
     * @param suggestedFileName preferred filename shown in save dialog
     * @param downloadAllowed   whether downloading the previewed image is allowed
     */
    public void showImage(String imageUriOrPath, String suggestedFileName, boolean downloadAllowed) {
        this.imageUriOrPath = imageUriOrPath;
        this.suggestedFileName = suggestedFileName;
        this.downloadAllowed = downloadAllowed;

        configureDownloadButton(downloadAllowed);
        requestPreviewWindowStabilization();
        loadPreviewImage(imageUriOrPath);
    }

    /**
     * Injects active settings for media preview behavior.
     *
     * @param settings active settings instance
     */
    public void setSettings(ClientSettings settings) {
        this.settings = settings == null ? ClientSettings.defaults() : settings;
    }

    /**
     * Updates visibility and enabled state for the download button.
     *
     * @param allowed whether the current preview source can be downloaded
     */
    private void configureDownloadButton(boolean allowed) {
        if (downloadButton == null) {
            return;
        }
        boolean visible = settings.isMediaShowDownloadButton();
        downloadButton.setVisible(visible);
        downloadButton.setManaged(visible);
        downloadButton.setDisable(!allowed || !visible);
    }

    /**
     * Loads an image into the preview component and tracks loading progress.
     *
     * @param source URI/path source to load into the preview image view
     */
    private void loadPreviewImage(String source) {
        if (previewImageView == null) {
            return;
        }
        long previewLoadId = ++activePreviewLoadId;

        previewImageView.setImage(null);
        previewImageView.setFitWidth(0);
        previewImageView.setFitHeight(0);
        resetHoverZoom();
        if (source == null || source.isBlank()) {
            setSpinnerVisible(false);
            requestPreviewWindowStabilization();
            return;
        }

        try {
            Image image = new Image(source, true);
            previewImageView.setImage(image);
            setSpinnerVisible(true);
            image.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal.doubleValue() >= 1.0) {
                    runOnFxThread(() -> completePreviewLoadIfCurrent(previewLoadId, image));
                }
            });
            image.errorProperty().addListener((obs, wasError, isError) -> {
                if (Boolean.TRUE.equals(isError) && isCurrentPreviewLoad(previewLoadId, image)) {
                    runOnFxThread(() -> {
                        setSpinnerVisible(false);
                        requestPreviewWindowStabilization();
                    });
                }
            });

            // Close the listener-registration race: local images can complete
            // between the initial progress check and listener attachment.
            if (image.getProgress() >= 1.0) {
                runOnFxThread(() -> completePreviewLoadIfCurrent(previewLoadId, image));
            } else if (image.isError()) {
                runOnFxThread(() -> {
                    setSpinnerVisible(false);
                    requestPreviewWindowStabilization();
                });
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to load preview image", ex);
            setSpinnerVisible(false);
            requestPreviewWindowStabilization();
            showAttachmentError("Could not load image preview.");
        }
    }

    /**
     * Applies completion UI updates only when the finished image belongs to
     * the latest preview request.
     *
     * @param previewLoadId sequence id captured when load started
     * @param image         loaded image candidate
     */
    private void completePreviewLoadIfCurrent(long previewLoadId, Image image) {
        if (!isCurrentPreviewLoad(previewLoadId, image)) {
            return;
        }
        setSpinnerVisible(false);
        applyImageDimensions(image);
        requestPreviewWindowStabilization();
    }

    /**
     * Checks whether a callback still targets the current previewed image.
     *
     * @param previewLoadId sequence id captured when load started
     * @param image         image tied to callback invocation
     * @return {@code true} when callback belongs to latest preview request
     */
    private boolean isCurrentPreviewLoad(long previewLoadId, Image image) {
        return previewLoadId == activePreviewLoadId
                && previewImageView != null
                && previewImageView.getImage() == image;
    }

    /**
     * Runs UI work on the JavaFX thread, immediately when already on FX thread.
     *
     * @param action UI action to run safely
     */
    private static void runOnFxThread(Runnable action) {
        if (action == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        Platform.runLater(action);
    }

    /**
     * Configures subtle hover zoom behavior with cursor-follow panning.
     */
    private void setupPreviewHoverZoom() {
        if (previewImageView == null) {
            return;
        }

        if (previewImageContainer != null) {
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(previewImageContainer.widthProperty());
            clip.heightProperty().bind(previewImageContainer.heightProperty());
            previewImageContainer.setClip(clip);
        }

        previewImageView.setOnMouseEntered(this::updateHoverZoom);
        previewImageView.setOnMouseMoved(this::updateHoverZoom);
        previewImageView.setOnMouseDragged(this::updateHoverZoom);
        previewImageView.setOnMouseExited(event -> resetHoverZoom());
    }

    /**
     * Handles user-initiated image download from the preview popup.
     */
    private void handleDownloadClick() {
        if (settings.isPrivacyConfirmAttachmentOpen()) {
            PopupMessageBuilder.create()
                    .popupKey("popup-confirm-attachment-open")
                    .title("Open attachment")
                    .message("Open or download this attachment?")
                    .actionText("Continue")
                    .cancelText("Cancel")
                    .showCancel(true)
                    .onAction(this::handleDownloadClickNow)
                    .show();
            return;
        }
        handleDownloadClickNow();
    }

    /**
     * Saves the current previewed image to user-selected destination immediately.
     */
    private void handleDownloadClickNow() {
        if (!downloadAllowed) {
            return;
        }

        Path sourcePath = ImageSaveSupport.resolveLocalSourcePath(imageUriOrPath);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            LOGGER.warn("Image source path is unavailable for download.");
            showAttachmentError("Image source file could not be found.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Image");
        ImageSaveSupport.configureImageSaveChooser(chooser, suggestedFileName, imageUriOrPath);

        Stage owner = resolveStage();
        File selected = chooser.showSaveDialog(owner);
        if (selected == null) {
            return;
        }

        try {
            Path targetPath = selected.toPath();
            if (sourcePath.equals(targetPath)) {
                return;
            }
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            LOGGER.warn("Failed to save image preview download", ex);
            showAttachmentError("Could not save image. Please try again.");
        }
    }

    /**
     * Configures drag/move/close behavior for the preview window.
     */
    private void setupWindowControls() {
        Platform.runLater(() -> {
            Stage stage = resolveStage();
            if (stage == null) {
                return;
            }
            stage.setResizable(false);
            stage.setMinWidth(PREVIEW_MIN_WINDOW_WIDTH);
            stage.setMinHeight(PREVIEW_MIN_WINDOW_HEIGHT);
            stage.setWidth(PREVIEW_MIN_WINDOW_WIDTH);
            stage.setHeight(PREVIEW_MIN_WINDOW_HEIGHT);
            applyStageMaxBounds(stage);

            if (closeButton != null) {
                closeButton.setOnAction(e -> stage.hide());
            }
            if (titleBar != null) {
                titleBar.setOnMousePressed(event -> {
                    xOffset = event.getSceneX();
                    yOffset = event.getSceneY();
                });
                titleBar.setOnMouseDragged(event -> {
                    stage.setX(event.getScreenX() - xOffset);
                    stage.setY(event.getScreenY() - yOffset);
                });
            }

            stage.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> requestPreviewWindowStabilization());
        });
    }

    /**
     * Resolves the stage hosting this preview controller.
     *
     * @return host stage, or {@code null} when scene/window has not been attached
     */
    private Stage resolveStage() {
        if (rootContainer == null || rootContainer.getScene() == null || rootContainer.getScene().getWindow() == null) {
            return null;
        }
        return (Stage) rootContainer.getScene().getWindow();
    }

    /**
     * Scales preview image dimensions to fit max bounds while preserving aspect
     * ratio.
     *
     * @param image loaded image to size in the preview container
     */
    private void applyImageDimensions(Image image) {
        if (image == null || image.isError()) {
            return;
        }
        double width = image.getWidth();
        double height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        PreviewBounds bounds = resolvePreviewBounds();
        double scale = Math.min(1.0, Math.min(bounds.maxImageWidth() / width, bounds.maxImageHeight() / height));
        double fittedWidth = width * scale;
        double fittedHeight = height * scale;
        previewImageView.setFitWidth(fittedWidth);
        previewImageView.setFitHeight(fittedHeight);
        applyPreviewLayout(fittedWidth, fittedHeight);
    }

    /**
     * Schedules two follow-up layout passes so preview geometry remains centered
     * even when late image/layout updates occur after initial popup show.
     */
    private void requestPreviewWindowStabilization() {
        runOnFxThread(() -> {
            stabilizePreviewWindow();
            Platform.runLater(this::stabilizePreviewWindow);
        });
    }

    /**
     * Re-applies preferred scene size and re-centers preview window over its owner.
     * This keeps the preview stable when image dimensions arrive asynchronously.
     */
    private void stabilizePreviewWindow() {
        Stage stage = resolveStage();
        if (stage == null || stage.getScene() == null || stage.getScene().getRoot() == null) {
            return;
        }

        stage.setMinWidth(PREVIEW_MIN_WINDOW_WIDTH);
        stage.setMinHeight(PREVIEW_MIN_WINDOW_HEIGHT);
        applyStageMaxBounds(stage);
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        stage.sizeToScene();
        clampStageToBounds(stage, resolvePreviewBounds());
        centerStageOverOwner(stage);
    }

    private void applyPreviewLayout(double imageWidth, double imageHeight) {
        double safeImageWidth = Math.max(1.0, imageWidth);
        double safeImageHeight = Math.max(1.0, imageHeight);
        PreviewBounds bounds = resolvePreviewBounds();

        if (previewImageContainer != null) {
            previewImageContainer.setMinWidth(safeImageWidth);
            previewImageContainer.setMinHeight(safeImageHeight);
            previewImageContainer.setPrefWidth(safeImageWidth);
            previewImageContainer.setPrefHeight(safeImageHeight);
            previewImageContainer.setMaxWidth(safeImageWidth);
            previewImageContainer.setMaxHeight(safeImageHeight);
        }

        if (rootContainer != null) {
            double targetWindowWidth = clampToRange(
                    safeImageWidth + PREVIEW_CHROME_WIDTH,
                    PREVIEW_MIN_WINDOW_WIDTH,
                    bounds.maxWindowWidth());
            double targetWindowHeight = clampToRange(
                    safeImageHeight + PREVIEW_CHROME_HEIGHT,
                    PREVIEW_MIN_WINDOW_HEIGHT,
                    bounds.maxWindowHeight());
            rootContainer.setPrefWidth(targetWindowWidth);
            rootContainer.setPrefHeight(targetWindowHeight);
            rootContainer.setMaxWidth(bounds.maxWindowWidth());
            rootContainer.setMaxHeight(bounds.maxWindowHeight());
        }
    }

    private void applyStageMaxBounds(Stage stage) {
        if (stage == null) {
            return;
        }
        PreviewBounds bounds = resolvePreviewBounds();
        stage.setMaxWidth(bounds.maxWindowWidth());
        stage.setMaxHeight(bounds.maxWindowHeight());
    }

    private static void clampStageToBounds(Stage stage, PreviewBounds bounds) {
        if (stage == null || bounds == null) {
            return;
        }
        if (stage.getWidth() > bounds.maxWindowWidth()) {
            stage.setWidth(bounds.maxWindowWidth());
        }
        if (stage.getHeight() > bounds.maxWindowHeight()) {
            stage.setHeight(bounds.maxWindowHeight());
        }
    }

    private PreviewBounds resolvePreviewBounds() {
        Rectangle2D visualBounds = resolveVisualBounds();
        double maxWindowWidth = Math.max(PREVIEW_MIN_WINDOW_WIDTH,
                visualBounds.getWidth() * PREVIEW_MAX_WINDOW_WIDTH_RATIO);
        double maxWindowHeight = Math.max(PREVIEW_MIN_WINDOW_HEIGHT,
                visualBounds.getHeight() * PREVIEW_MAX_WINDOW_HEIGHT_RATIO);
        double maxImageWidth = Math.max(PREVIEW_FALLBACK_IMAGE_WIDTH, maxWindowWidth - PREVIEW_CHROME_WIDTH);
        double maxImageHeight = Math.max(PREVIEW_FALLBACK_IMAGE_HEIGHT, maxWindowHeight - PREVIEW_CHROME_HEIGHT);
        return new PreviewBounds(maxImageWidth, maxImageHeight, maxWindowWidth, maxWindowHeight);
    }

    private Rectangle2D resolveVisualBounds() {
        Stage stage = resolveStage();
        if (stage == null) {
            return Screen.getPrimary().getVisualBounds();
        }

        Screen ownerScreen = screenForWindow(stage.getOwner());
        if (ownerScreen == null) {
            ownerScreen = screenForWindow(stage);
        }
        if (ownerScreen == null) {
            ownerScreen = Screen.getPrimary();
        }
        return ownerScreen.getVisualBounds();
    }

    private static Screen screenForWindow(Window window) {
        if (window == null) {
            return null;
        }
        double width = Math.max(1.0, window.getWidth());
        double height = Math.max(1.0, window.getHeight());
        var screens = Screen.getScreensForRectangle(window.getX(), window.getY(), width, height);
        if (screens == null || screens.isEmpty()) {
            return null;
        }
        return screens.getFirst();
    }

    private record PreviewBounds(
            double maxImageWidth,
            double maxImageHeight,
            double maxWindowWidth,
            double maxWindowHeight) {
    }

    /**
     * Centers the preview stage over its owner window when available.
     *
     * @param stage preview stage to reposition
     */
    private static void centerStageOverOwner(Stage stage) {
        if (stage == null) {
            return;
        }

        Window owner = stage.getOwner();
        if (owner == null || !owner.isShowing()) {
            stage.centerOnScreen();
            return;
        }

        double width = stage.getWidth();
        double height = stage.getHeight();
        if (width <= 0 || height <= 0) {
            stage.centerOnScreen();
            return;
        }

        stage.setX(owner.getX() + (owner.getWidth() - width) / 2.0);
        stage.setY(owner.getY() + (owner.getHeight() - height) / 2.0);
    }

    /**
     * Applies zoom/pan transform so the zoomed region follows cursor position.
     *
     * @param event pointer event within the image view
     */
    private void updateHoverZoom(MouseEvent event) {
        if (event == null || previewImageView == null || previewImageView.getImage() == null) {
            return;
        }
        if (!settings.isMediaHoverZoom()) {
            resetHoverZoom();
            return;
        }

        double width = previewImageView.getFitWidth();
        double height = previewImageView.getFitHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        double hoverZoomScale = settings.getMediaHoverZoomScale();
        previewImageView.setScaleX(hoverZoomScale);
        previewImageView.setScaleY(hoverZoomScale);

        double xRatio = clamp(event.getX() / width);
        double yRatio = clamp(event.getY() / height);

        double maxTranslateX = (width * hoverZoomScale - width) / 2.0;
        double maxTranslateY = (height * hoverZoomScale - height) / 2.0;

        previewImageView.setTranslateX((0.5 - xRatio) * maxTranslateX * 2.0);
        previewImageView.setTranslateY((0.5 - yRatio) * maxTranslateY * 2.0);
    }

    /**
     * Resets preview transforms to the default non-zoomed state.
     */
    private void resetHoverZoom() {
        if (previewImageView == null) {
            return;
        }
        previewImageView.setScaleX(1.0);
        previewImageView.setScaleY(1.0);
        previewImageView.setTranslateX(0.0);
        previewImageView.setTranslateY(0.0);
    }

    /**
     * Clamps a ratio to [0, 1].
     *
     * @param value raw ratio
     * @return clamped ratio
     */
    private static double clamp(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static double clampToRange(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Shows or hides the loading spinner.
     *
     * @param visible whether spinner should be visible/managed
     */
    private void setSpinnerVisible(boolean visible) {
        if (loadingSpinner == null) {
            return;
        }
        loadingSpinner.setVisible(visible);
        loadingSpinner.setManaged(visible);
    }

    /**
     * Displays an attachment-related error popup.
     *
     * @param message user-facing error message to display
     */
    private void showAttachmentError(String message) {
        PopupMessageSpec spec = buildAttachmentErrorSpec(message);
        PopupMessageBuilder.create()
                .popupKey(spec.popupKey())
                .title(spec.title())
                .message(spec.message())
                .actionText(spec.actionText())
                .singleAction(true)
                .movable(spec.movable())
                .show();
    }

    /**
     * Builds a standardized popup spec for attachment preview/save failures.
     *
     * @param message error message to show, or blank for default fallback
     * @return popup specification configured for attachment-error dialogs
     */
    static PopupMessageSpec buildAttachmentErrorSpec(String message) {
        String resolved = message == null || message.isBlank() ? "Attachment operation failed." : message;
        return new PopupMessageSpec(
                UiConstants.POPUP_ATTACHMENT_ERROR,
                "Attachment error",
                resolved,
                "OK",
                "Cancel",
                false,
                false,
                true,
                null,
                null);
    }
}
