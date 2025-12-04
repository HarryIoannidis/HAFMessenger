package com.haf.client.core;

import com.haf.client.utils.ViewRouter;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

public class ClientApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // 1. Hand over the Stage (Window) to our Router utility
        ViewRouter.setMainStage(primaryStage);

        // 2. Set the Application Icon (Taskbar)
        try {
            primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/icon.png"))));
        } catch (Exception e) {
            System.err.println("Warning: App icon not found.");
        }

        // 3. Set the Title
        primaryStage.setTitle("HAF Messenger");

        // 4. Launch the First Screen
        ViewRouter.switchToTransparent("/fxml/splash.fxml");
    }

    /**
     * The main entry point for the JavaFX application.
     * It is a fallback for IDEs.
     * @param args
     */
    public static void main(String[] args) {
        launch(args);
    }
}
