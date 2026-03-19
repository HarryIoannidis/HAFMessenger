package com.haf.client.controllers;

import com.haf.client.core.ChatSession;
import com.haf.client.core.CurrentUserSession;
import com.haf.client.models.UserProfileInfo;
import com.haf.client.services.DefaultMainSessionService;
import com.haf.client.services.MainSessionService;
import com.haf.client.models.ContactInfo;
import com.haf.client.utils.ContextMenuBuilder;
import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.MainViewModel;
import com.haf.shared.dto.UserSearchResultDTO;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
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
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Main application view ({@code main.fxml}).
 */
public class MainController implements SearchContactActions {

    private static final Logger LOGGER = Logger.getLogger(MainController.class.getName());
    private static final String NAV_ITEM_ICON = "nav-item-icon";
    private static final String NAV_ITEM_ICON_ACTIVE = "nav-item-icon-active";
    private static final String PROFILE_POPUP_KEY = "profile-popup";

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
    @FXML
    private JFXButton profilePopupButton;

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
    private ContactInfo contactContextTarget;
    private ContextMenu contactContextMenu;

    /** Reference to the loaded SearchController (null when not in search mode). */
    private SearchController searchController;

    /** Tracks whether results are currently displayed (for clear/search toggle). */
    private final MainViewModel viewModel = MainViewModel.createDefault();
    private final MainSessionService mainSessionService;

    // Cached views for performance
    private Parent currentChatView;
    private ChatController currentChatController;
    private String currentChatRecipientId;

    private final java.util.concurrent.CompletableFuture<Parent> placeholderFuture = new java.util.concurrent.CompletableFuture<>();
    private final java.util.concurrent.CompletableFuture<Parent> searchFuture = new java.util.concurrent.CompletableFuture<>();
    private final java.util.concurrent.atomic.AtomicInteger chatLoadGeneration = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicBoolean placeholderLoadingStarted = new java.util.concurrent.atomic.AtomicBoolean(
            false);
    private final java.util.concurrent.atomic.AtomicBoolean searchLoadingStarted = new java.util.concurrent.atomic.AtomicBoolean(
            false);

    enum ContactContextAction {
        PROFILE,
        DELETE_CHAT,
        REMOVE_CONTACT
    }

    public MainController() {
        this(new DefaultMainSessionService());
    }

    MainController(MainSessionService mainSessionService) {
        this.mainSessionService = Objects.requireNonNull(mainSessionService, "mainSessionService");
    }

    @FXML
    public void initialize() {
        bindViewModel();
        bindSelectedContactProfileSync();
        setupWindowControls();
        setupNavBar();
        setupDotsMenu();
        setupContactContextMenu();
        setupContactList();
        setupContactSelection();
        setupSearchField();
        setupProfilePopupTrigger();
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

    private void bindSelectedContactProfileSync() {
        viewModel.contactsProperty().addListener((ListChangeListener<ContactInfo>) change -> {
            boolean changed = false;
            while (change.next()) {
                changed = true;
            }
            if (changed) {
                // Keep profile strip synchronized when contact entries are replaced in
                // the list (presence refresh/update paths).
                refreshProfilePanelForSelectedContact();
            }
        });
    }

    private void refreshProfilePanelForSelectedContact() {
        if (viewModel.activeTabProperty().get() != MainViewModel.MainTab.MESSAGES) {
            return;
        }

        String trackedContactId = resolveTrackedContactId();
        if (trackedContactId == null) {
            return;
        }

        ContactInfo latest = viewModel.getContactById(trackedContactId);
        if (latest != null) {
            ContactInfo selected = contactsList.getSelectionModel().getSelectedItem();
            if (!latest.equals(selected)) {
                // Selection can keep a stale object reference after list item replacement.
                // Re-select the current model instance so header/list stay consistent.
                contactsList.getSelectionModel().select(latest);
            }
            contactsList.setUserData(latest);
            showProfilePanel(latest);
        }
    }

    private String resolveTrackedContactId() {
        if (currentChatRecipientId != null && !currentChatRecipientId.isBlank()) {
            return currentChatRecipientId;
        }

        Object tracked = contactsList.getUserData();
        if (tracked instanceof ContactInfo trackedContact && trackedContact.id() != null && !trackedContact.id().isBlank()) {
            return trackedContact.id();
        }

        ContactInfo selected = contactsList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.id() == null || selected.id().isBlank()) {
            return null;
        }
        return selected.id();
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

    private void setupProfilePopupTrigger() {
        if (profilePopupButton == null) {
            return;
        }

        profilePopupButton.setOnAction(e -> openSelectedContactProfilePopup());
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
            ContactInfo latest = viewModel.getContactById(selected.id());
            ContactInfo contactToShow = latest != null ? latest : selected;
            contactsList.setUserData(contactToShow);
            showProfilePanel(contactToShow);
            loadChat(contactToShow.id());
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
            controller.setContactActions(this);

            searchController = controller;
            searchFuture.complete(view);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load search FXML", e);
            searchFuture.completeExceptionally(e);
        }
    }

