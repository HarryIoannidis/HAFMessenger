package com.haf.client.controllers;

import com.haf.client.model.ContactInfo;
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
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
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

    // Profile panel detail nodes
    @FXML
    private Text profileNameText;
    @FXML
    private Text profileActivenessText;
    @FXML
    private Circle profileActivenessCircle;

    // Content area
    @FXML
    private StackPane contentPane;
    @FXML
    private ListView<ContactInfo> contactsList;

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

    // -------------------------------------------------------------------------
    // Nav bar
    // -------------------------------------------------------------------------

    private void setupNavBar() {
        navMessages.setOnAction(e -> activateMessagesTab());
        navSearch.setOnAction(e -> activateSearchTab());
    }

    private void activateMessagesTab() {
        indicatorMessages.setVisible(true);
        indicatorSearch.setVisible(false);

        iconMessages.getStyleClass().remove("nav-item-icon");
        if (!iconMessages.getStyleClass().contains("nav-item-icon-active")) {
            iconMessages.getStyleClass().add("nav-item-icon-active");
        }
        iconSearch.getStyleClass().remove("nav-item-icon-active");
        if (!iconSearch.getStyleClass().contains("nav-item-icon")) {
            iconSearch.getStyleClass().add("nav-item-icon");
        }

        // Profile panel visibility is controlled by selection, not by tab
        searchPanel.setVisible(false);
        searchPanel.setManaged(false);
    }

    private void activateSearchTab() {
        indicatorMessages.setVisible(false);
        indicatorSearch.setVisible(true);

        iconSearch.getStyleClass().remove("nav-item-icon");
        if (!iconSearch.getStyleClass().contains("nav-item-icon-active")) {
            iconSearch.getStyleClass().add("nav-item-icon-active");
        }
        iconMessages.getStyleClass().remove("nav-item-icon-active");
        if (!iconMessages.getStyleClass().contains("nav-item-icon")) {
            iconMessages.getStyleClass().add("nav-item-icon");
        }

        // Hide profile panel when in search mode
        hideProfilePanel();
        searchPanel.setVisible(true);
        searchPanel.setManaged(true);
    }

    // -------------------------------------------------------------------------
    // Profile panel helpers
    // -------------------------------------------------------------------------

    private void showProfilePanel(ContactInfo contact) {
        profileNameText.setText(contact.name());
        profileActivenessText.setText(contact.activenessLabel());
        try {
            profileActivenessCircle.setFill(Color.web(contact.activenessColor()));
        } catch (IllegalArgumentException ex) {
            profileActivenessCircle.setFill(Color.GRAY);
        }
        profilePanel.setVisible(true);
        profilePanel.setManaged(true);
    }

    private void hideProfilePanel() {
        profilePanel.setVisible(false);
        profilePanel.setManaged(false);
    }

    // -------------------------------------------------------------------------
    // Contact list
    // -------------------------------------------------------------------------

    private void setupContactList() {
        contactsList.setCellFactory(lv -> new ContactCell());

        // Demo contacts so the UI can be tested immediately
        contactsList.getItems().addAll(
                ContactInfo.online("Demo Contact"),
                ContactInfo.offline("Harry Ioannidis"));
    }

    private void setupContactSelection() {
        // Click on an already-selected item → deselect and revert to placeholder
        contactsList.setOnMouseClicked(event -> {
            int clickedIndex = contactsList.getSelectionModel().getSelectedIndex();
            ContactInfo clicked = contactsList.getSelectionModel().getSelectedItem();

            // We track whether the click target is already selected via a tag on the
            // ListView
            Object lastSelected = contactsList.getUserData();
            if (clicked != null && clicked.equals(lastSelected)) {
                // Same cell clicked again → deselect
                contactsList.getSelectionModel().clearSelection();
                contactsList.setUserData(null);
                hideProfilePanel();
                loadPlaceholder();
                return;
            }
            // Record this as the last selected item
            if (clicked != null) {
                contactsList.setUserData(clicked);
            }
        });

        contactsList.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldContact, newContact) -> {
                    if (newContact != null) {
                        showProfilePanel(newContact);
                        loadChat(newContact.name());
                    }
                    // If null (cleared above), placeholder + panel hide are already handled
                });
    }

    // -------------------------------------------------------------------------
    // Content pane
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Window chrome
    // -------------------------------------------------------------------------

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
