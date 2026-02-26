package com.haf.client.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ViewRouter {

    /**
     * Utility class.
     */
    private ViewRouter() {
    }

    private static final Logger logger = Logger.getLogger(ViewRouter.class.getName());

    /**
     * Loads the Manrope font once at class initialization.
     */
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
            if (mainStage.getStyle() == StageStyle.TRANSPARENT) {
                recreateStage(StageStyle.DECORATED);
            }

            FXMLLoader loader = new FXMLLoader(ViewRouter.class.getResource(fxmlPath));
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
            if (mainStage.getStyle() != StageStyle.TRANSPARENT) {
                recreateStage(StageStyle.TRANSPARENT);
            }

            FXMLLoader loader = new FXMLLoader(ViewRouter.class.getResource(fxmlPath));
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
                // Open new window at 1200x850 if current window is smaller,
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
     * Closes the main application stage.
     */
    public static void close() {
        mainStage.close();
    }
}
