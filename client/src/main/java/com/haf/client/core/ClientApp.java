package com.haf.client.core;

import com.haf.client.utils.ViewRouter;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(ClientApp.class.getName());

    @Override
    public void start(Stage primaryStage) {
        // 1. Hand over the Stage (Window) to our Router utility
        ViewRouter.setMainStage(primaryStage);

        // 2. Set the Application Icon (Taskbar)
        try {
            primaryStage.getIcons()
                    .add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/app_logo.png"))));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Warning: App icon not found.", e);
        }

        // 3. Set the Title
        primaryStage.setTitle("HAF Messenger");

        // 4. Launch the First Screen
        ViewRouter.switchToTransparent("/fxml/splash.fxml");
    }

    /**
     * The main entry point for the JavaFX application.
     * It is a fallback for IDEs.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
