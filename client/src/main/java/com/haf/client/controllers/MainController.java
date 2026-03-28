package com.haf.client.controllers;

import com.haf.client.core.ChatSession;
import com.haf.client.core.CurrentUserSession;
import com.haf.client.core.Launcher;
import com.haf.client.models.UserProfileInfo;
import com.haf.client.models.MessageVM;
import com.haf.client.services.DefaultMainSessionService;
import com.haf.client.services.MainSessionService;
import com.haf.client.models.ContactInfo;
import com.haf.client.utils.ClientSettings;
import com.haf.client.utils.ContextMenuBuilder;
import com.haf.client.utils.PopupMessageBuilder;
import com.haf.client.utils.RuntimeIssue;
import com.haf.client.utils.RuntimeIssuePopupGate;
import com.haf.client.utils.UiConstants;
import com.haf.client.utils.ViewRouter;
import com.haf.client.viewmodels.MainViewModel;
import com.haf.client.viewmodels.MessagesViewModel;
import com.haf.client.viewmodels.SearchSortViewModel;
import com.haf.shared.dto.UserSearchResultDTO;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.animation.PauseTransition;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.effect.GaussianBlur;
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
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.util.Duration;

/**
 * Controller for the Main application view ({@code main.fxml}).
 */
public class MainController implements SearchController.ContactActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainController.class);
    private static final long RUNTIME_ISSUE_POPUP_COOLDOWN_MS = 10_000L;
    private static final long CHAT_AUTO_RETRY_COOLDOWN_MS = 1_500L;
    private static final long LOGOUT_ON_EXIT_TIMEOUT_SECONDS = 5L;
    private static final long SEARCH_INSTANT_DEBOUNCE_MS = 300L;

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
    private final RuntimeIssuePopupGate runtimeIssuePopupGate = new RuntimeIssuePopupGate(
            RUNTIME_ISSUE_POPUP_COOLDOWN_MS);
    private final RuntimeIssuePopupGate chatAutoRetryGate = new RuntimeIssuePopupGate(
            CHAT_AUTO_RETRY_COOLDOWN_MS);
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final MainSessionService mainSessionService;
    private final ClientSettings settings = ClientSettings.forCurrentUserOrDefaults();
    private PauseTransition instantSearchDebounce;
    private final GaussianBlur privacyBlurEffect = new GaussianBlur();
    private MainContentLoader contentLoader;
    private SearchFilterController searchFilterUi;
    private SearchController runtimeIssueSearchController;
    private final Consumer<RuntimeIssue> runtimeIssueListener = this::handleRuntimeIssue;

    enum ContactContextAction {
        PROFILE,
        DELETE_CHAT,
        REMOVE_CONTACT
    }

    /**
     * Creates the controller with the default main-session service implementation.
     */
    public MainController() {
        this(new DefaultMainSessionService());
    }

    /**
     * Creates the controller with an explicit session service dependency.
     *
     * @param mainSessionService service responsible for logout and event listener
     *                           wiring
     * @throws NullPointerException when {@code mainSessionService} is {@code null}
     */
    MainController(MainSessionService mainSessionService) {
        this.mainSessionService = Objects.requireNonNull(mainSessionService, "mainSessionService");
    }

    /**
     * Initializes main view bindings, UI interactions, listeners, and initial
     * content state.
     */
    @FXML
    public void initialize() {
        contentLoader = new MainContentLoader(contentPane, this, this::handleViewLoadFailure, settings);
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
        applyImmediateSettings();
        registerSettingsListener();
        registerRuntimeIssueListeners();
        registerPresenceListener();
        registerIncomingMessageListener();
        startMessageReceiving();

        // Trigger pre-loading immediately
        contentLoader.triggerPreloading();
        scheduleSettingsPopupPreload();

        // Fetch contacts from server
        viewModel.fetchContacts();

        // Restore tab based on settings policy.
        if (settings.isGeneralRestoreLastTab() && "search".equals(settings.getLastActiveTab())) {
            activateSearchTab();
        } else {
            activateMessagesTab();
        }
    }

    /**
     * Binds the contacts list view to the view-model observable contacts
     * collection.
     */
    private void bindViewModel() {
        contactsList.setItems(viewModel.contactsProperty());
    }

    /**
     * Keeps the profile strip synchronized when contact entries are replaced in the
     * list.
     */
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

    /**
     * Refreshes profile-strip data for the tracked/selected contact when contact
     * model instances are replaced.
     */
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

    /**
     * Resolves the contact id currently tracked by the chat area or list selection
     * state.
     *
     * @return tracked contact id, or {@code null} when no contact is currently
     *         tracked
     */
    private String resolveTrackedContactId() {
        String activeChatRecipientId = contentLoader == null ? null : contentLoader.getCurrentChatRecipientId();
        if (activeChatRecipientId != null && !activeChatRecipientId.isBlank()) {
            return activeChatRecipientId;
        }

        Object tracked = contactsList.getUserData();
        if (tracked instanceof ContactInfo trackedContact && trackedContact.id() != null
                && !trackedContact.id().isBlank()) {
            return trackedContact.id();
        }

        ContactInfo selected = contactsList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.id() == null || selected.id().isBlank()) {
            return null;
        }
        return selected.id();
    }

    /**
     * Wires navigation buttons to switch between messages and search tabs.
     */
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

        setupSearchActionButton();

        toolbarSearchField.textProperty().addListener((obs, oldValue, newValue) ->
                handleSearchFieldTextChanged(newValue));
    }

    /**
     * Wires the search action button to toggle between search and clear actions
     * based on current search state.
     */
    private void setupSearchActionButton() {
        if (searchActionButton == null) {
            return;
        }
        searchActionButton.setOnAction(e -> {
            if (viewModel.hasSearchResultsProperty().get()) {
                clearSearch();
            } else {
                triggerSearchFlow();
            }
        });
    }

    /**
     * Handles text changes in the search field by scheduling a debounced instant
     * search when the feature is enabled and the search tab is active.
     *
     * @param newValue updated search field text
     */
    private void handleSearchFieldTextChanged(String newValue) {
        if (!settings.isSearchInstantOnType()
                || viewModel.activeTabProperty().get() != MainViewModel.MainTab.SEARCH) {
            return;
        }

        PauseTransition debounce = getInstantSearchDebounce();
        debounce.stop();
        debounce.setOnFinished(event -> handleInstantSearchDebounceFinished(newValue));
        debounce.playFromStart();
    }

    /**
     * Executes the instant search or clears results when the debounce timer fires.
     *
     * @param text search field text at the time the debounce was scheduled
     */
    private void handleInstantSearchDebounceFinished(String text) {
        if (!settings.isSearchInstantOnType()
                || viewModel.activeTabProperty().get() != MainViewModel.MainTab.SEARCH) {
            return;
        }
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            clearSearch();
            return;
        }
        triggerSearchFlow();
    }

    private PauseTransition getInstantSearchDebounce() {
        if (instantSearchDebounce == null) {
            instantSearchDebounce = new PauseTransition(Duration.millis(SEARCH_INSTANT_DEBOUNCE_MS));
        }
        return instantSearchDebounce;
    }

    /**
     * Creates and wires the search/filter orchestration controller used by toolbar
     * actions.
     */
    private void setupSearchFilterUi() {
        searchFilterUi = new SearchFilterController(
                this::executeSearchWithFilters,
                this::onSearchExecuted);
        applySearchFilterSettings();

        if (filterButton != null) {
            filterButton.setOnAction(e -> searchFilterUi.onFilterButtonTrigger(
                    toolbarSearchField == null ? null : toolbarSearchField.getText(),
                    filterButton));
        }
    }

    /**
     * Wires the profile button in the toolbar to open the selected-contact profile
     * popup.
     */
    private void setupProfilePopupTrigger() {
        if (profilePopupButton == null) {
            return;
        }

        profilePopupButton.setOnAction(e -> openSelectedContactProfilePopup());
    }

    /**
     * Triggers the search flow using the current toolbar query and active filter
     * options.
     */
    private void triggerSearchFlow() {
        if (searchFilterUi == null) {
            return;
        }
        searchFilterUi.onSearchTrigger(
                toolbarSearchField == null ? null : toolbarSearchField.getText(),
                filterButton != null ? filterButton : searchActionButton);
    }

    /**
     * Executes a search request through the loaded search content controller.
     *
     * @param query       raw search query text
     * @param sortOptions selected sort/filter options to apply
     * @return {@code true} when a search controller is available and search is
     *         dispatched, otherwise {@code false}
     */
    private boolean executeSearchWithFilters(String query, SearchSortViewModel.SortOptions sortOptions) {
        bindSearchRuntimeIssueListener();
        SearchController searchController = contentLoader == null ? null : contentLoader.getSearchController();
        if (searchController == null) {
            return false;
        }
        searchController.search(query, sortOptions);
        return true;
    }

    /**
     * Marks search state as populated and flips the toolbar action icon to the
     * clear symbol.
     */
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

    /**
     * Activates the messages tab, restores message-oriented panels, and opens
     * current chat/placeholder content.
     */
    private void activateMessagesTab() {
        MainViewModel.MainTab previousTab = viewModel.activeTabProperty().get();
        if (previousTab == MainViewModel.MainTab.SEARCH && !settings.isSearchPreserveLastQuery()) {
            clearSearch();
        }
        viewModel.setActiveTab(MainViewModel.MainTab.MESSAGES);
        settings.setLastActiveTab("messages");
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

    /**
     * Activates the search tab and ensures search UI/content are visible and
     * initialized.
     */
    private void activateSearchTab() {
        viewModel.setActiveTab(MainViewModel.MainTab.SEARCH);
        settings.setLastActiveTab("search");
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
        bindSearchRuntimeIssueListener();
        if (searchFilterUi != null) {
            searchFilterUi.onSearchTabActivated();
        }
    }

    /**
     * Applies active/inactive CSS classes on navigation icons according to the
     * active tab.
     *
     * @param messagesActive whether the messages tab is the active tab
     */
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
     * Shows the profile panel with contact identity and activeness presentation.
     *
     * @param contact contact whose profile data should be rendered in the panel
     */
    private void showProfilePanel(ContactInfo contact) {
        profileNameText.setText(contact.name());
        String activenessLabel = contact.activenessLabel() == null ? "" : contact.activenessLabel().trim();
        profileActivenessText.setText(activenessLabel);
        boolean hasActivenessLabel = !settings.isPrivacyHidePresenceIndicators() && !activenessLabel.isEmpty();
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

    /**
     * Hides the profile panel from both visibility and layout participation.
     */
    private void hideProfilePanel() {
        profilePanel.setVisible(false);
        profilePanel.setManaged(false);
    }

    /**
     * Shows the search panel and hides the profile panel.
     */
    private void showSearchPanel() {
        searchPanel.setVisible(true);
        searchPanel.setManaged(true);
        hideProfilePanel();
    }

    /**
     * Hides the search panel from both visibility and layout participation.
     */
    private void hideSearchPanel() {
        searchPanel.setVisible(false);
        searchPanel.setManaged(false);
    }

    /**
     * Opens a profile popup for the currently tracked or selected contact, if
     * available.
     */
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

    /**
     * Opens the current user's own profile popup from session data.
     */
    private void openSelfProfilePopup() {
        UserProfileInfo profile = CurrentUserSession.get();
        if (profile == null) {
            LOGGER.warn("Self profile is not available in session.");
            return;
        }
        openProfilePopup(profile.asSelfProfile(true));
    }

    /**
     * Opens the generic profile popup with the provided profile model.
     *
     * @param profile profile payload to display
     */
    private void openProfilePopup(UserProfileInfo profile) {
        if (profile == null) {
            return;
        }

        ViewRouter.showPopup(
                "profile-popup",
                UiConstants.FXML_PROFILE,
                ProfileController.class,
                controller -> controller.showProfile(profile));
    }

    /**
     * Opens the settings popup window.
     */
    private void openSettingsPopup() {
        ViewRouter.showPopup(
                UiConstants.POPUP_SETTINGS,
                UiConstants.FXML_SETTINGS,
                SettingsController.class,
                controller -> {
                    controller.setSettings(settings);
                    controller.setRestartRequestHandler(this::requestAppRestart);
                });
    }

    /**
     * Preloads the Settings popup shortly after main-view startup so first open
     * is instant.
     */
    private void scheduleSettingsPopupPreload() {
        Platform.runLater(() -> {
            try {
                ViewRouter.preloadPopup(
                        UiConstants.POPUP_SETTINGS,
                        UiConstants.FXML_SETTINGS,
                        SettingsController.class,
                        controller -> {
                            controller.setSettings(settings);
                            controller.setRestartRequestHandler(this::requestAppRestart);
                        });
            } catch (RuntimeException ex) {
                LOGGER.debug( "Settings popup preload skipped: {}", ex.getMessage());
            }
        });
    }

    /**
     * Configures contact list cells and wires click/context-menu callbacks for each
     * row.
     */
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

    /**
     * Checks whether a user id already exists in the current contacts collection.
     *
     * @param userId user id to test
     * @return {@code true} when contact exists locally
     */
    @Override
    public boolean hasContact(String userId) {
        return viewModel.hasContact(userId);
    }

    /**
     * Adds a search result as a contact in local state.
     *
     * @param result search result to convert into a contact entry
     */
    @Override
    public void addContact(UserSearchResultDTO result) {
        viewModel.addContact(result);
    }

    /**
     * Removes a contact and clears active selection/content when needed.
     *
     * @param userId contact id to remove
     */
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

    /**
     * Opens the profile popup for a search-result user.
     *
     * @param result search result whose profile should be displayed
     */
    @Override
    public void openProfile(UserSearchResultDTO result) {
        UserProfileInfo profile = UserProfileInfo.fromSearchResult(result, false);
        if (profile == null) {
            return;
        }
        openProfilePopup(profile);
    }

    /**
     * Wires mouse/selection interactions for contacts and maps them to view-model
     * selection actions.
     */
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

    /**
     * Clears selected contact state and displays the placeholder content.
     */
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

    /**
     * Executes contact-selection behavior for the action resolved by
     * {@link MainViewModel}.
     *
     * @param action                       resolved action for the current
     *                                     click/selection state
     * @param switchToMessagesAction       callback for switching into messages tab
     * @param deselectAndPlaceholderAction callback for deselecting contact and
     *                                     showing placeholder
     * @param keepSelectionAction          callback for keeping current selection
     *                                     intact
     * @throws NullPointerException when any parameter is {@code null}
     */
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

    /**
     * Executes immediate contact-context menu actions.
     *
     * @param action              context action selected from the menu
     * @param openProfileAction   callback for opening profile
     * @param deleteChatAction    callback for deleting local chat history
     * @param removeContactAction callback for removing contact
     * @throws NullPointerException when any parameter is {@code null}
     */
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

    /**
     * Executes contact-context actions where destructive operations are
     * confirmation-gated.
     *
     * @param action                     context action selected from the menu
     * @param openProfileAction          callback for profile action
     * @param confirmDeleteChatAction    callback for delete-chat confirmation flow
     * @param confirmRemoveContactAction callback for remove-contact confirmation
     *                                   flow
     * @throws NullPointerException when any parameter is {@code null}
     */
    static void applyContactContextActionWithConfirmation(
            ContactContextAction action,
            Runnable openProfileAction,
            Runnable confirmDeleteChatAction,
            Runnable confirmRemoveContactAction) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(openProfileAction, "openProfileAction");
        Objects.requireNonNull(confirmDeleteChatAction, "confirmDeleteChatAction");
        Objects.requireNonNull(confirmRemoveContactAction, "confirmRemoveContactAction");

        switch (action) {
            case PROFILE -> openProfileAction.run();
            case DELETE_CHAT -> confirmDeleteChatAction.run();
            case REMOVE_CONTACT -> confirmRemoveContactAction.run();
        }
    }

    /**
     * Builds the context menu shown for contact list rows.
     */
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

    /**
     * Installs scene-level outside-click handling so the contact context menu
     * closes on external clicks.
     */
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

    /**
     * Registers the outside-click event filter on a scene.
     *
     * @param scene scene receiving the filter
     */
    private void registerContactContextOutsideClickHandler(Scene scene) {
        if (scene == null || contactContextOutsideClickHandler == null) {
            return;
        }
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, contactContextOutsideClickHandler);
    }

    /**
     * Removes the previously registered outside-click event filter from a scene.
     *
     * @param scene scene from which the filter should be removed
     */
    private void unregisterContactContextOutsideClickHandler(Scene scene) {
        if (scene == null || contactContextOutsideClickHandler == null) {
            return;
        }
        scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, contactContextOutsideClickHandler);
    }

    /**
     * Hides the contact context menu when any scene mouse-press happens outside the
     * popup.
     *
     * @param event scene mouse event
     */
    private void handleContactContextOutsideClick(MouseEvent event) {
        if (event == null || contactContextMenu == null || !contactContextMenu.isShowing()) {
            return;
        }

        // ContextMenu lives in its own popup window, so scene clicks are outside
        // clicks.
        contactContextMenu.hide();
    }

    /**
     * Shows the contact context menu at a screen coordinate and syncs selection
     * with the targeted contact.
     *
     * @param contact contact row target
     * @param screenX popup anchor X coordinate in screen space
     * @param screenY popup anchor Y coordinate in screen space
     */
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

    /**
     * Dispatches the currently selected contact context action.
     *
     * @param action selected context-menu action
     */
    private void handleContactContextAction(ContactContextAction action) {
        ContactInfo target = contactContextTarget != null ? contactContextTarget
                : contactsList.getSelectionModel().getSelectedItem();
        if (target == null) {
            return;
        }

        applyContactContextActionWithConfirmation(
                action,
                () -> openProfilePopup(UserProfileInfo.fromContact(target, false)),
                () -> confirmDeleteChat(target),
                () -> confirmRemoveContact(target));
    }

    /**
     * Clears locally cached chat messages for a contact and refreshes the active
     * chat view when necessary.
     *
     * @param contactId contact id whose local timeline should be cleared
     */
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

    /**
     * Selects a contact in the list, focuses its row, and opens the messages
     * tab/chat.
     *
     * @param contact contact to select and open
     */
    private void selectContactAndOpenChat(ContactInfo contact) {
        contactsList.getSelectionModel().select(contact);
        int index = contactsList.getItems().indexOf(contact);
        if (index >= 0) {
            contactsList.getFocusModel().focus(index);
        }
        contactsList.setUserData(contact);
        activateMessagesTab();
    }

    /**
     * Checks whether a mouse click button is the primary button.
     *
     * @param button mouse button to evaluate
     * @return {@code true} when {@code button} is primary
     */
    private static boolean isPrimaryClick(MouseButton button) {
        return button == MouseButton.PRIMARY;
    }

    /**
     * Wires window chrome actions (minimize/maximize/close and title-bar drag
     * movement).
     */
    private void setupWindowControls() {
        Stage stage = ViewRouter.getMainStage();
        if (stage == null) {
            return;
        }

        if (settings.isGeneralRememberWindowState()) {
            restoreWindowState(stage);
        }

        if (minimizeButton != null) {
            minimizeButton.setOnAction(e -> stage.setIconified(true));
        }
        if (maximizeButton != null) {
            maximizeButton.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
        }
        if (closeButton != null) {
            closeButton.setOnAction(e -> confirmExitApplication());
        }

        stage.setOnCloseRequest(event -> {
            if (shutdownInProgress.get()) {
                return;
            }
            if (stage.getScene() == null || stage.getScene().getRoot() != rootContainer) {
                return;
            }
            event.consume();
            confirmExitApplication();
        });

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

        stage.focusedProperty().addListener((obs, oldFocused, newFocused) -> applyPrivacyBlur(Boolean.TRUE.equals(newFocused)));
        applyPrivacyBlur(stage.isFocused());
    }

    private void applyImmediateSettings() {
        applySearchFilterSettings();
        applyContactCellSettings();
        applyPrivacyBlur(ViewRouter.getMainStage() == null || ViewRouter.getMainStage().isFocused());
    }

    private void registerSettingsListener() {
        settings.addListener(key -> Platform.runLater(() -> {
            switch (key) {
                case SEARCH_AUTO_OPEN_FILTER_ON_FIRST_SEARCH -> applySearchFilterSettings();
                case NOTIFICATIONS_SHOW_UNREAD_BADGES, NOTIFICATIONS_BADGE_CAP, PRIVACY_HIDE_PRESENCE_INDICATORS ->
                        applyContactCellSettings();
                case PRIVACY_BLUR_ON_FOCUS_LOSS, PRIVACY_BLUR_STRENGTH -> {
                    Stage stage = ViewRouter.getMainStage();
                    applyPrivacyBlur(stage == null || stage.isFocused());
                }
                default -> {
                    // Other settings are read lazily by their owning controllers.
                }
            }
        }));
    }

    private void applySearchFilterSettings() {
        if (searchFilterUi != null) {
            searchFilterUi.setAutoOpenFilterOnFirstSearch(settings.isSearchAutoOpenFilterOnFirstSearch());
        }
    }

    private void applyContactCellSettings() {
        ContactCell.setShowUnreadBadges(settings.isNotificationsShowUnreadBadges());
        ContactCell.setUnreadBadgeCap(settings.getNotificationsBadgeCap());
        ContactCell.setHidePresenceIndicators(settings.isPrivacyHidePresenceIndicators());
        if (contactsList != null) {
            contactsList.refresh();
        }
        refreshProfilePanelForSelectedContact();
    }

    private void applyPrivacyBlur(boolean focused) {
        if (rootContainer == null) {
            return;
        }
        if (!focused && settings.isPrivacyBlurOnFocusLoss()) {
            privacyBlurEffect.setRadius(Math.max(1.0, settings.getPrivacyBlurStrength() * 2.0));
            rootContainer.setEffect(privacyBlurEffect);
            return;
        }
        rootContainer.setEffect(null);
    }

    private void restoreWindowState(Stage stage) {
        ClientSettings.WindowState state = settings.readWindowState();
        if (stage == null || state == null) {
            return;
        }
        if (state.width() > 0) {
            stage.setWidth(state.width());
        }
        if (state.height() > 0) {
            stage.setHeight(state.height());
        }
        if (!Double.isNaN(state.x())) {
            stage.setX(state.x());
        }
        if (!Double.isNaN(state.y())) {
            stage.setY(state.y());
        }
        stage.setMaximized(state.maximized());
    }

    private void persistWindowState(Stage stage) {
        if (stage == null || !settings.isGeneralRememberWindowState()) {
            return;
        }
        settings.writeWindowState(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), stage.isMaximized());
    }

    /**
     * Builds and wires the top-right dots menu actions.
     */
    private void setupDotsMenu() {
        if (dotsMenuButton == null) {
            return;
        }

        ContextMenu menu = ContextMenuBuilder.create()
                .addOption("mdi2a-account-circle-outline", "Profile", this::openSelfProfilePopup)
                .addSeparator()
                .addOption("mdi2c-cog-outline", "Settings", this::openSettingsPopup)
                .addOption("mdi2h-help-circle-outline", "Help", () -> LOGGER.info("TODO: Help clicked"))
                .addSeparator()
                .addOption("mdi2l-logout", "Log out", this::confirmLogout)
                .build();

        dotsMenuButton.setOnAction(e -> {
            if (menu.isShowing()) {
                menu.hide();
            } else {
                showDotsMenuAnchored(menu);
            }
        });
    }

    /**
     * Shows the dots menu aligned to the button's bottom-right corner when geometry
     * is available.
     *
     * @param menu menu instance to show
     */
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

    /**
     * Executes logout flow and routes the user back to the login view.
     */
    private void handleLogout() {
        LOGGER.info("Logging out...");
        persistWindowState(ViewRouter.getMainStage());
        mainSessionService.logout().whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.warn( "Logout completed with errors", throwable);
            }
            Platform.runLater(() -> ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN));
        });
    }

    private void requestAppRestart() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            return;
        }
        persistWindowState(ViewRouter.getMainStage());

        mainSessionService.logout().whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.warn( "Logout before restart completed with errors.", throwable);
            }

            boolean relaunched = relaunchClientProcess();
            if (!relaunched) {
                shutdownInProgress.set(false);
                Platform.runLater(this::showRestartFailurePopup);
                return;
            }

            Platform.runLater(() -> {
                Platform.exit();
                System.exit(0);
            });
        });
    }

    private boolean relaunchClientProcess() {
        try {
            String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            String classpath = System.getProperty("java.class.path");
            ProcessBuilder processBuilder = new ProcessBuilder(
                    javaBin,
                    "-cp",
                    classpath,
                    Launcher.class.getName());
            processBuilder.start();
            return true;
        } catch (Exception ex) {
            LOGGER.warn( "Failed to relaunch client process.", ex);
            return false;
        }
    }

    private void showRestartFailurePopup() {
        PopupMessageBuilder.create()
                .popupKey("popup-restart-failed")
                .title("Restart failed")
                .message("Could not restart the app automatically. Please restart it manually.")
                .actionText("OK")
                .singleAction(true)
                .show();
    }

    /**
     * Displays confirmation popup before terminating the application.
     */
    private void confirmExitApplication() {
        if (!settings.isGeneralConfirmExit()) {
            exitApplication();
            return;
        }
        PopupMessageBuilder.create()
                .popupKey(UiConstants.POPUP_CONFIRM_EXIT_APP)
                .title("Exit application")
                .message("Close HAF Messenger now?")
                .actionText("Exit")
                .cancelText("Cancel")
                .dangerAction(true)
                .onAction(this::exitApplication)
                .show();
    }

    /**
     * Displays confirmation popup before clearing local chat history for a contact.
     *
     * @param target contact whose chat history may be deleted
     */
    private void confirmDeleteChat(ContactInfo target) {
        String contactName = target == null || target.name() == null || target.name().isBlank() ? "this contact"
                : target.name();
        PopupMessageBuilder.create()
                .popupKey(UiConstants.POPUP_CONFIRM_DELETE_CHAT)
                .title("Delete chat")
                .message("Delete chat with " + contactName + "? This action is not recoverable.")
                .actionText("Delete")
                .cancelText("Cancel")
                .dangerAction(true)
                .onAction(() -> clearLocalChatHistory(target == null ? null : target.id()))
                .show();
    }

    /**
     * Displays confirmation popup before removing a contact from the contact list.
     *
     * @param target contact candidate to remove
     */
    private void confirmRemoveContact(ContactInfo target) {
        if (target == null || target.id() == null || target.id().isBlank()) {
            return;
        }
        String contactName = resolveContactDisplayName(target.name(), target.id());
        PopupMessageBuilder.create()
                .popupKey(UiConstants.POPUP_CONFIRM_REMOVE_CONTACT)
                .title("Remove contact")
                .message("Remove contact with " + contactName + " from your contacts list?")
                .actionText("Remove")
                .cancelText("Cancel")
                .dangerAction(true)
                .onAction(() -> removeContact(target.id()))
                .show();
    }

    /**
     * Resolves a human-readable contact display name for confirmation dialogs.
     *
     * @param name contact name candidate
     * @param id   contact id fallback
     * @return preferred display name string
     */
    private static String resolveContactDisplayName(String name, String id) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        if (id != null && !id.isBlank()) {
            return "user " + id.trim();
        }
        return "this user";
    }

    /**
     * Displays confirmation popup before executing logout.
     */
    private void confirmLogout() {
        PopupMessageBuilder.create()
                .popupKey(UiConstants.POPUP_CONFIRM_LOGOUT)
                .title("Log out")
                .message("Do you want to log out now?")
                .actionText("Log out")
                .cancelText("Cancel")
                .onAction(this::handleLogout)
                .show();
    }

    /**
     * Terminates the JavaFX application and exits the JVM process.
     */
    private void exitApplication() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            return;
        }
        persistWindowState(ViewRouter.getMainStage());

        CompletableFuture<Void> logoutFuture;
        try {
            logoutFuture = mainSessionService.logout()
                    .orTimeout(LOGOUT_ON_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            LOGGER.warn( "Failed to start logout on app exit; continuing shutdown.", ex);
            logoutFuture = CompletableFuture.completedFuture(null);
        }

        logoutFuture.whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.warn( "Logout on app exit completed with errors.", throwable);
            }
            Platform.runLater(() -> {
                Platform.exit();
                System.exit(0);
            });
        });
    }

    /**
     * Handles content-view loading failures by showing a retry-capable popup
     * message.
     *
     * @param viewKind    failing view type
     * @param error       underlying error
     * @param retryAction action invoked when user selects Retry
     */
    private void handleViewLoadFailure(MainContentLoader.ViewKind viewKind, Throwable error, Runnable retryAction) {
        String viewLabel = switch (viewKind) {
            case PLACEHOLDER -> "placeholder";
            case SEARCH -> "search";
            case CHAT -> "chat";
        };
        String reason = error == null || error.getMessage() == null || error.getMessage().isBlank()
                ? "Unknown error."
                : error.getMessage();
        Runnable task = () -> PopupMessageBuilder.create()
                .popupKey(UiConstants.POPUP_VIEW_LOAD_ERROR)
                .title("Failed to load view")
                .message("Could not load " + viewLabel + " view. " + reason)
                .actionText("Retry")
                .cancelText("Cancel")
                .onAction(retryAction)
                .show();

        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    /**
     * Registers runtime-issue listeners from view-models participating in main app
     * flows.
     */
    private void registerRuntimeIssueListeners() {
        viewModel.addRuntimeIssueListener(runtimeIssueListener);

        MessagesViewModel chatViewModel = ChatSession.get();
        if (chatViewModel != null) {
            chatViewModel.addRuntimeIssueListener(runtimeIssueListener);
        }

        bindSearchRuntimeIssueListener();
    }

    /**
     * Binds runtime-issue listener to the loaded search controller once available.
     */
    private void bindSearchRuntimeIssueListener() {
        SearchController searchController = contentLoader == null ? null : contentLoader.getSearchController();
        if (searchController == null || searchController == runtimeIssueSearchController) {
            return;
        }
        if (runtimeIssueSearchController != null) {
            runtimeIssueSearchController.setRuntimeIssueListener(null);
        }
        searchController.setRuntimeIssueListener(runtimeIssueListener);
        runtimeIssueSearchController = searchController;
    }

    /**
     * Presents runtime issues in popup UI with dedup cooldown and retry handling.
     *
     * @param issue recoverable runtime issue
     */
    private void handleRuntimeIssue(RuntimeIssue issue) {
        if (issue == null) {
            return;
        }
        Runnable task = () -> {
            if (isMessagingRuntimeIssue(issue)) {
                handleMessagingRuntimeIssue(issue);
                return;
            }
            if (!settings.isNotificationsShowRuntimePopups()) {
                return;
            }
            if (!runtimeIssuePopupGate.shouldShow(issue.dedupeKey())) {
                return;
            }
            PopupMessageBuilder.create()
                    .popupKey(UiConstants.POPUP_RUNTIME_ISSUE)
                    .title(issue.title())
                    .message(issue.message())
                    .actionText("Retry")
                    .cancelText("Dismiss")
                    .showCancel(true)
                    .onAction(() -> runRuntimeIssueRetry(issue.retryAction()))
                    .show();
        };

        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    /**
     * Logs chat runtime issues and performs silent automatic retries without
     * showing popup UI.
     *
     * @param issue runtime issue originating from messaging/chat flows
     */
    private void handleMessagingRuntimeIssue(RuntimeIssue issue) {
        LOGGER.warn("Chat runtime issue: key={}, message={}", issue.dedupeKey(), issue.message());

        if (!shouldAutoRetryMessagingIssue(issue)) {
            return;
        }

        String autoRetryKey = issue.dedupeKey() + ".auto-retry";
        if (!chatAutoRetryGate.shouldShow(autoRetryKey)) {
            LOGGER.debug( "Skipping duplicate chat auto-retry for key: {}", issue.dedupeKey());
            return;
        }

        CompletableFuture.runAsync(() -> runRuntimeIssueRetry(issue.retryAction()));
    }

    /**
     * Checks whether the provided issue originates from chat messaging workflows.
     *
     * @param issue runtime issue candidate
     * @return {@code true} when issue key belongs to messaging namespace
     */
    static boolean isMessagingRuntimeIssue(RuntimeIssue issue) {
        if (issue == null || issue.dedupeKey() == null) {
            return false;
        }
        return issue.dedupeKey().startsWith("messaging.");
    }

    /**
     * Determines whether a chat runtime issue should trigger automatic retry.
     *
     * @param issue runtime issue candidate
     * @return {@code true} when automatic retry should run
     */
    static boolean shouldAutoRetryMessagingIssue(RuntimeIssue issue) {
        return isMessagingRuntimeIssue(issue) && !"messaging.retry.failed".equals(issue.dedupeKey());
    }

    /**
     * Executes runtime issue retry callback with guarded error handling.
     *
     * @param retryAction retry callback
     */
    private void runRuntimeIssueRetry(Runnable retryAction) {
        if (retryAction == null) {
            return;
        }
        try {
            retryAction.run();
        } catch (Exception ex) {
            LOGGER.warn( "Runtime issue retry action failed", ex);
        }
    }

    /**
     * Registers the main presence listener bridge from session service to UI update
     * handler.
     */
    private void registerPresenceListener() {
        mainSessionService.registerPresenceListener(this::applyPresenceUpdate);
    }

    /**
     * Registers the main incoming-message listener bridge from session service to
     * unread update handler.
     */
    private void registerIncomingMessageListener() {
        mainSessionService.registerIncomingMessageListener(this::applyIncomingMessage);
    }

    /**
     * Starts chat message receiving if the chat session has been initialized.
     */
    private void startMessageReceiving() {
        MessagesViewModel chatViewModel = ChatSession.get();
        if (chatViewModel == null) {
            LOGGER.warn("Cannot start message receiving: chat session is not initialized.");
            return;
        }
        // WebSocket connect can block briefly; start it off the JavaFX thread.
        CompletableFuture.runAsync(chatViewModel::startReceiving);
    }

    /**
     * Schedules a presence update to run on the JavaFX application thread.
     *
     * @param userId user id whose presence changed
     * @param active latest activity flag
     */
    private void applyPresenceUpdate(String userId, boolean active) {
        Platform.runLater(() -> updateContactPresence(userId, active));
    }

    /**
     * Updates unread counters in response to an incoming message event.
     *
     * @param senderId sender/contact id
     * @param message  incoming message payload (unused here but included by
     *                 listener contract)
     */
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

    /**
     * Applies a contact presence change to the list and profile-strip state.
     *
     * @param userId user id whose presence changed
     * @param active latest activity flag
     */
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

    /**
     * Applies unread-count update logic for a sender against the currently active
     * chat recipient.
     *
     * @param senderId sender/contact id of the incoming message
     */
    private void updateUnreadForIncomingMessage(String senderId) {
        String activeChatRecipientId = contentLoader == null ? null : contentLoader.getCurrentChatRecipientId();
        viewModel.applyIncomingMessage(senderId, activeChatRecipientId);
    }
}
