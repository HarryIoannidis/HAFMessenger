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
    private Parent currentChatView;
    private String currentChatRecipientId;

    private final java.util.concurrent.CompletableFuture<Parent> placeholderFuture = new java.util.concurrent.CompletableFuture<>();
    private final java.util.concurrent.CompletableFuture<Parent> searchFuture = new java.util.concurrent.CompletableFuture<>();
    private final java.util.concurrent.atomic.AtomicBoolean placeholderLoadingStarted = new java.util.concurrent.atomic.AtomicBoolean(
            false);
    private final java.util.concurrent.atomic.AtomicBoolean searchLoadingStarted = new java.util.concurrent.atomic.AtomicBoolean(
            false);

    @FXML
    public void initialize() {
        setupWindowControls();
        setupNavBar();
        setupContactList();
        setupContactSelection();
        setupSearchField();

        // Trigger pre-loading immediately
        triggerPreloading();

        // Messages tab is active by default
        activateMessagesTab();
    }

    private void triggerPreloading() {
        Thread.ofVirtual().name("view-preloader").start(() -> {
            ensurePlaceholderLoaded();
            ensureSearchLoaded();
        });
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
            loadChat(selected.id());
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
     * Loads search.fxml into the content pane.
     */
    private void loadSearchView() {
        searchFuture.thenAccept(view -> javafx.application.Platform.runLater(() -> {
            contentPane.getChildren().setAll(view);
        }));

        // If not already loading, trigger it (non-blocking)
        if (!searchLoadingStarted.get()) {
            ensureSearchLoaded();
        }
    }

    private void ensureSearchLoaded() {
        if (searchLoadingStarted.getAndSet(true)) {
            return;
        }

        try {
            var resource = getClass().getResource(UiConstants.FXML_SEARCH);
            LOGGER.log(Level.INFO, "Loading search FXML: {0}", resource);
            FXMLLoader loader = new FXMLLoader(resource);
            Parent view = loader.load();
            SearchController controller = loader.getController();
            controller.setMainController(this);

            searchController = controller;
            searchFuture.complete(view);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load search FXML", e);
            searchFuture.completeExceptionally(e);
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

        // Find if contact already exists
        ContactInfo target = null;
        for (ContactInfo info : contactsList.getItems()) {
            if (info.id().equals(userId)) {
                target = info;
                break;
            }
        }

        // Add if not exists
        if (target == null) {
            target = ContactInfo.online(userId, fullName);
            contactsList.getItems().add(target);
        }

        // Select and load chat. Note: activateMessagesTab is triggered by the selection
        // listener.
        contactsList.getSelectionModel().select(target);
        contactsList.setUserData(target);
    }

    public boolean hasContact(String userId) {
        for (ContactInfo info : contactsList.getItems()) {
            if (info.id().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    public void addContact(String userId, String fullName) {
        if (!hasContact(userId)) {
            contactsList.getItems().add(ContactInfo.online(userId, fullName));
        }
    }

    public void removeContact(String userId) {
        contactsList.getItems().removeIf(info -> info.id().equals(userId));
        ContactInfo selected = contactsList.getSelectionModel().getSelectedItem();
        if (selected != null && selected.id().equals(userId)) {
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

            // If we are currently in Search Mode, clicking any contact should transport us
            // back to the Messages tab.
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
        placeholderFuture
                .thenAccept(view -> javafx.application.Platform.runLater(() -> contentPane.getChildren().setAll(view)));

        if (!placeholderLoadingStarted.get()) {
            ensurePlaceholderLoaded();
        }
    }

    private void ensurePlaceholderLoaded() {
        if (placeholderLoadingStarted.getAndSet(true)) {
            return;
        }

        try {
            var resource = getClass().getResource(UiConstants.FXML_PLACEHOLDER);
            LOGGER.log(Level.INFO, "Loading placeholder FXML: {0}", resource);
            FXMLLoader loader = new FXMLLoader(resource);
            Parent view = loader.load();
            placeholderFuture.complete(view);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not load placeholder FXML", e);
            placeholderFuture.completeExceptionally(e);
        }
    }

    private void loadChat(String recipientId) {
        // Reuse chat view if we are already chatting with the same contact
        if (currentChatView != null && recipientId.equals(currentChatRecipientId)) {
            contentPane.getChildren().setAll(currentChatView);
            return;
        }

        // Load new chat view in a background thread to prevent UI freeze
        Thread.ofVirtual().name("chat-loader").start(() -> {
            try {
                var resource = getClass().getResource(UiConstants.FXML_CHAT);
                LOGGER.log(Level.INFO, "Loading chat FXML: {0}", resource);
                FXMLLoader loader = new FXMLLoader(resource);
                javafx.scene.Parent view = loader.load();

                ChatController chatController = loader.getController();
                chatController.setRecipient(recipientId);

                // Update UI once the view is fully prepared in memory
                javafx.application.Platform.runLater(() -> {
                    currentChatView = view;
                    currentChatRecipientId = recipientId;
                    contentPane.getChildren().setAll(view);
                });
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not load chat FXML", e);
            }
        });
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
