package com.haf.client.controllers;

import com.haf.client.core.ChatSession;
import com.haf.client.core.CurrentUserSession;
import com.haf.client.core.Launcher;
import com.haf.client.core.AuthSessionState;
import com.haf.client.core.NetworkSession;
import com.haf.client.models.UserProfileInfo;
import com.haf.client.models.MessageVM;
import com.haf.client.models.MessageType;
import com.haf.client.services.DefaultMainSessionService;
import com.haf.client.services.DefaultTokenRefreshService;
import com.haf.client.services.DesktopNotificationService;
import com.haf.client.services.MainSessionService;
import com.haf.client.services.TokenRefreshService;
import com.haf.client.models.ContactInfo;
import com.haf.client.utils.ClientSettings;
import com.haf.client.utils.ClientRuntimeConfig;
import com.haf.client.builders.ContextMenuBuilder;
import com.haf.client.builders.PopupMessageBuilder;
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
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Locale;
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
    private static final String HIDDEN_ACTIVITY_LABEL = "Hidden Activity";
    private static final String POPUP_SESSION_REVOKED = "popup-session-revoked";
    private static final String POPUP_SESSION_TAKEOVER = "popup-session-takeover";
    private static final String POPUP_UNDECRYPTABLE_MESSAGES = "popup-undecryptable-messages";
    private static final String SESSION_TAKEOVER_ISSUE_KEY = "messaging.session.takeover";
    private static final String UNDECRYPTABLE_MESSAGES_ISSUE_KEY = "messaging.undecryptable.envelopes";
    private static final String POPUP_SESSION_REFRESH_FAILED = "popup-session-refresh-failed";
    private static final String SESSION_EXPIRED_LABEL = "Session expired";
    private static final long TOKEN_REFRESH_LEEWAY_SECONDS = 60L;
    private static final long TOKEN_REFRESH_RETRY_SECONDS = 30L;
    private static final DateTimeFormatter SESSION_EXPIRY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter SESSION_EXPIRY_DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("dd MMM HH:mm");
    private static final DesktopNotificationService DEFAULT_DESKTOP_NOTIFICATION_SERVICE = new DesktopNotificationService();

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
    @FXML
    private Text sessionExpiryTitleText;

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
    private ContextMenu dotsMenu;
    private EventHandler<MouseEvent> contactContextOutsideClickHandler;

    /** Tracks whether results are currently displayed (for clear/search toggle). */
    private final MainViewModel viewModel = MainViewModel.createDefault();
    private final RuntimeIssuePopupGate runtimeIssuePopupGate = new RuntimeIssuePopupGate(
            RUNTIME_ISSUE_POPUP_COOLDOWN_MS);
    private final RuntimeIssuePopupGate chatAutoRetryGate = new RuntimeIssuePopupGate(
            CHAT_AUTO_RETRY_COOLDOWN_MS);
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    private final AtomicBoolean revokedSessionHandlingInProgress = new AtomicBoolean(false);
    private final AtomicBoolean logoutInProgress = new AtomicBoolean(false);
    private final AtomicBoolean undecryptableMessagesPopupShown = new AtomicBoolean(false);
    private final MainSessionService mainSessionService;
    private final DesktopNotificationService desktopNotificationService;
    private final TokenRefreshService tokenRefreshService;
    private final ScheduledExecutorService tokenRefreshScheduler;
    private final Object tokenRefreshLock = new Object();
    private final ClientSettings settings = ClientSettings.forCurrentUserOrDefaults();
    private final AtomicBoolean autoRefreshTokenEnabled = new AtomicBoolean(true);
    private final AtomicBoolean tokenRefreshInFlight = new AtomicBoolean(false);
    private final AtomicBoolean sessionExpired = new AtomicBoolean(false);
    private PauseTransition instantSearchDebounce;
    private final GaussianBlur privacyBlurEffect = new GaussianBlur();
    private boolean startupBlurLocked;
    private boolean startupBlurPopupQueued;
    private ScheduledFuture<?> scheduledTokenRefresh;
    private ScheduledFuture<?> scheduledSessionExpiryIndicatorUpdate;
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
        this(new DefaultMainSessionService(),
                DEFAULT_DESKTOP_NOTIFICATION_SERVICE,
                new DefaultTokenRefreshService(),
                createTokenRefreshScheduler());
    }

    /**
     * Creates the controller with an explicit session service dependency.
     *
     * @param mainSessionService service responsible for logout and event listener
     *                           wiring
     * @throws NullPointerException when {@code mainSessionService} is {@code null}
     */
    MainController(MainSessionService mainSessionService) {
        this(mainSessionService,
                DEFAULT_DESKTOP_NOTIFICATION_SERVICE,
                new DefaultTokenRefreshService(),
                createTokenRefreshScheduler());
    }

    /**
     * Creates the controller with explicit dependencies.
     *
     * @param mainSessionService         service responsible for logout and
     *                                   listener wiring
     * @param desktopNotificationService service used for OS-native notification
     *                                   display
     */
    MainController(MainSessionService mainSessionService, DesktopNotificationService desktopNotificationService) {
        this(mainSessionService,
                desktopNotificationService,
                new DefaultTokenRefreshService(),
                createTokenRefreshScheduler());
    }

    /**
     * Creates the controller with explicit dependencies including token-refresh
     * runtime components.
     *
     * @param mainSessionService         service responsible for logout and listener
     *                                   wiring
     * @param desktopNotificationService service used for OS-native notifications
     * @param tokenRefreshService        service used for refresh-token rotation
     * @param tokenRefreshScheduler      scheduler used for delayed refresh
     *                                   execution
     * @throws NullPointerException when any dependency is {@code null}
     */
    MainController(
            MainSessionService mainSessionService,
            DesktopNotificationService desktopNotificationService,
            TokenRefreshService tokenRefreshService,
            ScheduledExecutorService tokenRefreshScheduler) {
        this.mainSessionService = Objects.requireNonNull(mainSessionService, "mainSessionService");
        this.desktopNotificationService = Objects.requireNonNull(
                desktopNotificationService,
                "desktopNotificationService");
        this.tokenRefreshService = Objects.requireNonNull(tokenRefreshService, "tokenRefreshService");
        this.tokenRefreshScheduler = Objects.requireNonNull(tokenRefreshScheduler, "tokenRefreshScheduler");
    }

    /**
     * Creates single-threaded scheduler used for token refresh background tasks.
     *
     * @return daemon single-threaded scheduled executor
     */
    private static ScheduledExecutorService createTokenRefreshScheduler() {
        AtomicInteger threadCounter = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("haf-token-refresh-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    /**
     * Initializes main view bindings, UI interactions, listeners, and initial
     * content state.
     */
    @FXML
    public void initialize() {
        hideMainStageUntilInitialLoadCompletes();
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

        beginInitialMainLoadPipeline();
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
        // Enter key can be policy-gated by settings; icon button remains available.
        toolbarSearchField.setOnAction(e -> {
            if (settings.isSearchRequireEnterToSearch()) {
                triggerSearchFlow();
            }
        });

        setupSearchActionButton();

        toolbarSearchField.textProperty()
                .addListener((obs, oldValue, newValue) -> handleSearchFieldTextChanged(newValue));
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
        if (settings.isSearchRequireEnterToSearch()
                || !settings.isSearchInstantOnType()
                || viewModel.activeTabProperty().get() != MainViewModel.MainTab.SEARCH) {
            PauseTransition debounce = instantSearchDebounce;
            if (debounce != null) {
                debounce.stop();
            }
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
        if (settings.isSearchRequireEnterToSearch()
                || !settings.isSearchInstantOnType()
                || viewModel.activeTabProperty().get() != MainViewModel.MainTab.SEARCH) {
            return;
        }
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            clearSearch();
            return;
        }
        if (normalized.length() < settings.getSearchMinimumQueryLength()) {
            return;
        }
        triggerSearchFlow();
    }

    /**
     * Returns the shared debounce timer used for instant search scheduling.
     *
     * @return lazily initialized debounce timer instance
     */
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
        applySearchSortMemorySettings();

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
        String query = toolbarSearchField == null ? null : toolbarSearchField.getText();
        if (isSearchQueryTooShort(query)) {
            return;
        }
        searchFilterUi.onSearchTrigger(
                query,
                filterButton != null ? filterButton : searchActionButton);
    }

    /**
     * Checks whether a query is non-empty and shorter than the configured minimum
     * search length.
     *
     * @param query search query to validate
     * @return {@code true} when query should be treated as too short
     */
    private boolean isSearchQueryTooShort(String query) {
        String normalized = query == null ? "" : query.trim();
        return !normalized.isBlank() && normalized.length() < settings.getSearchMinimumQueryLength();
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
        if (settings.isSearchRememberSortOptions()) {
            settings.setSearchSortOptions(sortOptions);
        }
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
        String resolvedActivenessLabel = settings.isPrivacyHidePresenceIndicators()
                ? HIDDEN_ACTIVITY_LABEL
                : activenessLabel;
        profileActivenessText.setText(resolvedActivenessLabel);
        boolean hasActivenessLabel = !resolvedActivenessLabel.isEmpty();
        profileActivenessText.setVisible(hasActivenessLabel);
        profileActivenessText.setManaged(hasActivenessLabel);
        boolean showActivenessCircle = hasActivenessLabel && !settings.isPrivacyHidePresenceIndicators();
        profileActivenessCircle.setVisible(showActivenessCircle);
        profileActivenessCircle.setManaged(showActivenessCircle);
        if (showActivenessCircle) {
            try {
                profileActivenessCircle.setFill(Color.web(contact.activenessColor()));
            } catch (IllegalArgumentException _) {
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
     * Starts initial main-screen loading work and reveals the main stage only after
     * preload completion.
     */
    private void beginInitialMainLoadPipeline() {
        CompletableFuture<Void> preloadViewsFuture = contentLoader.preloadAllViewsAsync();
        CompletableFuture<Void> fetchContactsFuture = viewModel.fetchContactsAsync();
        CompletableFuture<Void> preloadSettingsPopupFuture = preloadSettingsPopupAsync();
        CompletableFuture<Void> activateInitialTabFuture = preloadViewsFuture
                .handle((unused, throwable) -> null)
                .thenCompose(ignored -> runOnFxThreadAsync(this::activateInitialTabForStartup));

        CompletableFuture
                .allOf(preloadViewsFuture, fetchContactsFuture, preloadSettingsPopupFuture, activateInitialTabFuture)
                .whenComplete((unused, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        LOGGER.warn(
                                "Main initial load completed with failures; revealing UI.",
                                throwable);
                    }
                    if (startupBlurLocked) {
                        scheduleStartupBlurUnlockPopupAfterMainRender();
                    }
                    revealMainStageAfterInitialLoad();
                }));
    }

    /**
     * Activates the initial tab after preloaded views are ready so first reveal
     * shows fully initialized content.
     */
    private void activateInitialTabForStartup() {
        if (settings.isGeneralRestoreLastTab() && "search".equals(settings.getLastActiveTab())) {
            activateSearchTab();
            return;
        }
        activateMessagesTab();
    }

    /**
     * Runs an action on the JavaFX thread and completes once the action has
     * finished.
     *
     * @param action UI action to execute
     * @return future that completes when action execution completes
     */
    private static CompletableFuture<Void> runOnFxThreadAsync(Runnable action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable wrapped = () -> {
            try {
                action.run();
                future.complete(null);
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        };
        if (Platform.isFxApplicationThread()) {
            wrapped.run();
        } else {
            Platform.runLater(wrapped);
        }
        return future;
    }

    /**
     * Preloads the Settings popup and completes regardless of preload outcome.
     *
     * @return completion future for settings-popup preload
     */
    private CompletableFuture<Void> preloadSettingsPopupAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
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
                LOGGER.debug("Settings popup preload skipped: {}", ex.getMessage());
            } finally {
                future.complete(null);
            }
        });
        return future;
    }

    /**
     * Makes the main stage transparent until initial preload work finishes.
     */
    private void hideMainStageUntilInitialLoadCompletes() {
        if (rootContainer != null) {
            rootContainer.setVisible(false);
        }
        Stage stage = ViewRouter.getMainStage();
        if (stage != null) {
            stage.setOpacity(0.0);
        }
    }

    /**
     * Reveals the main stage after initial preload work has completed.
     */
    private void revealMainStageAfterInitialLoad() {
        if (rootContainer != null) {
            rootContainer.setVisible(true);
        }
        Stage stage = ViewRouter.getMainStage();
        if (stage != null) {
            stage.setOpacity(1.0);
        }
    }

    /**
     * Configures contact list cells and wires click/context-menu callbacks for each
     * row.
     */
    private void setupContactList() {
        contactsList.setCellFactory(lv -> {
            ContactCellController cell = new ContactCellController();
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

        stage.focusedProperty()
                .addListener((obs, oldFocused, newFocused) -> applyPrivacyBlur(Boolean.TRUE.equals(newFocused)));
        applyPrivacyBlur(stage.isFocused());
    }

    /**
     * Applies immediate settings.
     */
    private void applyImmediateSettings() {
        applySearchFilterSettings();
        applySearchSortMemorySettings();
        applyContactCellSettings();
        applySessionTokenSettings();
        syncStartupBlurLockFromSetting(false);
        applyPrivacyBlur(ViewRouter.getMainStage() == null || ViewRouter.getMainStage().isFocused());
    }

    /**
     * Registers settings listener.
     */
    private void registerSettingsListener() {
        settings.addListener(key -> Platform.runLater(() -> {
            switch (key) {
                case SEARCH_AUTO_OPEN_FILTER_ON_FIRST_SEARCH -> applySearchFilterSettings();
                case SEARCH_REMEMBER_SORT_OPTIONS -> applySearchSortMemorySettings();
                case NOTIFICATIONS_SHOW_UNREAD_BADGES, NOTIFICATIONS_BADGE_CAP ->
                    applyContactCellSettings();
                case PRIVACY_HIDE_PRESENCE_INDICATORS -> applyContactCellSettings();
                case PRIVACY_BLUR_ON_FOCUS_LOSS, PRIVACY_BLUR_STRENGTH -> {
                    Stage stage = ViewRouter.getMainStage();
                    applyPrivacyBlur(stage == null || stage.isFocused());
                }
                case PRIVACY_BLUR_ON_STARTUP_UNTIL_UNLOCK -> {
                    syncStartupBlurLockFromSetting();
                    Stage stage = ViewRouter.getMainStage();
                    applyPrivacyBlur(stage == null || stage.isFocused());
                }
                case ACCOUNT_AUTO_REFRESH_TOKEN -> applySessionTokenSettings();
                default -> {
                    // Other settings are read lazily by their owning controllers.
                }
            }
        }));
    }

    /**
     * Applies session-token related settings: auto-refresh policy and optional
     * title
     * expiry indicator.
     */
    private void applySessionTokenSettings() {
        boolean enabled = settings.isAccountAutoRefreshToken();
        autoRefreshTokenEnabled.set(enabled);
        cancelScheduledTokenRefresh();
        sessionExpired.set(isSessionExpiredByTimestamp(AuthSessionState.getAccessExpiresAtEpochSeconds()));
        updateSessionExpiryIndicator();
        if (enabled) {
            scheduleNextTokenRefreshFromSessionState();
            return;
        }
        scheduleSessionExpiryIndicatorUpdateFromSessionState();
    }

    /**
     * Schedules the next token-refresh attempt using current auth-session metadata.
     */
    private void scheduleNextTokenRefreshFromSessionState() {
        if (!autoRefreshTokenEnabled.get()) {
            return;
        }
        AuthSessionState.Snapshot snapshot = AuthSessionState.get();
        if (snapshot == null || snapshot.refreshToken() == null || snapshot.refreshToken().isBlank()) {
            return;
        }
        scheduleTokenRefresh(resolveTokenRefreshDelaySeconds(snapshot.accessExpiresAtEpochSeconds()));
    }

    /**
     * Computes refresh delay so rotation happens shortly before access-token
     * expiry.
     *
     * @param accessExpiresAtEpochSeconds access-token expiry epoch seconds
     * @return delay in seconds before refresh should run
     */
    private long resolveTokenRefreshDelaySeconds(Long accessExpiresAtEpochSeconds) {
        if (accessExpiresAtEpochSeconds == null || accessExpiresAtEpochSeconds <= 0L) {
            return TOKEN_REFRESH_RETRY_SECONDS;
        }
        long now = Instant.now().getEpochSecond();
        return Math.max(1L, accessExpiresAtEpochSeconds - now - TOKEN_REFRESH_LEEWAY_SECONDS);
    }

    /**
     * Checks whether a session expiry timestamp is already in the past.
     *
     * @param accessExpiresAtEpochSeconds access-token expiry epoch seconds
     * @return {@code true} when expiry is missing or already reached
     */
    private static boolean isSessionExpiredByTimestamp(Long accessExpiresAtEpochSeconds) {
        if (accessExpiresAtEpochSeconds == null || accessExpiresAtEpochSeconds <= 0L) {
            return false;
        }
        return accessExpiresAtEpochSeconds.longValue() <= Instant.now().getEpochSecond();
    }

    /**
     * Schedules title-bar expiry label update and expiry popup for the exact access
     * token expiry instant while auto-refresh is disabled.
     */
    private void scheduleSessionExpiryIndicatorUpdateFromSessionState() {
        if (autoRefreshTokenEnabled.get()) {
            return;
        }
        Long accessExpiresAtEpochSeconds = AuthSessionState.getAccessExpiresAtEpochSeconds();
        if (accessExpiresAtEpochSeconds == null || accessExpiresAtEpochSeconds <= 0L) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        long delaySeconds = accessExpiresAtEpochSeconds.longValue() - now;
        if (delaySeconds <= 0L) {
            markSessionExpiredFromTimeout();
            return;
        }
        synchronized (tokenRefreshLock) {
            cancelScheduledSessionExpiryIndicatorUpdateLocked();
            scheduledSessionExpiryIndicatorUpdate = tokenRefreshScheduler.schedule(
                    this::markSessionExpiredFromTimeout,
                    delaySeconds,
                    TimeUnit.SECONDS);
        }
    }

    /**
     * Marks session expired when expiry timeout is reached and surfaces session-end
     * handling on the UI thread.
     */
    private void markSessionExpiredFromTimeout() {
        if (!sessionExpired.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> {
            updateSessionExpiryIndicator();
            handleRuntimeIssue(new RuntimeIssue(
                    "messaging.session.revoked",
                    "Session Expired",
                    "Your session has expired. You can try refreshing once or log in again.",
                    () -> {
                    }));
        });
    }

    /**
     * Schedules token refresh execution after a delay, replacing any previously
     * scheduled task.
     *
     * @param delaySeconds refresh delay in seconds
     */
    private void scheduleTokenRefresh(long delaySeconds) {
        if (tokenRefreshScheduler.isShutdown()) {
            return;
        }
        synchronized (tokenRefreshLock) {
            cancelScheduledTokenRefreshLocked();
            scheduledTokenRefresh = tokenRefreshScheduler.schedule(
                    this::runTokenRefreshCycle,
                    delaySeconds,
                    TimeUnit.SECONDS);
        }
    }

    /**
     * Runs one refresh cycle and schedules follow-up work depending on result.
     */
    private void runTokenRefreshCycle() {
        if (!autoRefreshTokenEnabled.get()) {
            return;
        }
        if (!tokenRefreshInFlight.compareAndSet(false, true)) {
            return;
        }

        try {
            AuthSessionState.Snapshot snapshot = AuthSessionState.get();
            if (snapshot == null || snapshot.refreshToken() == null || snapshot.refreshToken().isBlank()) {
                return;
            }

            TokenRefreshService.TokenRefreshResult result = tokenRefreshService.refresh(snapshot.refreshToken());
            if (result.success()) {
                applySuccessfulTokenRefresh(result);
                return;
            }

            if (result.invalidSession()) {
                Platform.runLater(() -> handleRuntimeIssue(resolveInvalidSessionRuntimeIssue(result.errorMessage())));
                return;
            }

            LOGGER.warn(
                    "Token refresh failed; scheduling retry in {}s (reason={})",
                    TOKEN_REFRESH_RETRY_SECONDS,
                    sanitizeRefreshFailureForLog(result.errorMessage()));
            if (autoRefreshTokenEnabled.get()) {
                scheduleTokenRefresh(TOKEN_REFRESH_RETRY_SECONDS);
            }
        } finally {
            tokenRefreshInFlight.set(false);
        }
    }

    /**
     * Updates title-bar session expiry indicator visibility and label text based on
     * account token-refresh setting.
     */
    private void updateSessionExpiryIndicator() {
        if (sessionExpiryTitleText == null) {
            return;
        }
        if (autoRefreshTokenEnabled.get()) {
            sessionExpiryTitleText.setVisible(false);
            sessionExpiryTitleText.setManaged(false);
            sessionExpiryTitleText.setText("");
            return;
        }

        Long accessExpiresAtEpochSeconds = AuthSessionState.getAccessExpiresAtEpochSeconds();
        boolean expiredNow = sessionExpired.get() || isSessionExpiredByTimestamp(accessExpiresAtEpochSeconds);
        if (expiredNow) {
            sessionExpired.set(true);
        }
        String label;
        if (expiredNow) {
            label = SESSION_EXPIRED_LABEL;
        } else if (accessExpiresAtEpochSeconds == null || accessExpiresAtEpochSeconds <= 0L) {
            label = "Session expires soon";
        } else {
            label = "Session expires at " + formatSessionExpiry(accessExpiresAtEpochSeconds.longValue());
        }
        sessionExpiryTitleText.setText(label);
        sessionExpiryTitleText.setVisible(true);
        sessionExpiryTitleText.setManaged(true);
    }

    /**
     * Applies successful token refresh result to local auth state and scheduling.
     *
     * @param result successful refresh result
     */
    private void applySuccessfulTokenRefresh(TokenRefreshService.TokenRefreshResult result) {
        boolean recoveredExpiredSession = sessionExpired.get();
        AuthSessionState.set(
                result.accessToken(),
                result.refreshToken(),
                result.accessExpiresAtEpochSeconds(),
                result.refreshExpiresAtEpochSeconds());
        sessionExpired.set(false);

        var adapter = NetworkSession.get();
        if (adapter != null) {
            adapter.updateAccessToken(result.accessToken());
        }
        if (recoveredExpiredSession) {
            restoreMessagingTransportAfterSessionRefresh();
        }

        Platform.runLater(this::updateSessionExpiryIndicator);
        if (autoRefreshTokenEnabled.get()) {
            scheduleNextTokenRefreshFromSessionState();
            return;
        }
        scheduleSessionExpiryIndicatorUpdateFromSessionState();
    }

    /**
     * Restores receiver transport after a session-expired flow recovers via token
     * refresh.
     */
    private void restoreMessagingTransportAfterSessionRefresh() {
        MessagesViewModel chatViewModel = ChatSession.get();
        if (chatViewModel == null) {
            return;
        }
        chatViewModel.restoreReceiverTransportAfterSessionRefresh();
    }

    /**
     * Formats session expiry for title-bar display.
     *
     * @param epochSeconds expiry epoch seconds
     * @return formatted local-time label
     */
    private static String formatSessionExpiry(long epochSeconds) {
        ZoneId zone = ZoneId.systemDefault();
        var dateTime = Instant.ofEpochSecond(epochSeconds).atZone(zone);
        if (dateTime.toLocalDate().equals(LocalDate.now(zone))) {
            return dateTime.format(SESSION_EXPIRY_TIME_FORMATTER);
        }
        return dateTime.format(SESSION_EXPIRY_DATE_TIME_FORMATTER);
    }

    /**
     * Cancels any scheduled token-refresh task.
     */
    private void cancelScheduledTokenRefresh() {
        synchronized (tokenRefreshLock) {
            cancelScheduledTokenRefreshLocked();
        }
    }

    /**
     * Cancels scheduled token-refresh and expiry-indicator tasks while caller
     * already owns refresh lock.
     */
    private void cancelScheduledTokenRefreshLocked() {
        if (scheduledTokenRefresh == null) {
            cancelScheduledSessionExpiryIndicatorUpdateLocked();
            return;
        }
        scheduledTokenRefresh.cancel(false);
        scheduledTokenRefresh = null;
        cancelScheduledSessionExpiryIndicatorUpdateLocked();
    }

    /**
     * Cancels scheduled session-expiry indicator update task while caller already
     * owns refresh lock.
     */
    private void cancelScheduledSessionExpiryIndicatorUpdateLocked() {
        if (scheduledSessionExpiryIndicatorUpdate == null) {
            return;
        }
        scheduledSessionExpiryIndicatorUpdate.cancel(false);
        scheduledSessionExpiryIndicatorUpdate = null;
    }

    /**
     * Stops token-refresh scheduling and shuts down refresh executor.
     */
    private void shutdownTokenRefreshFlow() {
        autoRefreshTokenEnabled.set(false);
        cancelScheduledTokenRefresh();
        tokenRefreshScheduler.shutdownNow();
    }

    /**
     * Applies search filter settings.
     */
    private void applySearchFilterSettings() {
        if (searchFilterUi != null) {
            searchFilterUi.setAutoOpenFilterOnFirstSearch(settings.isSearchAutoOpenFilterOnFirstSearch());
        }
    }

    /**
     * Applies search sort memory settings.
     */
    private void applySearchSortMemorySettings() {
        if (searchFilterUi == null) {
            return;
        }
        if (!settings.isSearchRememberSortOptions()) {
            settings.clearSearchSortOptions();
            searchFilterUi.setSelectedSortOptions(SearchSortViewModel.SortOptions.DEFAULT);
            return;
        }
        searchFilterUi.setSelectedSortOptions(settings.getSearchSortOptions());
    }

    /**
     * Applies contact cell settings.
     */
    private void applyContactCellSettings() {
        ContactCellController.setShowUnreadBadges(settings.isNotificationsShowUnreadBadges());
        ContactCellController.setUnreadBadgeCap(settings.getNotificationsBadgeCap());
        ContactCellController.setHidePresenceIndicators(settings.isPrivacyHidePresenceIndicators());
        if (contactsList != null) {
            contactsList.refresh();
        }
        refreshProfilePanelForSelectedContact();
    }

    /**
     * Applies or clears the privacy blur effect based on focus and lock settings.
     *
     * @param focused whether the main stage is currently focused
     */
    private void applyPrivacyBlur(boolean focused) {
        if (rootContainer == null) {
            return;
        }
        if (startupBlurLocked) {
            privacyBlurEffect.setRadius(Math.max(1.0, settings.getPrivacyBlurStrength() * 2.0));
            rootContainer.setEffect(privacyBlurEffect);
            return;
        }
        if (!focused && settings.isPrivacyBlurOnFocusLoss()) {
            privacyBlurEffect.setRadius(Math.max(1.0, settings.getPrivacyBlurStrength() * 2.0));
            rootContainer.setEffect(privacyBlurEffect);
            return;
        }
        rootContainer.setEffect(null);
    }

    /**
     * Handles sync startup blur lock from setting.
     */
    private void syncStartupBlurLockFromSetting() {
        syncStartupBlurLockFromSetting(true);
    }

    /**
     * Applies startup blur-lock settings and optionally schedules the unlock popup.
     *
     * @param scheduleUnlockPopup {@code true} to queue unlock popup after initial
     *                            render
     */
    private void syncStartupBlurLockFromSetting(boolean scheduleUnlockPopup) {
        if (!settings.isPrivacyBlurOnStartupUntilUnlock()) {
            startupBlurLocked = false;
            startupBlurPopupQueued = false;
            return;
        }

        startupBlurLocked = true;
        if (scheduleUnlockPopup) {
            scheduleStartupBlurUnlockPopupAfterMainRender();
        }
    }

    /**
     * Handles lock startup privacy blur.
     */
    private void lockStartupPrivacyBlur() {
        if (startupBlurLocked) {
            showStartupBlurUnlockPopup();
            return;
        }
        startupBlurLocked = true;
        Stage stage = ViewRouter.getMainStage();
        applyPrivacyBlur(stage == null || stage.isFocused());
        showStartupBlurUnlockPopup();
    }

    /**
     * Handles unlock startup privacy blur.
     */
    private void unlockStartupPrivacyBlur() {
        startupBlurLocked = false;
        Stage stage = ViewRouter.getMainStage();
        applyPrivacyBlur(stage == null || stage.isFocused());
    }

    /**
     * Handles schedule startup blur unlock popup after main render.
     */
    private void scheduleStartupBlurUnlockPopupAfterMainRender() {
        if (!startupBlurLocked || startupBlurPopupQueued) {
            return;
        }
        startupBlurPopupQueued = true;
        Platform.runLater(() -> Platform.runLater(() -> {
            startupBlurPopupQueued = false;
            if (!startupBlurLocked) {
                return;
            }
            showStartupBlurUnlockPopup();
        }));
    }

    /**
     * Shows startup blur unlock popup.
     */
    private void showStartupBlurUnlockPopup() {
        PopupMessageBuilder.create()
                .popupKey("popup-privacy-startup-blur-unlock")
                .title("Privacy lock enabled")
                .message("Sensitive content is blurred. Select Unlock to continue.")
                .actionText("Unlock")
                .singleAction(true)
                .movable(false)
                .onAction(this::unlockStartupPrivacyBlur)
                .show();
    }

    /**
     * Restores persisted window bounds and maximized state when available.
     *
     * @param stage primary stage to restore
     */
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

    /**
     * Persists current window bounds and maximized state when enabled.
     *
     * @param stage primary stage to persist
     */
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

        dotsMenuButton.setOnAction(e -> {
            if (dotsMenu != null && dotsMenu.isShowing()) {
                dotsMenu.hide();
                return;
            }
            dotsMenu = buildDotsMenu();
            showDotsMenuAnchored(dotsMenu);
        });
    }

    /**
     * Builds the overflow context menu shown from the top-right dots button.
     *
     * @return configured dots-menu context menu
     */
    private ContextMenu buildDotsMenu() {
        boolean blurLocked = startupBlurLocked;
        String blurActionIcon = blurLocked ? "mdi2l-lock-open-variant-outline" : "mdi2l-lock-outline";
        String blurActionText = blurLocked ? "Unlock Privacy Blur" : "Lock Privacy Blur";
        Runnable blurAction = blurLocked ? this::unlockStartupPrivacyBlur : this::lockStartupPrivacyBlur;

        return ContextMenuBuilder.create()
                .addOption("mdi2a-account-circle-outline", "Profile", this::openSelfProfilePopup)
                .addSeparator()
                .addOption("mdi2c-cog-outline", "Settings", this::openSettingsPopup)
                .addOption(blurActionIcon, blurActionText, blurAction)
                .addOption("mdi2h-help-circle-outline", "Help", this::openHelpCenter)
                .addSeparator()
                .addOption("mdi2l-logout", "Log out", this::confirmLogout)
                .build();
    }

    /**
     * Opens help center.
     */
    private void openHelpCenter() {
        String helpCenterUrl = ClientRuntimeConfig.load().helpCenterBaseUri().toString();
        requestExternalLinkOpen(helpCenterUrl);
    }

    /**
     * Requests opening an external URL, optionally showing a confirmation popup.
     *
     * @param url URL to open
     */
    private void requestExternalLinkOpen(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (!settings.isPrivacyConfirmExternalLinkOpen()) {
            openExternalLink(url);
            return;
        }
        PopupMessageBuilder.create()
                .popupKey("popup-confirm-open-external-link")
                .title("Open external link")
                .message("Open this link in your browser?\n" + url)
                .actionText("Open")
                .cancelText("Cancel")
                .onAction(() -> openExternalLink(url))
                .show();
    }

    /**
     * Opens an external URL in the system browser and shows a fallback popup on
     * failure.
     *
     * @param url URL to open
     */
    private void openExternalLink(String url) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                showExternalLinkOpenFailedPopup();
                return;
            }
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception ex) {
            LOGGER.warn("Failed to open external link: {}", url, ex);
            showExternalLinkOpenFailedPopup();
        }
    }

    /**
     * Shows external link open failed popup.
     */
    private void showExternalLinkOpenFailedPopup() {
        PopupMessageBuilder.create()
                .popupKey("popup-open-external-link-failed")
                .title("Could not open link")
                .message("Your system browser could not be opened for this link.")
                .actionText("OK")
                .singleAction(true)
                .show();
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
        logoutInProgress.set(true);
        shutdownTokenRefreshFlow();
        persistWindowState(ViewRouter.getMainStage());
        mainSessionService.logout().whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.warn("Logout completed with errors", throwable);
            }
            Platform.runLater(() -> ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN));
        });
    }

    /**
     * Requests app restart.
     */
    private void requestAppRestart() {
        if (!shutdownInProgress.compareAndSet(false, true)) {
            return;
        }
        logoutInProgress.set(true);
        shutdownTokenRefreshFlow();
        persistWindowState(ViewRouter.getMainStage());

        mainSessionService.logout().whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.warn("Logout before restart completed with errors.", throwable);
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

    /**
     * Launches a new client JVM process using the current runtime and classpath.
     *
     * @return {@code true} when process start succeeds, otherwise {@code false}
     */
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
            LOGGER.warn("Failed to relaunch client process.", ex);
            return false;
        }
    }

    /**
     * Shows restart failure popup.
     */
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
        if (!settings.isGeneralConfirmDeleteChat()) {
            clearLocalChatHistory(target == null ? null : target.id());
            return;
        }
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
        if (!settings.isGeneralConfirmRemoveContact()) {
            removeContact(target.id());
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
        if (!settings.isGeneralConfirmLogout()) {
            handleLogout();
            return;
        }
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
        shutdownTokenRefreshFlow();
        persistWindowState(ViewRouter.getMainStage());

        CompletableFuture<Void> logoutFuture;
        try {
            logoutFuture = mainSessionService.logout()
                    .orTimeout(LOGOUT_ON_EXIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            LOGGER.warn("Failed to start logout on app exit; continuing shutdown.", ex);
            logoutFuture = CompletableFuture.completedFuture(null);
        }

        logoutFuture.whenComplete((unused, throwable) -> {
            if (throwable != null) {
                LOGGER.warn("Logout on app exit completed with errors.", throwable);
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
        Runnable task = () -> handleRuntimeIssueOnUiThread(issue);

        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }

    /**
     * Handles runtime issue after marshaling execution to the JavaFX application
     * thread.
     *
     * @param issue runtime issue to render/handle
     */
    private void handleRuntimeIssueOnUiThread(RuntimeIssue issue) {
        if (handleSpecialRuntimeIssue(issue)) {
            return;
        }
        if (!shouldShowGenericRuntimeIssuePopup(issue)) {
            return;
        }
        showGenericRuntimeIssuePopup(issue);
    }

    /**
     * Handles runtime issues with dedicated flows and returns whether the issue was
     * consumed.
     *
     * @param issue runtime issue candidate
     * @return {@code true} when issue has been fully handled
     */
    private boolean handleSpecialRuntimeIssue(RuntimeIssue issue) {
        if (isSessionTakeoverIssue(issue)) {
            if (shouldSuppressRevokedSessionPopup()) {
                LOGGER.debug("Suppressing takeover-session popup during local logout/shutdown flow.");
                return true;
            }
            handleSessionTakeoverIssue();
            return true;
        }
        if (isRevokedSessionIssue(issue)) {
            if (shouldSuppressRevokedSessionPopup()) {
                LOGGER.debug("Suppressing revoked-session popup during local logout/shutdown flow.");
                return true;
            }
            handleRevokedSessionIssue();
            return true;
        }
        if (isUndecryptableMessagesIssue(issue)) {
            handleUndecryptableMessagesIssue(issue);
            return true;
        }
        if (isMessagingRuntimeIssue(issue)) {
            handleMessagingRuntimeIssue(issue);
            return true;
        }
        return false;
    }

    /**
     * Checks whether generic runtime popup notifications are enabled and permitted
     * by
     * cooldown gate.
     *
     * @param issue runtime issue candidate
     * @return {@code true} when generic popup should be shown
     */
    private boolean shouldShowGenericRuntimeIssuePopup(RuntimeIssue issue) {
        if (!settings.isNotificationsShowRuntimePopups()) {
            return false;
        }
        return runtimeIssuePopupGate.shouldShow(issue.dedupeKey());
    }

    /**
     * Displays the generic retry/dismiss runtime popup.
     *
     * @param issue runtime issue to render
     */
    private void showGenericRuntimeIssuePopup(RuntimeIssue issue) {
        PopupMessageBuilder.create()
                .popupKey(UiConstants.POPUP_RUNTIME_ISSUE)
                .title(issue.title())
                .message(issue.message())
                .actionText("Retry")
                .cancelText("Dismiss")
                .showCancel(true)
                .onAction(() -> runRuntimeIssueRetry(issue.retryAction()))
                .show();
    }

    /**
     * Checks whether runtime issue indicates revoked/invalid authenticated session.
     *
     * @param issue runtime issue candidate
     * @return {@code true} when issue key marks revoked-session handling
     */
    private static boolean isRevokedSessionIssue(RuntimeIssue issue) {
        return issue != null && "messaging.session.revoked".equals(issue.dedupeKey());
    }

    /**
     * Checks whether runtime issue indicates forced logout due to login takeover.
     *
     * @param issue runtime issue candidate
     * @return {@code true} when issue key marks takeover-session handling
     */
    private static boolean isSessionTakeoverIssue(RuntimeIssue issue) {
        return issue != null && SESSION_TAKEOVER_ISSUE_KEY.equals(issue.dedupeKey());
    }

    /**
     * Checks whether runtime issue indicates skipped undecryptable encrypted
     * messages.
     *
     * @param issue runtime issue candidate
     * @return {@code true} when issue key marks undecryptable-message handling
     */
    private static boolean isUndecryptableMessagesIssue(RuntimeIssue issue) {
        return issue != null && UNDECRYPTABLE_MESSAGES_ISSUE_KEY.equals(issue.dedupeKey());
    }

    /**
     * Returns whether revoked-session popups should be suppressed because local
     * logout/restart/exit handling is already active.
     */
    private boolean shouldSuppressRevokedSessionPopup() {
        return logoutInProgress.get() || shutdownInProgress.get();
    }

    /**
     * Shows blocking revoked-session popup and routes the user to login after
     * confirmation.
     */
    private void handleRevokedSessionIssue() {
        if (!runtimeIssuePopupGate.shouldShow("messaging.session.revoked")) {
            return;
        }
        sessionExpired.set(true);
        cancelScheduledTokenRefresh();
        updateSessionExpiryIndicator();
        PopupMessageBuilder.create()
                .popupKey(POPUP_SESSION_REVOKED)
                .title("Session Expired")
                .message("Your session has expired. You can try refreshing once or log in again.")
                .actionText("Refresh Session")
                .cancelText("Log out")
                .showCancel(true)
                .movable(false)
                .onAction(this::attemptManualSessionRefresh)
                .onCancel(this::completeRevokedSessionLogout)
                .show();
    }

    /**
     * Shows blocking takeover-session popup and routes the user to login.
     */
    private void handleSessionTakeoverIssue() {
        if (!runtimeIssuePopupGate.shouldShow(SESSION_TAKEOVER_ISSUE_KEY)) {
            return;
        }
        sessionExpired.set(true);
        cancelScheduledTokenRefresh();
        updateSessionExpiryIndicator();
        PopupMessageBuilder.create()
                .popupKey(POPUP_SESSION_TAKEOVER)
                .title("Logged out")
                .message("A new device logged into this account. You have been logged out.")
                .actionText("Go to Login")
                .singleAction(true)
                .movable(false)
                .onAction(this::completeRevokedSessionLogout)
                .show();
    }

    /**
     * Attempts one manual refresh-token rotation after session-expired popup
     * confirmation.
     */
    private void attemptManualSessionRefresh() {
        if (!tokenRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                AuthSessionState.Snapshot snapshot = AuthSessionState.get();
                if (snapshot == null || snapshot.refreshToken() == null || snapshot.refreshToken().isBlank()) {
                    Platform.runLater(this::completeRevokedSessionLogout);
                    return;
                }

                TokenRefreshService.TokenRefreshResult result = tokenRefreshService.refresh(snapshot.refreshToken());
                if (result.success()) {
                    applySuccessfulTokenRefresh(result);
                    return;
                }
                if (result.invalidSession()) {
                    Platform.runLater(this::completeRevokedSessionLogout);
                    return;
                }
                LOGGER.warn(
                        "Manual session refresh failed (reason={})",
                        sanitizeRefreshFailureForLog(result.errorMessage()));
                String failureMessage = resolveManualRefreshFailureMessage(result.errorMessage());
                Platform.runLater(() -> showManualRefreshFailedPopup(failureMessage));
            } finally {
                tokenRefreshInFlight.set(false);
            }
        });
    }

    /**
     * Shows fallback popup when manual refresh attempt fails due to transient
     * transport/server errors.
     */
    private void showManualRefreshFailedPopup(String message) {
        PopupMessageBuilder.create()
                .popupKey(POPUP_SESSION_REFRESH_FAILED)
                .title("Refresh failed")
                .message(message)
                .actionText("Go to Login")
                .singleAction(true)
                .movable(false)
                .onAction(this::completeRevokedSessionLogout)
                .show();
    }

    /**
     * Resolves user-facing manual refresh failure text from refresh result errors.
     *
     * @param errorMessage refresh failure reason from refresh service
     * @return popup message text
     */
    static String resolveManualRefreshFailureMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank() || "token refresh failed".equalsIgnoreCase(errorMessage)) {
            return "Could not refresh your session. Please log in again.";
        }
        return "Could not refresh your session (" + errorMessage.trim() + "). Please log in again.";
    }

    /**
     * Maps invalid-session refresh failures to takeover-specific or generic session
     * runtime issues.
     *
     * @param errorMessage refresh failure reason
     * @return runtime issue payload to publish on UI thread
     */
    private static RuntimeIssue resolveInvalidSessionRuntimeIssue(String errorMessage) {
        if (isTakeoverSessionMessage(errorMessage)) {
            return new RuntimeIssue(
                    SESSION_TAKEOVER_ISSUE_KEY,
                    "Logged out",
                    "A new device logged into this account. You have been logged out.",
                    () -> {
                    });
        }
        return new RuntimeIssue(
                "messaging.session.revoked",
                "Session Expired",
                "Your session expired due to inactivity. Please log in again.",
                () -> {
                });
    }

    private static boolean isTakeoverSessionMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("session revoked by takeover")
                || normalized.contains("revoked by takeover");
    }

    /**
     * Resolves concise non-empty reason text for refresh-failure logging.
     *
     * @param errorMessage refresh failure reason
     * @return normalized log-safe reason text
     */
    private static String sanitizeRefreshFailureForLog(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return "unknown";
        }
        return errorMessage.trim();
    }

    /**
     * Shows a one-time informational popup when one or more envelopes cannot be
     * decrypted on this device.
     *
     * @param issue undecryptable-message runtime issue
     */
    private void handleUndecryptableMessagesIssue(RuntimeIssue issue) {
        if (!undecryptableMessagesPopupShown.compareAndSet(false, true)) {
            return;
        }
        PopupMessageBuilder.create()
                .popupKey(POPUP_UNDECRYPTABLE_MESSAGES)
                .title(issue.title())
                .message(issue.message())
                .actionText("Dismiss")
                .singleAction(true)
                .movable(false)
                .show();
    }

    /**
     * Performs one-time local logout cleanup and routes application to login view.
     */
    private void completeRevokedSessionLogout() {
        if (!revokedSessionHandlingInProgress.compareAndSet(false, true)) {
            return;
        }
        shutdownTokenRefreshFlow();
        mainSessionService.logout().whenComplete((unused, throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                LOGGER.info("Session revoke cleanup completed with non-fatal errors: {}", throwable.getMessage());
            }
            try {
                ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN);
            } catch (Exception ex) {
                LOGGER.error("Failed to route to login after revoked session cleanup", ex);
            } finally {
                revokedSessionHandlingInProgress.set(false);
            }
        }));
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
            LOGGER.debug("Skipping duplicate chat auto-retry for key: {}", issue.dedupeKey());
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
            LOGGER.warn("Runtime issue retry action failed", ex);
        }
    }

    /**
     * Registers the main presence listener bridge from session service to UI update
     * handler.
     */
    private void registerPresenceListener() {
        mainSessionService.registerPresenceListener(new MessagesViewModel.PresenceListener() {
            @Override
            public void onPresenceUpdate(String userId, boolean active) {
                applyPresenceUpdate(userId, active);
            }
        });
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
     * @param active visible activity flag
     */
    private void applyPresenceUpdate(String userId, boolean active) {
        Platform.runLater(() -> updateContactPresence(userId, active));
    }

    /**
     * Updates unread counters in response to an incoming message event.
     *
     * @param senderId sender/contact id
     * @param message  incoming message payload used for OS notification text
     */
    private void applyIncomingMessage(String senderId, MessageVM message) {
        if (senderId == null || senderId.isBlank()) {
            return;
        }

        Runnable task = () -> {
            MainViewModel.IncomingUnreadAction unreadAction = updateUnreadForIncomingMessage(senderId);
            maybeShowIncomingOsNotification(senderId, message, unreadAction);
        };
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
     * @param active visible activity flag
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
     * @return unread action to apply for the incoming message
     */
    private MainViewModel.IncomingUnreadAction updateUnreadForIncomingMessage(String senderId) {
        String activeChatRecipientId = contentLoader == null ? null : contentLoader.getCurrentChatRecipientId();
        return viewModel.applyIncomingMessage(senderId, activeChatRecipientId);
    }

    /**
     * Shows a desktop notification for an incoming message when notification rules
     * allow it.
     *
     * @param senderId     sender/contact id of the incoming message
     * @param message      incoming message payload
     * @param unreadAction unread action produced for this incoming event
     */
    private void maybeShowIncomingOsNotification(
            String senderId,
            MessageVM message,
            MainViewModel.IncomingUnreadAction unreadAction) {
        Stage stage = ViewRouter.getMainStage();
        boolean windowFocused = stage != null && stage.isFocused();
        if (!shouldShowIncomingOsNotification(settings.isNotificationsShowOsNotifications(), unreadAction,
                windowFocused)) {
            return;
        }

        ContactInfo contact = viewModel.getContactById(senderId);
        String title = resolveIncomingOsNotificationTitle(contact);
        String body = resolveIncomingOsNotificationBody(message, settings.isPrivacyShowNotificationMessagePreview());
        desktopNotificationService.showNotification(
                title,
                body,
                () -> Platform.runLater(() -> focusAppAndOpenSenderChat(senderId)));
    }

    /**
     * Determines whether an incoming-message desktop notification should be shown.
     *
     * @param notificationsEnabled whether OS notifications are enabled in settings
     * @param unreadAction         unread action for the incoming event
     * @param windowFocused        whether the main window is currently focused
     * @return {@code true} when a desktop notification should be shown
     */
    static boolean shouldShowIncomingOsNotification(
            boolean notificationsEnabled,
            MainViewModel.IncomingUnreadAction unreadAction,
            boolean windowFocused) {
        if (!notificationsEnabled || unreadAction == null) {
            return false;
        }
        return unreadAction == MainViewModel.IncomingUnreadAction.INCREMENT || !windowFocused;
    }

    /**
     * Resolves desktop-notification body text from message content and privacy
     * settings.
     *
     * @param message            incoming message payload
     * @param showMessagePreview whether text previews are allowed in notifications
     * @return notification body text
     */
    static String resolveIncomingOsNotificationBody(MessageVM message, boolean showMessagePreview) {
        if (message == null || message.type() == null) {
            return "sent a message";
        }
        MessageType type = message.type();
        if (showMessagePreview && type == MessageType.TEXT) {
            String preview = normalizeNotificationTextPreview(message.content());
            if (!preview.isBlank()) {
                return preview;
            }
        }
        return switch (type) {
            case TEXT -> "sent a text";
            case IMAGE -> "sent an image";
            case FILE -> "sent a file";
        };
    }

    /**
     * Normalizes notification preview text into a single-line bounded string.
     *
     * @param text raw preview text
     * @return normalized preview text suitable for desktop notifications
     */
    private static String normalizeNotificationTextPreview(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "";
        }
        int maxLength = 120;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }

    /**
     * Resolves desktop-notification title from contact data.
     *
     * @param contact sender contact information
     * @return display title for the desktop notification
     */
    static String resolveIncomingOsNotificationTitle(ContactInfo contact) {
        if (contact != null && contact.name() != null && !contact.name().isBlank()) {
            return contact.name().trim();
        }
        return "Unknown Contact";
    }

    /**
     * Focuses the client window and opens chat for the sender when possible.
     *
     * @param senderId sender/contact id to open
     */
    private void focusAppAndOpenSenderChat(String senderId) {
        if (senderId == null || senderId.isBlank()) {
            return;
        }

        Stage stage = ViewRouter.getMainStage();
        if (stage != null) {
            stage.setIconified(false);
            stage.show();
            stage.toFront();
            stage.requestFocus();
        }

        ContactInfo target = viewModel.getContactById(senderId);
        if (target == null) {
            target = viewModel.ensureIncomingContact(senderId);
        }
        if (target == null) {
            return;
        }
        selectContactAndOpenChat(target);
    }
}
