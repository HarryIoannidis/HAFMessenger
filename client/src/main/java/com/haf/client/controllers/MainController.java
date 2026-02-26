package com.haf.client.controllers;

import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.jfoenix.controls.JFXButton;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Main application view ({@code main.fxml}).
 */
public class MainController {

    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());

    // Window chrome
    @FXML
    private BorderPane rootContainer;
    @FXML
    private HBox titleBar;
    @FXML
    private JFXButton minimizeButton;
    @FXML
    private JFXButton maximizeButton;
    @FXML
    private JFXButton closeButton;

    // Nav buttons and their indicators/icons
    @FXML
    private JFXButton navMessages;
    @FXML
    private JFXButton navSearch;
    @FXML
    private Pane indicatorMessages;
    @FXML
    private Pane indicatorSearch;
    @FXML
    private FontIcon iconMessages;
    @FXML
    private FontIcon iconSearch;

    // Toolbar panels
    @FXML
    private HBox profilePanel;
    @FXML
    private HBox searchPanel;

    // Content area
    @FXML
    private StackPane contentPane;
    @FXML
    private ListView<String> contactsList;

    private double xOffset;
    private double yOffset;

    @FXML
    public void initialize() {
        setupWindowControls();
        setupNavBar();
        setupContactList();
        loadPlaceholder();
        setupContactSelection();

        // Messages tab is active by default
        activateMessagesTab();
    }

    private void setupNavBar() {
        navMessages.setOnAction(e -> activateMessagesTab());
        navSearch.setOnAction(e -> activateSearchTab());
    }

    private void activateMessagesTab() {
        // Indicators
        indicatorMessages.setVisible(true);
        indicatorSearch.setVisible(false);

        // Icons
        iconMessages.getStyleClass().remove("nav-item-icon");
        if (!iconMessages.getStyleClass().contains("nav-item-icon-active")) {
            iconMessages.getStyleClass().add("nav-item-icon-active");
        }
        iconSearch.getStyleClass().remove("nav-item-icon-active");
        if (!iconSearch.getStyleClass().contains("nav-item-icon")) {
            iconSearch.getStyleClass().add("nav-item-icon");
        }

        // Toolbar panels
        profilePanel.setVisible(true);
        profilePanel.setManaged(true);
        searchPanel.setVisible(false);
        searchPanel.setManaged(false);
    }

    private void activateSearchTab() {
        // Indicators
        indicatorMessages.setVisible(false);
        indicatorSearch.setVisible(true);

        // Icons
        iconSearch.getStyleClass().remove("nav-item-icon");
        if (!iconSearch.getStyleClass().contains("nav-item-icon-active")) {
            iconSearch.getStyleClass().add("nav-item-icon-active");
        }
        iconMessages.getStyleClass().remove("nav-item-icon-active");
        if (!iconMessages.getStyleClass().contains("nav-item-icon")) {
            iconMessages.getStyleClass().add("nav-item-icon");
        }

        // Toolbar panels
        profilePanel.setVisible(false);
        profilePanel.setManaged(false);
        searchPanel.setVisible(true);
        searchPanel.setManaged(true);
    }

    private void setupContactList() {
        contactsList.setCellFactory(lv -> new ContactCell());

        // Add a demo contact so chat can be tested
        contactsList.getItems().add("Demo Contact");
    }

    private void setupContactSelection() {
        contactsList.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldContact, newContact) -> {
                    if (newContact != null) {
                        loadChat(newContact);
                    }
                });
    }

    private void loadPlaceholder() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(UiConstants.FXML_PLACEHOLDER));
            Parent placeholder = loader.load();
            contentPane.getChildren().setAll(placeholder);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not load placeholder FXML", e);
        }
    }

    private void loadChat(String recipientId) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(UiConstants.FXML_CHAT));
            Parent chatView = loader.load();

            ChatController chatController = loader.getController();
            chatController.setRecipient(recipientId);

            contentPane.getChildren().setAll(chatView);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load chat FXML", e);
        }
    }

    private void setupWindowControls() {
        Stage stage = ViewRouter.getMainStage();

        if (minimizeButton != null) {
            minimizeButton.setOnAction(e -> stage.setIconified(true));
        }
        if (maximizeButton != null) {
            maximizeButton.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
        }
        if (closeButton != null) {
            closeButton.setOnAction(e -> {
                javafx.application.Platform.exit();
                System.exit(0);
            });
        }

        if (titleBar != null) {
            titleBar.setOnMousePressed(event -> {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            });
            titleBar.setOnMouseDragged(event -> {
                if (stage.isMaximized()) {
                    stage.setMaximized(false);
                }
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            });
        }
    }
}
