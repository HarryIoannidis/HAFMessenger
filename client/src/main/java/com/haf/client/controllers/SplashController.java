package com.haf.client.controllers;

import com.haf.client.utils.ViewRouter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

public class SplashController {

    @FXML private StackPane rootContainer;
    @FXML private ImageView logo;
    @FXML private Label title;
    @FXML private Label subtitle;
    @FXML private Label status;
    @FXML private Label percentage;
    @FXML private ProgressBar progressBar;
    @FXML private Label version;
    /**
     * Initializes the controller class.
     * This method is automatically called after the FXML file has been loaded.
     */
    @FXML public void initialize() {
        version.setText("1.0.0");

        applyProgressBarClip();

        new Thread(this::simulateLoading).start();
    }

    private void applyProgressBarClip() {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(progressBar.widthProperty());
        clip.heightProperty().bind(progressBar.heightProperty());

        clip.setArcWidth(24);
        clip.setArcHeight(24);
        progressBar.setClip(clip);
    }

    private void simulateLoading() {
        try {
            Thread.sleep(2000);

            // Phase 1
            updateUI("Initializing Core...", 0.1);
            Thread.sleep(600);

            // Phase 2
            updateUI("Loading Security Modules...", 0.4);
            Thread.sleep(800);

            // Phase 3
            updateUI("Connecting to database...", 0.7);
            Thread.sleep(800);

            // Phase 4
            updateUI("Ready", 1.0);
            Thread.sleep(400);

//            Platform.runLater(() -> {
//                 Ensure you have login.fxml created, or this will crash!
//                 ViewRouter.switchToTransparent("/fxml/login.fxml");
//            });

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void updateUI(String statusText, double progress) {
        Platform.runLater(() -> {
            status.setText(statusText);
            progressBar.setProgress(progress);
            percentage.setText((int)(progress * 100) + "%");
        });
    }
}

