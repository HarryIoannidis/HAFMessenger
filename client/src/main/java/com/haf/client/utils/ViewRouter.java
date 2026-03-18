package com.haf.client.utils;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ViewRouter {

    /**
     * Utility class.
     */
    private ViewRouter() {
    }

    private static final Logger logger = Logger.getLogger(ViewRouter.class.getName());

    static {
        try {
            Font.loadFont(ViewRouter.class.getResourceAsStream(UiConstants.FONT_MANROPE),
                    UiConstants.FONT_SIZE_REGULAR);
            Font.loadFont(ViewRouter.class.getResourceAsStream(UiConstants.FONT_MANROPE_BOLD),
                    UiConstants.FONT_SIZE_BOLD);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not load fonts", e);
        }
    }

    private static Stage mainStage;
    private static final Map<String, PopupEntry> popupEntries = new ConcurrentHashMap<>();

    /**
     * Sets the main application stage.
     * 
     * @param stage the main application stage
     */
    public static void setMainStage(Stage stage) {
        mainStage = stage;
    }

    /**
     * Switches the main stage to the specified FXML view.
     * 
     * @param fxmlPath the path to the FXML file
     */
    public static void switchTo(String fxmlPath) {
        try {
            closeAllPopups();
            if (mainStage.getStyle() == StageStyle.TRANSPARENT) {
                recreateStage(StageStyle.DECORATED);
            }

            var resource = ViewRouter.class.getResource(fxmlPath);
            logger.log(Level.INFO, "Switching to FXML: {0}", resource);
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();

            if (mainStage.getScene() != null) {
                mainStage.getScene().setRoot(root);
            } else {
                Scene scene = new Scene(root);
                mainStage.setScene(scene);
            }
            mainStage.centerOnScreen();
            mainStage.show();

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load FXML: " + fxmlPath, e);
        }
    }

    /**
     * Switches the main stage to a transparent style with the specified FXML view.
     * 
     * @param fxmlPath the path to the FXML file
     */
    public static void switchToTransparent(String fxmlPath) {
        try {
            closeAllPopups();
            if (mainStage.getStyle() != StageStyle.TRANSPARENT) {
                recreateStage(StageStyle.TRANSPARENT);
            }

            var resource = ViewRouter.class.getResource(fxmlPath);
            logger.log(Level.INFO, "Switching to transparent FXML: {0}", resource);
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            scene.getRoot().setStyle("-fx-font-smoothing-type: lcd;");

            boolean isSplash = fxmlPath.equals(UiConstants.FXML_SPLASH);

            // Hide before changing a transparent stage's size on Linux to prevent rendering
            // artifacts (black borders)
            // We only need this workaround when transitioning from the small Splash screen
            // to a larger screen (Login/Register)
            boolean wasShowing = mainStage.isShowing();
            if (wasShowing && !isSplash && mainStage.getWidth() < 1200) {
                mainStage.hide();
            }

            mainStage.setResizable(true);

            mainStage.setScene(scene);

            if (isSplash) {
                // Splash: use its own FXML dimensions, no resizing
                if (root instanceof javafx.scene.layout.Region region) {
                    mainStage.setWidth(region.getPrefWidth());
                    mainStage.setHeight(region.getPrefHeight());
                }
                mainStage.setResizable(false);
                mainStage.centerOnScreen();
            } else {
                // Open the new window at 1200x850 if the current window is smaller,
                // otherwise keep current dimensions
                if (Double.isNaN(mainStage.getWidth()) || mainStage.getWidth() < 1200 || mainStage.getHeight() < 850) {
                    mainStage.setWidth(1200);
                    mainStage.setHeight(850);
                    mainStage.centerOnScreen();
                }
                WindowResizeHelper.enableResizing(mainStage);
            }
            mainStage.show();

        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load FXML: " + fxmlPath, e);
        }
    }

    /**
     * Recreates the main stage with the specified style.
     * 
     * @param style the stage style
     */
    private static void recreateStage(StageStyle style) {
        closeAllPopups();
        mainStage.close();
        Stage newStage = new Stage();
        newStage.initStyle(style);
        if (!mainStage.getIcons().isEmpty()) {
            newStage.getIcons().add(mainStage.getIcons().getFirst());
        }
        newStage.setTitle(mainStage.getTitle());
        mainStage = newStage;
    }

    /**
     * Returns the main application stage.
     *
     * @return the main Stage
     */
    public static Stage getMainStage() {
        return mainStage;
    }

    public static <T> void showPopup(
            String popupKey,
            String fxmlPath,
            Class<T> controllerType,
            Consumer<T> configureController) {
        Objects.requireNonNull(popupKey, "popupKey");
        Objects.requireNonNull(fxmlPath, "fxmlPath");
        Objects.requireNonNull(controllerType, "controllerType");

        if (mainStage == null) {
            throw new IllegalStateException("Main stage is not initialized.");
        }

        PopupEntry entry = popupEntries.get(popupKey);
        if (entry == null || entry.stage() == null || entry.stage().getOwner() != mainStage) {
            entry = loadPopupEntry(fxmlPath, controllerType);
            popupEntries.put(popupKey, entry);
        }

        T controller = controllerType.cast(entry.controller());
        if (configureController != null) {
            configureController.accept(controller);
        }

        Stage popupStage = entry.stage();
        popupStage.sizeToScene();
        centerPopupOverMainStage(popupStage);

        if (!popupStage.isShowing()) {
            popupStage.show();
        }
        popupStage.toFront();
        popupStage.requestFocus();
    }

    /**
     * Closes the main application stage.
     */
    public static void close() {
        closeAllPopups();
        mainStage.close();
    }

    private static <T> PopupEntry loadPopupEntry(String fxmlPath, Class<T> controllerType) {
        try {
            var resource = ViewRouter.class.getResource(fxmlPath);
            logger.log(Level.INFO, "Loading popup FXML: {0}", resource);
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            Object controller = loader.getController();
            if (!controllerType.isInstance(controller)) {
                throw new IllegalStateException("Popup controller type mismatch for " + fxmlPath);
            }

            Stage popupStage = new Stage();
            popupStage.initOwner(mainStage);
            popupStage.initStyle(StageStyle.TRANSPARENT);
            popupStage.initModality(Modality.NONE);
            popupStage.setResizable(false);

            StackPane popupShell = new StackPane(root);
            popupShell.setPadding(new Insets(14));
            popupShell.setPickOnBounds(false);
            popupShell.setStyle("-fx-background-color: transparent;");

            Scene popupScene = new Scene(popupShell);
            popupScene.setFill(Color.TRANSPARENT);
            popupScene.getRoot().setStyle("-fx-font-smoothing-type: lcd; -fx-background-color: transparent;");
            popupStage.setScene(popupScene);
            popupStage.setTitle(mainStage.getTitle());

            return new PopupEntry(popupStage, controller);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load popup FXML: " + fxmlPath, e);
        }
    }

    private static void centerPopupOverMainStage(Stage popupStage) {
        double popupWidth = popupStage.getWidth();
        double popupHeight = popupStage.getHeight();

        if (popupWidth <= 0 || popupHeight <= 0) {
            popupStage.sizeToScene();
            popupWidth = popupStage.getWidth();
            popupHeight = popupStage.getHeight();
        }

        double targetX = mainStage.getX() + ((mainStage.getWidth() - popupWidth) / 2.0);
        double targetY = mainStage.getY() + ((mainStage.getHeight() - popupHeight) / 2.0);
        popupStage.setX(targetX);
        popupStage.setY(targetY);
    }

    private static void closeAllPopups() {
        for (PopupEntry entry : popupEntries.values()) {
            Stage stage = entry.stage();
            if (stage != null) {
                stage.close();
            }
        }
        popupEntries.clear();
    }

    private record PopupEntry(Stage stage, Object controller) {
    }
}
