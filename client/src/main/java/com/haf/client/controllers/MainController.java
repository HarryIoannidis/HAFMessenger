package com.haf.client.controllers;

import com.haf.client.core.ChatSession;
import com.haf.client.core.CurrentUserSession;
import com.haf.client.models.UserProfileInfo;
import com.haf.client.models.MessageVM;
import com.haf.client.services.DefaultMainSessionService;
import com.haf.client.services.MainSessionService;
import com.haf.client.models.ContactInfo;
import com.haf.client.utils.ContextMenuBuilder;
import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.MainViewModel;
import com.haf.client.viewmodels.MessageViewModel;
import com.haf.client.viewmodels.SearchSortViewModel;
import com.haf.shared.dto.UserSearchResultDTO;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

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
    private JFXButton filterButton;
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
    private EventHandler<MouseEvent> contactContextOutsideClickHandler;

    /** Tracks whether results are currently displayed (for clear/search toggle). */
    private final MainViewModel viewModel = MainViewModel.createDefault();
    private final MainSessionService mainSessionService;
    private MainContentLoader contentLoader;
    private SearchFilterController searchFilterUi;

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
        contentLoader = new MainContentLoader(contentPane, this);
        bindViewModel();
        bindSelectedContactProfileSync();
        setupWindowControls();
        setupNavBar();
        setupDotsMenu();
        setupContactContextMenu();
        setupContactContextMenuOutsideClickClose();
        setupContactList();
        setupContactSelection();
        setupSearchFilterUi();
        setupSearchField();
        setupProfilePopupTrigger();
        registerPresenceListener();
        registerIncomingMessageListener();
        startMessageReceiving();

        // Trigger pre-loading immediately
        contentLoader.triggerPreloading();

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
        String activeChatRecipientId = contentLoader == null ? null : contentLoader.getCurrentChatRecipientId();
        if (activeChatRecipientId != null && !activeChatRecipientId.isBlank()) {
            return activeChatRecipientId;
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

    private void setupNavBar() {
        navMessages.setOnAction(e -> activateMessagesTab());
        navSearch.setOnAction(e -> activateSearchTab());
    }

    /**
     * Wires Enter key on the search field and the action button (magnify / clear).
     */
    private void setupSearchField() {
        // Enter key triggers search flow through the filter UI state machine.
        toolbarSearchField.setOnAction(e -> triggerSearchFlow());

        // Action button: search or clear depending on state
        if (searchActionButton != null) {
            searchActionButton.setOnAction(e -> {
                if (viewModel.hasSearchResultsProperty().get()) {
                    clearSearch();
                } else {
                    triggerSearchFlow();
                }
            });
        }
    }

    private void setupSearchFilterUi() {
        searchFilterUi = new SearchFilterController(
                this::executeSearchWithFilters,
                this::onSearchExecuted);

        if (filterButton != null) {
            filterButton.setOnAction(e -> searchFilterUi.onFilterButtonTrigger(
                    toolbarSearchField == null ? null : toolbarSearchField.getText(),
                    filterButton));
        }
    }

    private void setupProfilePopupTrigger() {
        if (profilePopupButton == null) {
            return;
        }

        profilePopupButton.setOnAction(e -> openSelectedContactProfilePopup());
    }

    private void triggerSearchFlow() {
        if (searchFilterUi == null) {
            return;
        }
        searchFilterUi.onSearchTrigger(
                toolbarSearchField == null ? null : toolbarSearchField.getText(),
                filterButton != null ? filterButton : searchActionButton);
    }

    private boolean executeSearchWithFilters(String query, SearchSortViewModel.SortOptions sortOptions) {
        SearchController searchController = contentLoader == null ? null : contentLoader.getSearchController();
        if (searchController == null) {
            return false;
        }
        searchController.search(query, sortOptions);
        return true;
    }

    private void onSearchExecuted() {
        viewModel.setHasSearchResults(true);
        if (searchActionIcon != null) {
            searchActionIcon.setIconLiteral("mdi2c-close");
        }
    }

    /**
     * Clears the search results and resets the field and icon.
     */
    private void clearSearch() {
        toolbarSearchField.clear();
        SearchController searchController = contentLoader == null ? null : contentLoader.getSearchController();
        if (searchController != null) {
            searchController.clearResults();
        }
        if (searchFilterUi != null) {
            searchFilterUi.onClear();
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
            viewModel.resetUnreadOnChatOpen(contactToShow.id());
            if (contentLoader != null) {
                contentLoader.showChat(contactToShow.id());
            }
        } else {
            if (contentLoader != null) {
                contentLoader.showPlaceholder();
            }
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
        if (contentLoader != null) {
            contentLoader.showSearchView();
        }
        if (searchFilterUi != null) {
            searchFilterUi.onSearchTabActivated();
        }
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
        String activeChatRecipientId = contentLoader == null ? null : contentLoader.getCurrentChatRecipientId();
        if (viewModel.shouldShowPlaceholderAfterRemoval(
                userId,
                selectedBeforeRemoval,
                activeChatRecipientId)) {
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
                    MainViewModel.isSameContactSelection(clicked, lastSelected));

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
        if (contentLoader != null) {
            contentLoader.clearCurrentChatRecipient();
        }
        hideProfilePanel();
        if (contentLoader != null) {
            contentLoader.showPlaceholder();
        }
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
        contactContextMenu.setAutoHide(true);
    }

    private void setupContactContextMenuOutsideClickClose() {
        if (rootContainer == null) {
            return;
        }

        contactContextOutsideClickHandler = this::handleContactContextOutsideClick;
        rootContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            unregisterContactContextOutsideClickHandler(oldScene);
            registerContactContextOutsideClickHandler(newScene);
        });

        registerContactContextOutsideClickHandler(rootContainer.getScene());
    }

    private void registerContactContextOutsideClickHandler(Scene scene) {
        if (scene == null || contactContextOutsideClickHandler == null) {
            return;
        }
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, contactContextOutsideClickHandler);
    }

    private void unregisterContactContextOutsideClickHandler(Scene scene) {
        if (scene == null || contactContextOutsideClickHandler == null) {
            return;
        }
        scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, contactContextOutsideClickHandler);
    }

    private void handleContactContextOutsideClick(MouseEvent event) {
        if (event == null || contactContextMenu == null || !contactContextMenu.isShowing()) {
            return;
        }

        // ContextMenu lives in its own popup window, so scene clicks are outside clicks.
        contactContextMenu.hide();
    }

    private void showContactContextMenu(ContactInfo contact, double screenX, double screenY) {
        if (contactContextMenu == null || contact == null) {
            return;
        }

        contactContextTarget = contact;
        if (!MainViewModel.isSameContactSelection(contact, contactsList.getSelectionModel().getSelectedItem())) {
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
        if (contentLoader != null) {
            contentLoader.refreshActiveChatIfRecipient(contactId);
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

        double menuWidth = menu.prefWidth(-1);
        if (menuWidth <= 0) {
            menu.show(dotsMenuButton, bounds.getMinX(), bounds.getMaxY() + 4);
            return;
        }
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

    private void registerIncomingMessageListener() {
        mainSessionService.registerIncomingMessageListener(this::applyIncomingMessage);
    }

    private void startMessageReceiving() {
        MessageViewModel chatViewModel = ChatSession.get();
        if (chatViewModel == null) {
            LOGGER.warning("Cannot start message receiving: chat session is not initialized.");
            return;
        }
        chatViewModel.startReceiving();
    }

    private void applyPresenceUpdate(String userId, boolean active) {
        Platform.runLater(() -> updateContactPresence(userId, active));
    }

    private void applyIncomingMessage(String senderId, MessageVM message) {
        if (senderId == null || senderId.isBlank()) {
            return;
        }

        Runnable task = () -> updateUnreadForIncomingMessage(senderId);
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
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
            if (contentLoader != null) {
                contentLoader.setCurrentChatRecipientId(updated.id());
            }
            showProfilePanel(updated);
        }
    }

    private void updateUnreadForIncomingMessage(String senderId) {
        String activeChatRecipientId = contentLoader == null ? null : contentLoader.getCurrentChatRecipientId();
        viewModel.applyIncomingMessage(senderId, activeChatRecipientId);
    }
}
