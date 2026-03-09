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

    // Cached views for performance
    private Parent cachedSearchView;
    private Parent cachedPlaceholderView;
    private Parent currentChatView;
    private String currentChatRecipientId;

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

        updateNavStyles(true);
        hideSearchPanel();

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

        updateNavStyles(false);

        // Hide profile panel when in search mode
        hideProfilePanel();
        showSearchPanel();

        // Load search FXML into contentPane
        loadSearchView();
    }

    private void updateNavStyles(boolean messagesActive) {
        if (messagesActive) {
            iconMessages.getStyleClass().remove("nav-item-icon");
            if (!iconMessages.getStyleClass().contains("nav-item-icon-active")) {
                iconMessages.getStyleClass().add("nav-item-icon-active");
            }
            iconSearch.getStyleClass().remove("nav-item-icon-active");
            if (!iconSearch.getStyleClass().contains("nav-item-icon")) {
                iconSearch.getStyleClass().add("nav-item-icon");
            }
        } else {
            iconSearch.getStyleClass().remove("nav-item-icon");
            if (!iconSearch.getStyleClass().contains("nav-item-icon-active")) {
                iconSearch.getStyleClass().add("nav-item-icon-active");
            }
            iconMessages.getStyleClass().remove("nav-item-icon-active");
            if (!iconMessages.getStyleClass().contains("nav-item-icon")) {
                iconMessages.getStyleClass().add("nav-item-icon");
            }
        }
    }

    /**
     * Loads search.fxml into the content pane and stores the SearchController ref.
     */
    private void loadSearchView() {
        if (cachedSearchView == null) {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource(UiConstants.FXML_SEARCH));
                cachedSearchView = loader.load();
                searchController = loader.getController();
                searchController.setMainController(this);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not load search FXML", e);
                return;
            }
        }
        contentPane.getChildren().setAll(cachedSearchView);
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
        hideSearchPanel();
    }

    private void hideProfilePanel() {
        profilePanel.setVisible(false);
        profilePanel.setManaged(false);
    }

    private void showSearchPanel() {
        searchPanel.setVisible(true);
        searchPanel.setManaged(true);
        hideProfilePanel();
    }

    private void hideSearchPanel() {
        searchPanel.setVisible(false);
        searchPanel.setManaged(false);
    }

    private void setupContactList() {
        contactsList.setCellFactory(lv -> {
            ContactCell cell = new ContactCell();
            cell.setOnClick(this::activateMessagesTab);
            return cell;
        });
    }

    /**
     * Adds a searched user to the contacts list and switches to their chat.
     */
    public void startChatWith(String userId, String fullName) {
        // Switch to messages tab
        activateMessagesTab();

        // Find if contact already exists
        ContactInfo target = null;
        for (ContactInfo info : contactsList.getItems()) {
            if (info.name().equals(fullName)) { // Using name as identifier for now
                target = info;
                break;
            }
        }

        // Add if not exists
        if (target == null) {
            target = ContactInfo.online(fullName);
            contactsList.getItems().add(target);
        }

        // Select and load chat
        contactsList.getSelectionModel().select(target);
        contactsList.setUserData(target);
        showProfilePanel(target);
        loadChat(userId);
    }

    public boolean hasContact(String fullName) {
        for (ContactInfo info : contactsList.getItems()) {
            if (info.name().equals(fullName)) {
                return true;
            }
        }
        return false;
    }

    public void addContact(String fullName) {
        if (!hasContact(fullName)) {
            contactsList.getItems().add(ContactInfo.online(fullName));
        }
    }

    public void removeContact(String fullName) {
        contactsList.getItems().removeIf(info -> info.name().equals(fullName));
        ContactInfo selected = contactsList.getSelectionModel().getSelectedItem();
        if (selected != null && selected.name().equals(fullName)) {
            contactsList.getSelectionModel().clearSelection();
            contactsList.setUserData(null);
            hideProfilePanel();
            loadPlaceholder();
        }
    }

    private void setupContactSelection() {
        // Click on an already-selected item → deselect and revert to placeholder
        contactsList.setOnMouseClicked(event -> {
            ContactInfo clicked = contactsList.getSelectionModel().getSelectedItem();

            if (clicked == null) {
                return;
            }

            // If we are currently in Search Mode, clicking any contact (even the current
            // one)
            // should transport us back to the Messages tab.
            if (indicatorSearch.isVisible()) {
                activateMessagesTab();
                contactsList.setUserData(clicked);
                return;
            }

            // If we are already in Messages Mode, toggle selection (deselect if clicked
            // again)
            Object lastSelected = contactsList.getUserData();
            if (clicked.equals(lastSelected)) {
                contactsList.getSelectionModel().clearSelection();
                contactsList.setUserData(null);
                hideProfilePanel();
                loadPlaceholder();
            } else {
                contactsList.setUserData(clicked);
            }
        });

        contactsList.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldContact, newContact) -> {
                    if (newContact != null) {
                        activateMessagesTab();
                    }
                });
    }

    private void loadPlaceholder() {
        if (cachedPlaceholderView == null) {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource(UiConstants.FXML_PLACEHOLDER));
                cachedPlaceholderView = loader.load();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not load placeholder FXML", e);
                return;
            }
        }
        contentPane.getChildren().setAll(cachedPlaceholderView);
    }

    private void loadChat(String recipientId) {
        // Reuse chat view if we are already chatting with the same contact
        if (currentChatView != null && recipientId.equals(currentChatRecipientId)) {
            contentPane.getChildren().setAll(currentChatView);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(UiConstants.FXML_CHAT));
            currentChatView = loader.load();

            ChatController chatController = loader.getController();
            chatController.setRecipient(recipientId);

            currentChatRecipientId = recipientId;
            contentPane.getChildren().setAll(currentChatView);
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
