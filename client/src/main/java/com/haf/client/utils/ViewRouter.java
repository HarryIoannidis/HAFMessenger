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
import javafx.stage.Window;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewRouter {

    /**
     * Utility class.
     */
    private ViewRouter() {
    }

    private static final Logger logger = LoggerFactory.getLogger(ViewRouter.class);

    static {
        try {
            Font.loadFont(ViewRouter.class.getResourceAsStream(UiConstants.FONT_MANROPE),
                    UiConstants.FONT_SIZE_REGULAR);
            Font.loadFont(ViewRouter.class.getResourceAsStream(UiConstants.FONT_MANROPE_BOLD),
                    UiConstants.FONT_SIZE_BOLD);
        } catch (Exception e) {
            logger.warn( "Could not load fonts", e);
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
            logger.info( "Switching to FXML: {}", resource);
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
            logger.info( "Switching to transparent FXML: {}", resource);
            FXMLLoader loader = new FXMLLoader(resource);
            Parent root = loader.load();
            boolean isSplash = fxmlPath.equals(UiConstants.FXML_SPLASH);

            Parent sceneRoot = root;
            if (isSplash) {
                StackPane splashShell = new StackPane(root);
                splashShell.setPadding(new Insets(14));
                splashShell.setPickOnBounds(false);
                splashShell.setStyle("-fx-background-color: transparent;");
                sceneRoot = splashShell;
            }

            Scene scene = new Scene(sceneRoot);
            scene.setFill(Color.TRANSPARENT);
            if (isSplash) {
                scene.getRoot().setStyle("-fx-font-smoothing-type: lcd; -fx-background-color: transparent;");
            } else {
                // Keep custom transparent stage chrome for non-splash views, but let the
                // view's own CSS control panel/window backgrounds.
                scene.getRoot().setStyle("-fx-font-smoothing-type: lcd;");
            }

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
                // Splash: size to scene so shell padding leaves room for card shadow.
                mainStage.sizeToScene();
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

    /**
     * Shows (or reuses) a keyed popup window and optionally configures its
     * controller before display.
     *
     * @param popupKey            unique popup key used to cache/reuse stage
     *                            instances
     * @param fxmlPath            popup FXML path to load when the popup is first
     *                            created
     * @param controllerType      expected controller type for the loaded popup
     * @param configureController optional callback to configure the popup
     *                            controller before showing
     * @param <T>                 concrete popup controller type
     * @throws IllegalStateException if the main stage is not initialized
     */
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

        PopupEntry entry = getOrCreatePopupEntry(popupKey, fxmlPath, controllerType);

        T controller = controllerType.cast(entry.controller());
        if (configureController != null) {
            configureController.accept(controller);
        }

        Stage popupStage = entry.stage();
        popupStage.sizeToScene();
        centerPopupOverMainWindow(popupStage);

        if (!popupStage.isShowing()) {
            popupStage.show();
        }
        popupStage.toFront();
        popupStage.requestFocus();
    }

    /**
     * Preloads and caches a popup window so the first visible open is instant.
     *
     * @param popupKey       unique popup key used to cache/reuse stage instances
     * @param fxmlPath       popup FXML path to load if missing from cache
     * @param controllerType expected controller type for the loaded popup
     * @param <T>            concrete popup controller type
     */
    public static <T> void preloadPopup(
            String popupKey,
            String fxmlPath,
            Class<T> controllerType) {
        preloadPopup(popupKey, fxmlPath, controllerType, null);
    }

    /**
     * Preloads and caches a popup window and optionally configures its
     * controller after load.
     *
     * @param popupKey            unique popup key used to cache/reuse stage
     *                            instances
     * @param fxmlPath            popup FXML path to load if missing from cache
     * @param controllerType      expected controller type for the loaded popup
     * @param configureController optional callback to configure the controller
     * @param <T>                 concrete popup controller type
     */
    public static <T> void preloadPopup(
            String popupKey,
            String fxmlPath,
            Class<T> controllerType,
            Consumer<T> configureController) {
        Objects.requireNonNull(popupKey, "popupKey");
        Objects.requireNonNull(fxmlPath, "fxmlPath");
        Objects.requireNonNull(controllerType, "controllerType");

        if (mainStage == null) {
            logger.warn("Skipping popup preload because main stage is not initialized.");
            return;
        }

        PopupEntry entry = getOrCreatePopupEntry(popupKey, fxmlPath, controllerType);
        if (configureController != null) {
            T controller = controllerType.cast(entry.controller());
            configureController.accept(controller);
        }
        warmPopupEntry(entry);
    }

    /**
     * Closes the main application stage.
     */
    public static void close() {
        closeAllPopups();
        mainStage.close();
    }

    /**
     * Loads a popup FXML and creates a transparent popup stage bound to the main
     * stage.
     *
     * @param fxmlPath       popup FXML resource path
     * @param controllerType expected controller class
     * @param <T>            concrete controller type
     * @return popup stage + controller wrapper used by popup cache
     * @throws UncheckedIOException  when the FXML cannot be loaded
     * @throws IllegalStateException when loaded controller type does not match
     *                               {@code controllerType}
     */
    private static <T> PopupEntry loadPopupEntry(String fxmlPath, Class<T> controllerType) {
        try {
            var resource = ViewRouter.class.getResource(fxmlPath);
            logger.info( "Loading popup FXML: {}", resource);
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
            popupShell.setMinSize(UiConstants.POPUP_MIN_WIDTH, UiConstants.POPUP_MIN_HEIGHT);
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

    /**
     * Returns an existing popup cache entry or creates one when absent/invalid.
     *
     * @param popupKey       unique popup key
     * @param fxmlPath       popup FXML path
     * @param controllerType expected controller type
     * @param <T>            concrete controller type
     * @return cached or newly created popup entry
     */
    private static synchronized <T> PopupEntry getOrCreatePopupEntry(
            String popupKey,
            String fxmlPath,
            Class<T> controllerType) {
        PopupEntry entry = popupEntries.get(popupKey);
        if (entry == null || entry.stage() == null || entry.stage().getOwner() != mainStage) {
            entry = loadPopupEntry(fxmlPath, controllerType);
            popupEntries.put(popupKey, entry);
        }
        return entry;
    }

    /**
     * Prepares popup scene CSS/layout/render pipeline ahead of first visible show.
     *
     * @param entry cached popup entry to warm
     */
    private static void warmPopupEntry(PopupEntry entry) {
        if (entry == null || entry.stage() == null || entry.stage().getScene() == null
                || entry.stage().getScene().getRoot() == null) {
            return;
        }

        Stage popupStage = entry.stage();
        Parent root = popupStage.getScene().getRoot();
        popupStage.sizeToScene();
        root.applyCss();
        root.layout();
        try {
            root.snapshot(null, null);
        } catch (RuntimeException ex) {
            logger.trace( "Popup warmup snapshot skipped: {}", ex.getMessage());
        }

        if (!popupStage.isShowing()) {
            double previousOpacity = popupStage.getOpacity();
            try {
                popupStage.setOpacity(0.0);
                centerPopupOverMainWindow(popupStage);
                popupStage.show();
                popupStage.hide();
            } catch (RuntimeException ex) {
                logger.trace( "Popup invisible show/hide warmup skipped: {}", ex.getMessage());
            } finally {
                popupStage.setOpacity(previousOpacity);
                if (mainStage != null) {
                    mainStage.requestFocus();
                }
            }
        }
    }

    /**
     * Centers a popup stage over the main application stage.
     *
     * @param popupStage popup stage to reposition
     */
    private static void centerPopupOverMainWindow(Stage popupStage) {
        Stage anchorStage = resolvePopupAnchorStage(popupStage);
        if (anchorStage == null) {
            return;
        }

        double anchorX = anchorStage.getX();
        double anchorY = anchorStage.getY();
        double anchorWidth = anchorStage.getWidth();
        double anchorHeight = anchorStage.getHeight();

        Scene anchorScene = anchorStage.getScene();
        if (anchorScene != null && anchorScene.getWidth() > 0 && anchorScene.getHeight() > 0) {
            // Center against the visible scene area. This avoids root-bound
            // conversions returning shifted values on transparent/custom windows.
            anchorX = anchorStage.getX() + anchorScene.getX();
            anchorY = anchorStage.getY() + anchorScene.getY();
            anchorWidth = anchorScene.getWidth();
            anchorHeight = anchorScene.getHeight();
        }

        double popupWidth = popupStage.getWidth();
        double popupHeight = popupStage.getHeight();

        if (popupWidth <= 0 || popupHeight <= 0) {
            popupStage.sizeToScene();
            popupWidth = popupStage.getWidth();
            popupHeight = popupStage.getHeight();
        }

        double targetX = anchorX + ((anchorWidth - popupWidth) / 2.0);
        double targetY = anchorY + ((anchorHeight - popupHeight) / 2.0);
        popupStage.setX(targetX);
        popupStage.setY(targetY);
    }

    /**
     * Resolves the stage used to anchor popup centering.
     * Preference order: showing main stage -> focused showing stage -> first
     * showing stage.
     *
     * @return stage used as centering anchor, or {@code null} when unavailable
     */
    private static Stage resolvePopupAnchorStage(Stage excludedStage) {
        if (mainStage != null && mainStage != excludedStage && mainStage.isShowing()) {
            return mainStage;
        }

        for (Window window : Window.getWindows()) {
            if (window instanceof Stage stage
                    && stage != excludedStage
                    && stage.isShowing()
                    && stage.isFocused()) {
                return stage;
            }
        }

        for (Window window : Window.getWindows()) {
            if (window instanceof Stage stage && stage != excludedStage && stage.isShowing()) {
                return stage;
            }
        }

        return null;
    }

    /**
     * Closes all tracked popup stages and clears the popup cache.
     */
    private static void closeAllPopups() {
        for (PopupEntry entry : popupEntries.values()) {
            Stage stage = entry.stage();
            if (stage != null) {
                stage.close();
            }
        }
        popupEntries.clear();
    }

    /**
     * Checks if the main stage is currently showing a transparent window.
     * This is used to determine if a full recreate-and-resize is needed.
     */
    private record PopupEntry(Stage stage, Object controller) {
        // no-op
    }
}
