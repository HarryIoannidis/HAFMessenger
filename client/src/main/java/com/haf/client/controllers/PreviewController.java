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
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
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
    private static final double DEFAULT_CLICK_ZOOM_SCALE = 2.0;
    private static final int MAX_TEMPORARY_ZOOM_STEP = 2;
    private static final double UNZOOMED_SCALE = 1.0;
    private static final double ZOOM_EPSILON = 0.0001;

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
    private int previewZoomStep;
    private boolean hoverActive;
    private Cursor zoomInCursor = Cursor.HAND;
    private Cursor zoomOutCursor = Cursor.OPEN_HAND;

    /**
     * Initializes window controls and download button wiring.
     */
    @FXML
    public void initialize() {
        setupWindowControls();
        setupPreviewClickZoom();
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
        resetPreviewZoomForCurrentWindow();
        resetPreviewLayout();
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
        resetPreviewZoomForCurrentWindow();
        configureDownloadButton(downloadAllowed);
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
        applyCurrentPreviewZoomScale();
        refreshPreviewZoomCursor();
        if (loadingSpinner != null) {
            loadingSpinner.progressProperty().unbind();
            loadingSpinner.setProgress(-1);
        }
        if (source == null || source.isBlank()) {
            setSpinnerVisible(false);
            requestPreviewWindowStabilization();
            return;
        }

        try {
            Image image = new Image(source, true);
            previewImageView.setImage(image);
            if (loadingSpinner != null) {
                loadingSpinner.progressProperty().bind(image.progressProperty());
            }
            setSpinnerVisible(true);
            image.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal.doubleValue() >= 1.0) {
                    runOnFxThread(() -> completePreviewLoadIfCurrent(previewLoadId, image));
                }
            });
            image.errorProperty().addListener((obs, wasError, isError) -> {
                if (Boolean.TRUE.equals(isError) && isCurrentPreviewLoad(previewLoadId, image)) {
                    runOnFxThread(() -> {
                        if (loadingSpinner != null) {
                            loadingSpinner.progressProperty().unbind();
                        }
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
                    if (loadingSpinner != null) {
                        loadingSpinner.progressProperty().unbind();
                    }
                    setSpinnerVisible(false);
                    requestPreviewWindowStabilization();
                });
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to load preview image", ex);
            if (loadingSpinner != null) {
                loadingSpinner.progressProperty().unbind();
            }
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
        if (loadingSpinner != null) {
            loadingSpinner.progressProperty().unbind();
        }
        setSpinnerVisible(false);
        applyImageDimensions(image);
        applyCurrentPreviewZoomScale();
        refreshPreviewZoomCursor();
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
     * Configures click-driven preview zoom behavior with cursor-follow panning.
     */
    private void setupPreviewClickZoom() {
        if (previewImageView == null) {
            return;
        }

        if (previewImageContainer != null) {
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(previewImageContainer.widthProperty());
            clip.heightProperty().bind(previewImageContainer.heightProperty());
            previewImageContainer.setClip(clip);
        }
        initializeZoomCursors();
        previewImageView.setOnMouseEntered(this::handlePreviewMouseEntered);
        previewImageView.setOnMouseMoved(this::updateZoomPanFromPointer);
        previewImageView.setOnMouseDragged(this::updateZoomPanFromPointer);
        previewImageView.setOnMouseClicked(this::togglePreviewZoom);
        previewImageView.setOnMouseExited(this::handlePreviewMouseExited);
        resetPreviewZoomForCurrentWindow();
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
     * Resets preview layout constraints to minimum dimensions, clearing any
     * stale sizing from a previously displayed image. This prevents the reused
     * popup window from retaining old container bounds that can cause side
     * clipping when the next image has different dimensions.
     */
    private void resetPreviewLayout() {
        if (previewImageContainer != null) {
            previewImageContainer.setMinWidth(PREVIEW_FALLBACK_IMAGE_WIDTH);
            previewImageContainer.setMinHeight(PREVIEW_FALLBACK_IMAGE_HEIGHT);
            previewImageContainer.setPrefWidth(PREVIEW_FALLBACK_IMAGE_WIDTH);
            previewImageContainer.setPrefHeight(PREVIEW_FALLBACK_IMAGE_HEIGHT);
            previewImageContainer.setMaxWidth(PREVIEW_FALLBACK_IMAGE_WIDTH);
            previewImageContainer.setMaxHeight(PREVIEW_FALLBACK_IMAGE_HEIGHT);
        }
        if (rootContainer != null) {
            rootContainer.setPrefWidth(PREVIEW_MIN_WINDOW_WIDTH);
            rootContainer.setPrefHeight(PREVIEW_MIN_WINDOW_HEIGHT);
        }
        Stage stage = resolveStage();
        if (stage != null) {
            stage.setWidth(PREVIEW_MIN_WINDOW_WIDTH);
            stage.setHeight(PREVIEW_MIN_WINDOW_HEIGHT);
        }
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
     * Resets zoom state for the currently opened preview window.
     * The image always opens at normal (1x) scale. Hover zoom only activates
     * when the mouse actually enters the image area.
     */
    private void resetPreviewZoomForCurrentWindow() {
        previewZoomStep = 0;
        hoverActive = false;
        applyCurrentPreviewZoomScale();
        refreshPreviewZoomCursor();
    }

    /**
     * Handles mouse entering the preview image area.
     * Activates hover-driven zoom when hover zoom is enabled and not
     * click-toggled off for this window.
     *
     * @param event mouse entered event
     */
    private void handlePreviewMouseEntered(MouseEvent event) {
        hoverActive = true;
        updateZoomPanFromPointer(event);
    }

    /**
     * Handles mouse leaving the preview image area.
     * Deactivates hover zoom and resets transform back to normal scale.
     *
     * @param event mouse exited event
     */
    private void handlePreviewMouseExited(MouseEvent event) {
        hoverActive = false;
        applyCurrentPreviewZoomScale();
        refreshPreviewZoomCursor();
    }

    /**
     * Toggles zoom level on preview-image click.
     * With hover zoom enabled, click toggles between configured zoom and normal
     * size.
     * Without hover zoom, click cycles through 1x -> 2x -> 4x -> 1x.
     */
    private void togglePreviewZoom(MouseEvent event) {
        if (event == null || previewImageView == null || previewImageView.getImage() == null) {
            return;
        }
        if (!settings.isMediaClickZoom()) {
            return;
        }
        if (settings.isMediaHoverZoom()) {
            // Toggle hover zoom off/on for this window only.
            // Step 0 = hover zoom active (default), step 1 = hover zoom disabled for this
            // window.
            previewZoomStep = previewZoomStep > 0 ? 0 : 1;
        } else {
            previewZoomStep = previewZoomStep >= MAX_TEMPORARY_ZOOM_STEP ? 0 : previewZoomStep + 1;
        }
        applyCurrentPreviewZoomScale();
        updateZoomPanFromPointer(event);
        refreshPreviewZoomCursor();
    }

    /**
     * Applies zoom/pan transform so the zoomed region follows cursor position.
     *
     * @param event pointer event within the image view
     */
    private void updateZoomPanFromPointer(MouseEvent event) {
        if (event == null || previewImageView == null || previewImageView.getImage() == null) {
            return;
        }
        refreshPreviewZoomCursor();
        double zoomScale = resolveActivePreviewZoomScale();
        if (zoomScale <= UNZOOMED_SCALE + ZOOM_EPSILON) {
            resetPreviewTransformOnly();
            return;
        }

        double width = previewImageView.getFitWidth();
        double height = previewImageView.getFitHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        previewImageView.setScaleX(zoomScale);
        previewImageView.setScaleY(zoomScale);

        double xRatio = clamp(event.getX() / width);
        double yRatio = clamp(event.getY() / height);

        double maxTranslateX = (width * zoomScale - width) / 2.0;
        double maxTranslateY = (height * zoomScale - height) / 2.0;

        previewImageView.setTranslateX((0.5 - xRatio) * maxTranslateX * 2.0);
        previewImageView.setTranslateY((0.5 - yRatio) * maxTranslateY * 2.0);
    }

    /**
     * Resolves the active zoom scale from settings and the current zoom step.
     *
     * @return current scale applied to the preview image
     */
    private double resolveActivePreviewZoomScale() {
        if (settings.isMediaHoverZoom()) {
            // Hover zoom: active when mouse is over image AND not click-toggled off.
            // previewZoomStep == 0 means hover zoom is active (default).
            // previewZoomStep == 1 means user clicked to disable hover zoom for this
            // window.
            boolean hoverZoomDisabledByClick = previewZoomStep > 0;
            if (hoverActive && !hoverZoomDisabledByClick) {
                return settings.getMediaHoverZoomScale();
            }
            return UNZOOMED_SCALE;
        }
        return Math.pow(DEFAULT_CLICK_ZOOM_SCALE, Math.max(0, previewZoomStep));
    }

    /**
     * Applies the current zoom scale to the preview image view and recenters
     * translation when zoom returns to normal size.
     */
    private void applyCurrentPreviewZoomScale() {
        if (previewImageView == null) {
            return;
        }
        double zoomScale = resolveActivePreviewZoomScale();
        previewImageView.setScaleX(zoomScale);
        previewImageView.setScaleY(zoomScale);
        if (zoomScale <= UNZOOMED_SCALE + ZOOM_EPSILON) {
            previewImageView.setTranslateX(0.0);
            previewImageView.setTranslateY(0.0);
        }
    }

    /**
     * Updates preview image cursor to reflect whether the next click action
     * zooms in or zooms out.
     */
    private void refreshPreviewZoomCursor() {
        if (previewImageView == null) {
            return;
        }
        if (previewImageView.getImage() == null) {
            previewImageView.setCursor(Cursor.DEFAULT);
            return;
        }
        if (settings.isMediaHoverZoom()) {
            // previewZoomStep == 0 means hover zoom active: show zoom-out cursor
            // (hovering zooms in, click will zoom out / disable)
            // previewZoomStep == 1 means hover zoom disabled by click: show zoom-in cursor
            // (click will re-enable hover zoom)
            previewImageView.setCursor(previewZoomStep == 0 ? zoomOutCursor : zoomInCursor);
            return;
        }
        if (!settings.isMediaClickZoom()) {
            previewImageView.setCursor(Cursor.DEFAULT);
            return;
        }
        previewImageView.setCursor(previewZoomStep >= MAX_TEMPORARY_ZOOM_STEP ? zoomOutCursor : zoomInCursor);
    }

    /**
     * Resets only transform state (scale/translation) without changing the
     * click-zoom step machine.
     */
    private void resetPreviewTransformOnly() {
        if (previewImageView == null) {
            return;
        }
        previewImageView.setScaleX(UNZOOMED_SCALE);
        previewImageView.setScaleY(UNZOOMED_SCALE);
        previewImageView.setTranslateX(0.0);
        previewImageView.setTranslateY(0.0);
    }

    /**
     * Initializes zoom-in/zoom-out cursors used by preview image interactions.
     */
    private void initializeZoomCursors() {
        zoomInCursor = createZoomCursor(true);
        zoomOutCursor = createZoomCursor(false);
    }

    /**
     * Builds a simple magnifier cursor for zoom-in/zoom-out affordance.
     *
     * @param zoomIn {@code true} to draw plus sign, {@code false} for minus
     * @return custom image cursor, or fallback system cursor when generation fails
     */
    private Cursor createZoomCursor(boolean zoomIn) {
        try {
            Canvas canvas = new Canvas(24, 24);
            GraphicsContext graphics = canvas.getGraphicsContext2D();
            graphics.setStroke(Color.rgb(21, 21, 21, 0.95));
            graphics.setLineWidth(2.1);
            graphics.strokeOval(2.5, 2.5, 12.5, 12.5);
            graphics.strokeLine(13.2, 13.2, 20.5, 20.5);
            graphics.setLineWidth(1.9);
            graphics.strokeLine(5.8, 8.8, 11.8, 8.8);
            if (zoomIn) {
                graphics.strokeLine(8.8, 5.8, 8.8, 11.8);
            }
            SnapshotParameters snapshotParameters = new SnapshotParameters();
            snapshotParameters.setFill(Color.TRANSPARENT);
            WritableImage image = canvas.snapshot(snapshotParameters, new WritableImage(24, 24));
            return new ImageCursor(image, 3.0, 3.0);
        } catch (Exception ex) {
            LOGGER.debug("Could not build custom preview zoom cursor, falling back to defaults", ex);
            return zoomIn ? Cursor.HAND : Cursor.OPEN_HAND;
        }
    }

    /**
     * Clamps a ratio to [0, 1].
     *
     * @param value raw ratio
     * @return clamped ratio
     */
    private static double clamp(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }

    /**
     * Clamps a numeric value within an inclusive range.
     *
     * @param value value to clamp
     * @param min   lower bound
     * @param max   upper bound
     * @return clamped value
     */
    private static double clampToRange(double value, double min, double max) {
        return Math.clamp(value, min, max);
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