    private void showProfilePanel(ContactInfo contact) {
        profileNameText.setText(contact.name());
        String activenessLabel = contact.activenessLabel() == null ? "" : contact.activenessLabel().trim();
        profileActivenessText.setText(activenessLabel);
        boolean hasActivenessLabel = !activenessLabel.isEmpty();
        profileActivenessText.setVisible(hasActivenessLabel);
        profileActivenessText.setManaged(hasActivenessLabel);
        profileActivenessCircle.setVisible(hasActivenessLabel);
        profileActivenessCircle.setManaged(hasActivenessLabel);
        if (hasActivenessLabel) {
            try {
                profileActivenessCircle.setFill(Color.web(contact.activenessColor()));
            } catch (IllegalArgumentException ex) {
                profileActivenessCircle.setFill(Color.GRAY);
            }
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

    private void openSelectedContactProfilePopup() {
        String trackedContactId = resolveTrackedContactId();
        if (trackedContactId == null) {
            return;
        }

        ContactInfo contact = viewModel.getContactById(trackedContactId);
        if (contact == null) {
            contact = contactsList.getSelectionModel().getSelectedItem();
        }
        if (contact == null) {
            return;
        }

        openProfilePopup(UserProfileInfo.fromContact(contact, false));
    }

    private void openSelfProfilePopup() {
        UserProfileInfo profile = CurrentUserSession.get();
        if (profile == null) {
            LOGGER.warning("Self profile is not available in session.");
            return;
        }
        openProfilePopup(profile.asSelfProfile(true));
    }

    private void openProfilePopup(UserProfileInfo profile) {
        if (profile == null) {
            return;
        }

        ViewRouter.showPopup(
                PROFILE_POPUP_KEY,
                UiConstants.FXML_PROFILE,
                ProfileController.class,
                controller -> controller.showProfile(profile));
    }

    private void setupContactList() {
        contactsList.setCellFactory(lv -> {
            ContactCell cell = new ContactCell();
            cell.setOnClick(this::activateMessagesTab);
            cell.setOnContextMenuRequest(this::showContactContextMenu);
            return cell;
        });
    }

    /**
     * Adds a searched user to the contacts list and switches to their chat.
     */
    @Override
    public void startChatWith(UserSearchResultDTO result) {
        if (result == null || result.getUserId() == null || result.getUserId().isBlank()) {
            return;
        }

        viewModel.addContact(result);
        ContactInfo target = viewModel.getContactById(result.getUserId());
        if (target == null) {
            target = viewModel.ensureChatContact(result);
        }
        if (target == null) {
            return;
        }

        // Select and load chat. Note: activateMessagesTab is triggered by the selection
        // listener.
        contactsList.getSelectionModel().select(target);
        contactsList.setUserData(target);
    }

    @Override
    public boolean hasContact(String userId) {
        return viewModel.hasContact(userId);
    }

    @Override
    public void addContact(UserSearchResultDTO result) {
        viewModel.addContact(result);
    }

    @Override
    public void removeContact(String userId) {
        ContactInfo selectedBeforeRemoval = contactsList.getSelectionModel().getSelectedItem();
        viewModel.removeContact(userId);
        boolean contactsEmptyAfterRemoval = viewModel.contactsProperty().isEmpty();
        if (shouldShowPlaceholderAfterRemoval(
                userId,
                selectedBeforeRemoval,
                currentChatRecipientId,
                contactsEmptyAfterRemoval)) {
            clearSelectionAndShowPlaceholder();
        }
    }

    @Override
    public void openProfile(UserSearchResultDTO result) {
        UserProfileInfo profile = UserProfileInfo.fromSearchResult(result, false);
        if (profile == null) {
            return;
        }
        openProfilePopup(profile);
    }

    private void setupContactSelection() {
        contactsList.setOnMouseClicked(event -> {
            if (!isPrimaryClick(event.getButton())) {
                return;
            }

            ContactInfo clicked = contactsList.getSelectionModel().getSelectedItem();
            if (clicked == null) {
                return;
            }

            Object lastSelected = contactsList.getUserData();
            MainViewModel.ContactSelectionAction action = viewModel.resolveContactSelectionAction(
                    viewModel.activeTabProperty().get(),
                    clicked.equals(lastSelected));

            applyContactSelectionAction(action,
                    () -> {
                        activateMessagesTab();
                        contactsList.setUserData(clicked);
                    },
                    this::clearSelectionAndShowPlaceholder,
                    () -> contactsList.setUserData(clicked));
        });

        contactsList.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldContact, newContact) -> {
                    if (newContact != null) {
                        contactsList.setUserData(newContact);
                        activateMessagesTab();
                    }
                });
    }

