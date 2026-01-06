package com.haf.client.core;

import com.haf.client.utils.ViewRouter;
import com.haf.client.utils.UiConstants;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(ClientApp.class.getName());

    /**
     * Starts the JavaFX application.
     * @param primaryStage the primary stage for this application, onto which the application scene can be set.
     * Applications may create other stages, if needed, but they will not be primary stages.
     */
    @Override
    public void start(Stage primaryStage) {
        // 1. Hand over the Stage (Window) to our Router utility
        ViewRouter.setMainStage(primaryStage);

        // 2. Set the Application Icon (Taskbar)
        try {
            primaryStage.getIcons()
                    .add(new Image(Objects.requireNonNull(getClass().getResourceAsStream(UiConstants.IMAGE_APP_LOGO))));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Warning: App icon not found.", e);
        }

        // 3. Set the Title
        primaryStage.setTitle(UiConstants.APP_TITLE);

        // 4. Launch the First Screen
        ViewRouter.switchToTransparent(UiConstants.FXML_SPLASH);
    }

    /**
     * The main entry point for the JavaFX application.
     * It is a fallback for IDEs.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        System.setProperty("prism.lcdtext", "true"); // επιβάλλει LCD smoothing
        System.setProperty("prism.text", "t2k");
        launch(args);
    }
}
