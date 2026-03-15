package com.haf.client.controllers;

import com.haf.client.core.ChatSession;
import com.haf.client.core.NetworkSession;
import com.haf.client.models.ContactInfo;
import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.MainViewModel;
import com.haf.client.viewmodels.MessageViewModel;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Main application view ({@code main.fxml}).
 */
public class MainController {

    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());
    private static final String NAV_ITEM_ICON = "nav-item-icon";
    private static final String NAV_ITEM_ICON_ACTIVE = "nav-item-icon-active";

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

    // Dots menu button
    @FXML
    private JFXButton dotsMenuButton;

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
    private final MainViewModel viewModel = MainViewModel.createDefault();

    // Cached views for performance
    private Parent currentChatView;
    private ChatController currentChatController;
    private String currentChatRecipientId;
    private MessageViewModel activeMessageViewModel;
    private MessageViewModel.PresenceListener presenceListener;

    private final java.util.concurrent.CompletableFuture<Parent> placeholderFuture = new java.util.concurrent.CompletableFuture<>();
    private final java.util.concurrent.CompletableFuture<Parent> searchFuture = new java.util.concurrent.CompletableFuture<>();
    private final java.util.concurrent.atomic.AtomicBoolean placeholderLoadingStarted = new java.util.concurrent.atomic.AtomicBoolean(
            false);
    private final java.util.concurrent.atomic.AtomicBoolean searchLoadingStarted = new java.util.concurrent.atomic.AtomicBoolean(
            false);

    @FXML
    public void initialize() {
        bindViewModel();
        setupWindowControls();
        setupNavBar();
        setupDotsMenu();
        setupContactList();
        setupContactSelection();
        setupSearchField();
        registerPresenceListener();

        // Trigger pre-loading immediately
        triggerPreloading();

        // Fetch contacts from server
        viewModel.fetchContacts();

        // Messages tab is active by default
        activateMessagesTab();
    }

    private void bindViewModel() {
        contactsList.setItems(viewModel.contactsProperty());
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
                if (viewModel.hasSearchResultsProperty().get()) {
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
            viewModel.setHasSearchResults(true);
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
        viewModel.setHasSearchResults(false);
        // Restore magnify icon
        if (searchActionIcon != null) {
            searchActionIcon.setIconLiteral("mdi2m-magnify");
        }
    }

    private void activateMessagesTab() {
        viewModel.setActiveTab(MainViewModel.MainTab.MESSAGES);
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
        viewModel.setActiveTab(MainViewModel.MainTab.SEARCH);
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
            iconMessages.getStyleClass().remove(NAV_ITEM_ICON);
            if (!iconMessages.getStyleClass().contains(NAV_ITEM_ICON_ACTIVE)) {
                iconMessages.getStyleClass().add(NAV_ITEM_ICON_ACTIVE);
            }
            iconSearch.getStyleClass().remove(NAV_ITEM_ICON_ACTIVE);
            if (!iconSearch.getStyleClass().contains(NAV_ITEM_ICON)) {
                iconSearch.getStyleClass().add(NAV_ITEM_ICON);
            }
        } else {
            iconSearch.getStyleClass().remove(NAV_ITEM_ICON);
            if (!iconSearch.getStyleClass().contains(NAV_ITEM_ICON_ACTIVE)) {
                iconSearch.getStyleClass().add(NAV_ITEM_ICON_ACTIVE);
            }
            iconMessages.getStyleClass().remove(NAV_ITEM_ICON_ACTIVE);
            if (!iconMessages.getStyleClass().contains(NAV_ITEM_ICON)) {
                iconMessages.getStyleClass().add(NAV_ITEM_ICON);
            }
        }
    }

    /**
     * Loads search.fxml into the content pane.
     */
    private void loadSearchView() {
        searchFuture.thenAccept(view -> javafx.application.Platform.runLater(
                () -> contentPane.getChildren().setAll(view)));

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
    public void startChatWith(String userId, String fullName, String regNumber) {
        viewModel.addContact(userId, fullName, regNumber);
        ContactInfo target = viewModel.getContactById(userId);
        if (target == null) {
            target = viewModel.ensureChatContact(userId, fullName, regNumber);
        }

        // Select and load chat. Note: activateMessagesTab is triggered by the selection
        // listener.
        contactsList.getSelectionModel().select(target);
        contactsList.setUserData(target);
    }

    public boolean hasContact(String userId) {
        return viewModel.hasContact(userId);
    }

    public void addContact(String userId, String fullName, String regNumber) {
        viewModel.addContact(userId, fullName, regNumber);
    }

    public void removeContact(String userId) {
        viewModel.removeContact(userId);
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
        // Reuse chat view if already loaded — just switch recipient
        if (currentChatView != null && currentChatController != null) {
            if (!recipientId.equals(currentChatRecipientId)) {
                currentChatController.setRecipient(recipientId);
                currentChatRecipientId = recipientId;
            }
            contentPane.getChildren().setAll(currentChatView);
            return;
        }

        // First load: create the chat view in a background thread
        Thread.ofVirtual().name("chat-loader").start(() -> {
            try {
                var resource = getClass().getResource(UiConstants.FXML_CHAT);
                LOGGER.log(Level.INFO, "Loading chat FXML: {0}", resource);
                FXMLLoader loader = new FXMLLoader(resource);
                javafx.scene.Parent view = loader.load();

                ChatController chatController = loader.getController();
                chatController.setRecipient(recipientId);

                javafx.application.Platform.runLater(() -> {
                    currentChatView = view;
                    currentChatController = chatController;
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

    private void setupDotsMenu() {
        if (dotsMenuButton == null) {
            return;
        }

        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("dropdown-menu");

        MenuItem profileItem = createIconMenuItem("mdi2a-account-circle-outline", "Profile");
        MenuItem settingsItem = createIconMenuItem("mdi2c-cog-outline", "Settings");
        MenuItem helpItem = createIconMenuItem("mdi2h-help-circle-outline", "Help");
        MenuItem logoutItem = createIconMenuItem("mdi2l-logout", "Log out");

        profileItem.setOnAction(e -> LOGGER.info("TODO: Profile clicked"));
        settingsItem.setOnAction(e -> LOGGER.info("TODO: Settings clicked"));
        helpItem.setOnAction(e -> LOGGER.info("TODO: Help clicked"));
        logoutItem.setOnAction(e -> handleLogout());

        menu.getItems().addAll(
                profileItem,
                new SeparatorMenuItem(),
                settingsItem,
                helpItem,
                new SeparatorMenuItem(),
                logoutItem);

        dotsMenuButton.setOnAction(e -> {
            if (menu.isShowing()) {
                menu.hide();
            } else {
                showDotsMenuAnchored(menu);
            }
        });
    }

    private void showDotsMenuAnchored(ContextMenu menu) {
        Bounds bounds = dotsMenuButton.localToScreen(dotsMenuButton.getBoundsInLocal());
        if (bounds == null) {
            menu.show(dotsMenuButton, 0, 0);
            return;
        }

        double menuWidth = Math.max(menu.prefWidth(-1), 220);
        menu.show(dotsMenuButton, bounds.getMaxX() - menuWidth, bounds.getMaxY() + 4);
    }

    private MenuItem createIconMenuItem(String iconLiteral, String text) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(22);
        Label label = new Label(text, icon);
        label.setGraphicTextGap(12);
        MenuItem item = new MenuItem();
        item.setGraphic(label);
        item.getStyleClass().add("dropdown-menu-item");
        label.getStyleClass().add("dropdown-menu-label");
        return item;
    }

    private void handleLogout() {
        LOGGER.info("Logging out...");
        Thread.ofVirtual().name("logout-thread").start(() -> {
            // 1. Revoke server session (best effort), then close socket.
            if (NetworkSession.get() != null) {
                try {
                    NetworkSession.get().postAuthenticated("/api/v1/logout", "{}").get(3, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Logout API call failed; continuing with local logout", ex);
                }
                try {
                    NetworkSession.get().close();
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "Error closing WebSocket on logout", ex);
                }
            }

            // 2. Clear session singletons
            unregisterPresenceListener();
            NetworkSession.clear();
            ChatSession.clear();

            // 3. Navigate back to login screen
            Platform.runLater(() -> ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN));
        });
    }

    private void registerPresenceListener() {
        activeMessageViewModel = ChatSession.get();
        if (activeMessageViewModel == null) {
            return;
        }
        presenceListener = this::applyPresenceUpdate;
        activeMessageViewModel.addPresenceListener(presenceListener);
    }

    private void unregisterPresenceListener() {
        if (activeMessageViewModel != null && presenceListener != null) {
            activeMessageViewModel.removePresenceListener(presenceListener);
        }
        activeMessageViewModel = null;
        presenceListener = null;
    }

    private void applyPresenceUpdate(String userId, boolean active) {
        Platform.runLater(() -> updateContactPresence(userId, active));
    }

    private void updateContactPresence(String userId, boolean active) {
        ContactInfo updated = viewModel.updateContactPresence(userId, active);
        if (updated == null) {
            return;
        }

        ContactInfo selected = contactsList.getSelectionModel().getSelectedItem();
        if (selected != null && selected.id().equals(userId)) {
            contactsList.getSelectionModel().select(updated);
            showProfilePanel(updated);
        }
    }
}
