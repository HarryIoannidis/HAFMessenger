package com.haf.client.controllers;

import com.haf.client.model.ContactInfo;
import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.jfoenix.controls.JFXButton;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
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

    // Search field and action button
    @FXML
    private TextField toolbarSearchField;
    @FXML
    private JFXButton searchActionButton;
    @FXML
    private FontIcon searchActionIcon;

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

    /** Reference to the loaded SearchController (null when not in search mode). */
    private SearchController searchController;

    /** Tracks whether results are currently displayed (for clear/search toggle). */
    private boolean hasSearchResults;

    @FXML
    public void initialize() {
        setupWindowControls();
        setupNavBar();
        setupContactList();
        loadPlaceholder();
        setupContactSelection();
        setupSearchField();

        // Messages tab is active by default
        activateMessagesTab();
    }

    private void setupNavBar() {
        navMessages.setOnAction(e -> activateMessagesTab());
        navSearch.setOnAction(e -> activateSearchTab());
    }

    /**
     * Wires Enter key on the search field and the action button (magnify / clear).
     */
    private void setupSearchField() {
        // Enter key triggers search
        toolbarSearchField.setOnAction(e -> performSearch());

        // Action button: search or clear depending on state
        if (searchActionButton != null) {
            searchActionButton.setOnAction(e -> {
                if (hasSearchResults) {
                    clearSearch();
                } else {
                    performSearch();
                }
            });
        }
    }

    /**
     * Performs the search using the current text in the toolbar field.
     */
    private void performSearch() {
        if (searchController == null) {
            return;
        }
        String query = toolbarSearchField.getText();
        if (query != null && !query.isBlank()) {
            searchController.search(query);
            hasSearchResults = true;
            // Switch icon to clear (X)
            if (searchActionIcon != null) {
                searchActionIcon.setIconLiteral("mdi2c-close");
            }
        }
    }

    /**
     * Clears the search results and resets the field and icon.
     */
    private void clearSearch() {
        toolbarSearchField.clear();
        if (searchController != null) {
            searchController.clearResults();
        }
        hasSearchResults = false;
        // Restore magnify icon
        if (searchActionIcon != null) {
            searchActionIcon.setIconLiteral("mdi2m-magnify");
        }
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

        searchPanel.setVisible(false);
        searchPanel.setManaged(false);

        // Discard the search controller
        searchController = null;
        hasSearchResults = false;
        if (searchActionIcon != null) {
            searchActionIcon.setIconLiteral("mdi2m-magnify");
        }

        // Restore profile panel if a contact is still selected
        ContactInfo selected = contactsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showProfilePanel(selected);
            loadChat(selected.name());
        } else {
            loadPlaceholder();
        }
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

        // Load search FXML into contentPane
        loadSearchView();
    }

    /**
     * Loads search.fxml into the content pane and stores the SearchController ref.
     */
    private void loadSearchView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(UiConstants.FXML_SEARCH));
            Parent searchView = loader.load();
            searchController = loader.getController();
            contentPane.getChildren().setAll(searchView);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load search FXML", e);
        }
    }

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
