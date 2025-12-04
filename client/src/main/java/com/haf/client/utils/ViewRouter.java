package com.haf.client.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class ViewRouter {

    private static Stage mainStage;

    /**
     * Sets the main application stage.
     * @param stage the main application stage
     */
    public static void setMainStage(Stage stage) {
        mainStage = stage;
    }

    /**
     * Switches the main stage to the specified FXML view.
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
                mainStage.setScene(new Scene(root));
            }
            mainStage.centerOnScreen();
            mainStage.show();

        } catch (IOException e) {
            throw new RuntimeException("Failed to load FXML: " + fxmlPath, e);
        }
    }

    /**
     * Switches the main stage to a transparent style with the specified FXML view.
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

            mainStage.setScene(scene);
            mainStage.centerOnScreen();
            mainStage.show();

        } catch (IOException e) {
            throw new RuntimeException("Failed to load FXML: " + fxmlPath, e);
        }
    }

    /**
     * Recreates the main stage with the specified style.
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
     * Closes the main application stage.
     */
    public static void close() {
        mainStage.close();
    }
}