    private void clearSelectionAndShowPlaceholder() {
        contactsList.getSelectionModel().clearSelection();
        contactsList.setUserData(null);
        currentChatRecipientId = null;
        hideProfilePanel();
        loadPlaceholder();
    }

    static void applyContactSelectionAction(
            MainViewModel.ContactSelectionAction action,
            Runnable switchToMessagesAction,
            Runnable deselectAndPlaceholderAction,
            Runnable keepSelectionAction) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(switchToMessagesAction, "switchToMessagesAction");
        Objects.requireNonNull(deselectAndPlaceholderAction, "deselectAndPlaceholderAction");
        Objects.requireNonNull(keepSelectionAction, "keepSelectionAction");

        switch (action) {
            case SWITCH_TO_MESSAGES_TAB -> switchToMessagesAction.run();
            case DESELECT_AND_SHOW_PLACEHOLDER -> deselectAndPlaceholderAction.run();
            case KEEP_SELECTED_CONTACT -> keepSelectionAction.run();
        }
    }

    static void applyContactContextAction(
            ContactContextAction action,
            Runnable openProfileAction,
            Runnable deleteChatAction,
            Runnable removeContactAction) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(openProfileAction, "openProfileAction");
        Objects.requireNonNull(deleteChatAction, "deleteChatAction");
        Objects.requireNonNull(removeContactAction, "removeContactAction");

        switch (action) {
            case PROFILE -> openProfileAction.run();
            case DELETE_CHAT -> deleteChatAction.run();
            case REMOVE_CONTACT -> removeContactAction.run();
        }
    }

    static boolean shouldShowPlaceholderAfterRemoval(
            String removedUserId,
            ContactInfo selectedBeforeRemoval,
            String activeChatRecipientId,
            boolean contactsEmptyAfterRemoval) {
        if (contactsEmptyAfterRemoval) {
            return true;
        }
        if (removedUserId == null || removedUserId.isBlank()) {
            return false;
        }
        if (selectedBeforeRemoval != null && removedUserId.equals(selectedBeforeRemoval.id())) {
            return true;
        }
        return activeChatRecipientId != null && removedUserId.equals(activeChatRecipientId);
    }

    private void setupContactContextMenu() {
        contactContextMenu = ContextMenuBuilder.create()
                .addOption(
                        "mdi2a-account-circle-outline",
                        "Profile",
                        () -> handleContactContextAction(ContactContextAction.PROFILE))
                .addSeparator()
                .addOption(
                        "mdi2d-delete-outline",
                        "Delete chat",
                        () -> handleContactContextAction(ContactContextAction.DELETE_CHAT))
                .addOption(
                        "mdi2a-account-remove-outline",
                        "Remove contact",
                        () -> handleContactContextAction(ContactContextAction.REMOVE_CONTACT))
                .onHidden(() -> contactContextTarget = null)
                .build();
    }

    private void showContactContextMenu(ContactInfo contact, double screenX, double screenY) {
        if (contactContextMenu == null || contact == null) {
            return;
        }

        contactContextTarget = contact;
        if (!contact.equals(contactsList.getSelectionModel().getSelectedItem())) {
            selectContactAndOpenChat(contact);
        }

        if (contactContextMenu.isShowing()) {
            contactContextMenu.hide();
        }
        contactContextMenu.show(contactsList, screenX, screenY);
    }

    private void handleContactContextAction(ContactContextAction action) {
        ContactInfo target = contactContextTarget != null ? contactContextTarget : contactsList.getSelectionModel().getSelectedItem();
        if (target == null) {
            return;
        }

        applyContactContextAction(
                action,
                () -> openProfilePopup(UserProfileInfo.fromContact(target, false)),
                () -> clearLocalChatHistory(target.id()),
                () -> removeContact(target.id()));
    }

    private void clearLocalChatHistory(String contactId) {
        if (contactId == null || contactId.isBlank()) {
            return;
        }
        var messageViewModel = ChatSession.get();
        if (messageViewModel == null) {
            return;
        }

        messageViewModel.getMessages(contactId).clear();
        if (currentChatController != null && contactId.equals(currentChatRecipientId)) {
            currentChatController.setRecipient(contactId);
        }
    }

    private void selectContactAndOpenChat(ContactInfo contact) {
        contactsList.getSelectionModel().select(contact);
        int index = contactsList.getItems().indexOf(contact);
        if (index >= 0) {
            contactsList.getFocusModel().focus(index);
        }
        contactsList.setUserData(contact);
        activateMessagesTab();
    }

    private static boolean isPrimaryClick(MouseButton button) {
        return button == MouseButton.PRIMARY;
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
        int loadGeneration = chatLoadGeneration.incrementAndGet();

        // Reuse chat view if already loaded — just switch recipient
        if (currentChatView != null && currentChatController != null) {
            if (!recipientId.equals(currentChatRecipientId)) {
                currentChatController.setRecipient(recipientId);
                currentChatRecipientId = recipientId;
            }
            contentPane.getChildren().setAll(currentChatView);
            return;
        }

        Runnable loadAction = () -> {
            try {
                var resource = getClass().getResource(UiConstants.FXML_CHAT);
                LOGGER.log(Level.INFO, "Loading chat FXML: {0}", resource);
                FXMLLoader loader = new FXMLLoader(resource);
                javafx.scene.Parent view = loader.load();

                ChatController chatController = loader.getController();

                if (loadGeneration != chatLoadGeneration.get()) {
                    return;
                }
                chatController.setRecipient(recipientId);
                currentChatView = view;
                currentChatController = chatController;
                currentChatRecipientId = recipientId;
                contentPane.getChildren().setAll(view);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Could not load chat FXML", e);
            }
        };

        if (javafx.application.Platform.isFxApplicationThread()) {
            loadAction.run();
        } else {
            javafx.application.Platform.runLater(loadAction);
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

    private void setupDotsMenu() {
        if (dotsMenuButton == null) {
            return;
        }

        ContextMenu menu = ContextMenuBuilder.create()
                .addOption("mdi2a-account-circle-outline", "Profile", this::openSelfProfilePopup)
                .addSeparator()
                .addOption("mdi2c-cog-outline", "Settings", () -> LOGGER.info("TODO: Settings clicked"))
                .addOption("mdi2h-help-circle-outline", "Help", () -> LOGGER.info("TODO: Help clicked"))
                .addSeparator()
                .addOption("mdi2l-logout", "Log out", this::handleLogout)
                .build();

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

    private void handleLogout() {
        LOGGER.info("Logging out...");
        mainSessionService.logout().whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.log(Level.WARNING, "Logout completed with errors", throwable);
            }
            Platform.runLater(() -> ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN));
        });
    }

    private void registerPresenceListener() {
        mainSessionService.registerPresenceListener(this::applyPresenceUpdate);
    }

    private void applyPresenceUpdate(String userId, boolean active) {
        Platform.runLater(() -> updateContactPresence(userId, active));
    }

    private void updateContactPresence(String userId, boolean active) {
        ContactInfo updated = viewModel.updateContactPresence(userId, active);
        if (updated == null) {
            return;
        }

        String trackedContactId = resolveTrackedContactId();
        if (trackedContactId != null && trackedContactId.equals(userId)) {
            ContactInfo selected = contactsList.getSelectionModel().getSelectedItem();
            if (!updated.equals(selected)) {
                contactsList.getSelectionModel().select(updated);
            }
            contactsList.setUserData(updated);
            currentChatRecipientId = updated.id();
            showProfilePanel(updated);
        }
    }
}
