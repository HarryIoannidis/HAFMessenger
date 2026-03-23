package com.haf.client.controllers;

import com.haf.client.utils.ImageSaveSupport;
import com.haf.client.utils.PopupMessageBuilder;
import com.haf.client.utils.PopupMessageSpec;
import com.haf.client.utils.UiConstants;
import com.haf.client.utils.WindowResizeHelper;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for image preview popup window.
 */
public class PreviewController {

    private static final Logger LOGGER = Logger.getLogger(PreviewController.class.getName());
    private static final double MAX_PREVIEW_WIDTH = 400.0;
    private static final double MAX_PREVIEW_HEIGHT = 400.0;

    @FXML
    private BorderPane rootContainer;
    @FXML
    private HBox titleBar;
    @FXML
    private JFXButton minimizeButton;
    @FXML
    private JFXButton closeButton;
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

    @FXML
    public void initialize() {
        setupWindowControls();
        if (downloadButton != null) {
            downloadButton.setOnAction(e -> handleDownloadClick());
        }
    }

    public void showImage(String imageUriOrPath, String suggestedFileName, boolean downloadAllowed) {
        this.imageUriOrPath = imageUriOrPath;
        this.suggestedFileName = suggestedFileName;
        this.downloadAllowed = downloadAllowed;

        configureDownloadButton(downloadAllowed);
        loadPreviewImage(imageUriOrPath);
    }

    private void configureDownloadButton(boolean allowed) {
        if (downloadButton == null) {
            return;
        }
        downloadButton.setVisible(true);
        downloadButton.setManaged(true);
        downloadButton.setDisable(!allowed);
    }

    private void loadPreviewImage(String source) {
        if (previewImageView == null) {
            return;
        }

        previewImageView.setImage(null);
        previewImageView.setFitWidth(0);
        previewImageView.setFitHeight(0);
        if (source == null || source.isBlank()) {
            setSpinnerVisible(false);
            return;
        }

        try {
            Image image = new Image(source, true);
            previewImageView.setImage(image);
            setSpinnerVisible(true);

            if (image.getProgress() >= 1.0) {
                setSpinnerVisible(false);
                applyImageDimensions(image);
                return;
            }

            image.progressProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal.doubleValue() >= 1.0) {
                    setSpinnerVisible(false);
                    applyImageDimensions(image);
                }
            });
            image.errorProperty().addListener((obs, wasError, isError) -> {
                if (Boolean.TRUE.equals(isError)) {
                    setSpinnerVisible(false);
                }
            });
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to load preview image", ex);
            setSpinnerVisible(false);
            showAttachmentError("Could not load image preview.");
        }
    }

    private void handleDownloadClick() {
        if (!downloadAllowed) {
            return;
        }

        Path sourcePath = ImageSaveSupport.resolveLocalSourcePath(imageUriOrPath);
        if (sourcePath == null || !Files.exists(sourcePath)) {
            LOGGER.warning("Image source path is unavailable for download.");
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
            LOGGER.log(Level.WARNING, "Failed to save image preview download", ex);
            showAttachmentError("Could not save image. Please try again.");
        }
    }

    private void setupWindowControls() {
        Platform.runLater(() -> {
            Stage stage = resolveStage();
            if (stage == null) {
                return;
            }
            stage.setResizable(true);
            WindowResizeHelper.enableResizing(stage);
            stage.setMinWidth(280);
            stage.setMinHeight(220);

            if (minimizeButton != null) {
                minimizeButton.setOnAction(e -> stage.setIconified(true));
            }
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
        });
    }

    private Stage resolveStage() {
        if (rootContainer == null || rootContainer.getScene() == null || rootContainer.getScene().getWindow() == null) {
            return null;
        }
        return (Stage) rootContainer.getScene().getWindow();
    }

    private void applyImageDimensions(Image image) {
        if (image == null || image.isError()) {
            return;
        }
        double width = image.getWidth();
        double height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        double scale = Math.min(1.0, Math.min(MAX_PREVIEW_WIDTH / width, MAX_PREVIEW_HEIGHT / height));
        previewImageView.setFitWidth(width * scale);
        previewImageView.setFitHeight(height * scale);
        Stage stage = resolveStage();
        if (stage != null) {
            stage.sizeToScene();
        }
    }

    private void setSpinnerVisible(boolean visible) {
        if (loadingSpinner == null) {
            return;
        }
        loadingSpinner.setVisible(visible);
        loadingSpinner.setManaged(visible);
    }

    private void showAttachmentError(String message) {
        PopupMessageSpec spec = buildAttachmentErrorSpec(message);
        PopupMessageBuilder.create()
                .popupKey(spec.popupKey())
                .title(spec.title())
                .message(spec.message())
                .actionText(spec.actionText())
                .singleAction(true)
                .show();
    }

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
                null,
                null);
    }
}
