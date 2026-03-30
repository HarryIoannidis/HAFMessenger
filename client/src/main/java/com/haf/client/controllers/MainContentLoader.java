package com.haf.client.controllers;

import com.haf.client.utils.ClientSettings;
import com.haf.client.utils.UiConstants;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns lazy loading/caching of main-content views (placeholder, search, chat).
 */
final class MainContentLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(MainContentLoader.class);

    enum ViewKind {
        PLACEHOLDER,
        SEARCH,
        CHAT
    }

    @FunctionalInterface
    interface ViewLoadFailureHandler {
        /**
         * Receives a view-load failure with a retry callback for user-driven recovery.
         *
         * @param viewKind logical view that failed to load
         * @param error underlying loading error
         * @param retryAction callback that retries loading the same view
         */
        void onViewLoadFailure(ViewKind viewKind, Throwable error, Runnable retryAction);
    }

    @FunctionalInterface
    interface ViewLoader {
        /**
         * Loads an FXML view and returns both root node and controller instance.
         *
         * @param fxmlPath classpath FXML path
         * @return loaded view/controller tuple
         * @throws IOException when FXML cannot be loaded
         */
        LoadedView load(String fxmlPath) throws IOException;
    }

    record LoadedView(Parent view, Object controller) {
    }

    private final StackPane contentPane;
    private final SearchController.ContactActions searchContactActions;
    private final ViewLoadFailureHandler viewLoadFailureHandler;
    private final ViewLoader viewLoader;
    private final ClientSettings settings;

    private SearchController searchController;
    private Parent currentChatView;
    private ChatController currentChatController;
    private String currentChatRecipientId;

    // Guarded by synchronized reset/ensure methods.
    private CompletableFuture<Parent> placeholderFuture = new CompletableFuture<>();
    private CompletableFuture<Parent> searchFuture = new CompletableFuture<>();
    private CompletableFuture<LoadedView> chatFuture = new CompletableFuture<>();
    private final AtomicInteger chatLoadGeneration = new AtomicInteger();
    private final AtomicBoolean placeholderLoadingStarted = new AtomicBoolean(false);
    private final AtomicBoolean searchLoadingStarted = new AtomicBoolean(false);
    private final AtomicBoolean chatLoadingStarted = new AtomicBoolean(false);

    /**
     * Creates a content loader using the default FXML loader strategy.
     *
     * @param contentPane main content container to populate
     * @param searchContactActions search callbacks used by the search controller
     * @param viewLoadFailureHandler optional failure handler (no-op when {@code null})
     * @throws NullPointerException when required dependencies are {@code null}
     */
    MainContentLoader(StackPane contentPane,
            SearchController.ContactActions searchContactActions,
            ViewLoadFailureHandler viewLoadFailureHandler) {
        this(contentPane, searchContactActions, viewLoadFailureHandler, ClientSettings.defaults(), MainContentLoader::loadFromFxml);
    }

    /**
     * Creates a content loader using the default FXML loader strategy and explicit
     * client settings.
     *
     * @param contentPane main content container to populate
     * @param searchContactActions search callbacks used by the search controller
     * @param viewLoadFailureHandler optional failure handler (no-op when {@code null})
     * @param settings client settings used by created child controllers
     */
    MainContentLoader(StackPane contentPane,
            SearchController.ContactActions searchContactActions,
            ViewLoadFailureHandler viewLoadFailureHandler,
            ClientSettings settings) {
        this(contentPane, searchContactActions, viewLoadFailureHandler, settings, MainContentLoader::loadFromFxml);
    }

    /**
     * Creates a content loader with an explicit view-loader strategy (useful for tests).
     *
     * @param contentPane main content container to populate
     * @param searchContactActions search callbacks used by the search controller
     * @param viewLoadFailureHandler optional failure handler (no-op when {@code null})
     * @param viewLoader strategy used to load FXML views
     * @throws NullPointerException when required dependencies are {@code null}
     */
    MainContentLoader(StackPane contentPane,
            SearchController.ContactActions searchContactActions,
            ViewLoadFailureHandler viewLoadFailureHandler,
            ClientSettings settings,
            ViewLoader viewLoader) {
        this.contentPane = Objects.requireNonNull(contentPane, "contentPane");
        this.searchContactActions = Objects.requireNonNull(searchContactActions, "searchContactActions");
        this.viewLoadFailureHandler = viewLoadFailureHandler == null ? (viewKind, error, retryAction) -> {
        } : viewLoadFailureHandler;
        this.settings = settings == null ? ClientSettings.defaults() : settings;
        this.viewLoader = Objects.requireNonNull(viewLoader, "viewLoader");
    }

    /**
     * Starts background preloading for primary main-content views.
     */
    void triggerPreloading() {
        preloadAllViewsAsync();
    }

    /**
     * Starts background preloading for all primary main-content views and returns a
     * completion future.
     *
     * @return future that completes when placeholder, search, and chat views are
     *         preloaded
     */
    CompletableFuture<Void> preloadAllViewsAsync() {
        CompletableFuture<Parent> placeholderReady = ensurePlaceholderFutureReady();
        CompletableFuture<Parent> searchReady = ensureSearchFutureReady();
        CompletableFuture<LoadedView> chatReady = ensureChatFutureReady();

        Thread.ofPlatform().daemon().name("view-preloader").start(() -> {
            ensurePlaceholderLoaded();
            ensureSearchLoaded();
            ensureChatLoaded();
        });

        return CompletableFuture.allOf(placeholderReady, searchReady, chatReady);
    }

    /**
     * Displays the search view, loading it lazily if needed.
     */
    void showSearchView() {
        CompletableFuture<Parent> future = ensureSearchFutureReady();
        future.thenAccept(view -> runOnUiThread(() -> contentPane.getChildren().setAll(view)));
        if (!searchLoadingStarted.get()) {
            Thread.ofPlatform().daemon().name("search-view-loader").start(this::ensureSearchLoaded);
        }
    }

    /**
     * Displays the placeholder view, loading it lazily if needed.
     */
    void showPlaceholder() {
        CompletableFuture<Parent> future = ensurePlaceholderFutureReady();
        future.thenAccept(view -> runOnUiThread(() -> contentPane.getChildren().setAll(view)));
        if (!placeholderLoadingStarted.get()) {
            Thread.ofPlatform().daemon().name("placeholder-view-loader").start(this::ensurePlaceholderLoaded);
        }
    }

    /**
     * Displays chat view for a recipient, reusing cached chat view when already loaded.
     *
     * @param recipientId active chat recipient id
     */
    void showChat(String recipientId) {
        int loadGeneration = chatLoadGeneration.incrementAndGet();

        // Reuse chat view if already loaded - just switch recipient.
        if (currentChatView != null && currentChatController != null) {
            if (!recipientId.equals(currentChatRecipientId)) {
                currentChatController.setRecipient(recipientId);
                currentChatRecipientId = recipientId;
            }
            contentPane.getChildren().setAll(currentChatView);
            return;
        }

        CompletableFuture<LoadedView> future = ensureChatFutureReady();
        future.thenAccept(loaded -> runOnUiThread(() -> applyLoadedChatView(recipientId, loadGeneration, loaded)));
        future.exceptionally(error -> {
            Throwable cause = error instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null
                    ? ce.getCause()
                    : error;
            runOnUiThread(() -> {
                if (loadGeneration != chatLoadGeneration.get()) {
                    return;
                }
                LOGGER.error("Could not load chat FXML", cause);
                notifyViewLoadFailure(
                        ViewKind.CHAT,
                        cause,
                        () -> retryShowChat(recipientId));
            });
            return null;
        });

        if (!chatLoadingStarted.get()) {
            Thread.ofPlatform().daemon().name("chat-view-loader").start(this::ensureChatLoaded);
        }
    }

    /**
     * Returns the cached search controller once search view has been loaded.
     *
     * @return search controller or {@code null} when search view not loaded yet
     */
    SearchController getSearchController() {
        return searchController;
    }

    /**
     * Returns the recipient currently bound to the active cached chat controller.
     *
     * @return current chat recipient id, or {@code null} when not set
     */
    String getCurrentChatRecipientId() {
        return currentChatRecipientId;
    }

    /**
     * Updates the tracked active chat recipient id.
     *
     * @param recipientId recipient id to track
     */
    void setCurrentChatRecipientId(String recipientId) {
        currentChatRecipientId = recipientId;
    }

    /**
     * Clears tracked active chat recipient id.
     */
    void clearCurrentChatRecipient() {
        currentChatRecipientId = null;
    }

    /**
     * Refreshes current chat controller recipient binding when the requested id matches active chat.
     *
     * @param recipientId recipient id to refresh
     */
    void refreshActiveChatIfRecipient(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            return;
        }
        if (currentChatController != null && recipientId.equals(currentChatRecipientId)) {
            currentChatController.setRecipient(recipientId);
        }
    }

    /**
     * Retries placeholder rendering by resetting cache state first.
     */
    private void retryShowPlaceholder() {
        resetPlaceholderCache();
        showPlaceholder();
    }

    /**
     * Retries search rendering by resetting search cache state first.
     */
    private void retryShowSearchView() {
        resetSearchCache();
        showSearchView();
    }

    /**
     * Retries chat loading by clearing cached chat view/controller and re-invoking chat display.
     *
     * @param recipientId recipient id to reopen
     */
    private void retryShowChat(String recipientId) {
        resetChatCache();
        showChat(recipientId);
    }

    /**
     * Resets placeholder loading cache and state flags.
     */
    private synchronized void resetPlaceholderCache() {
        placeholderFuture = new CompletableFuture<>();
        placeholderLoadingStarted.set(false);
    }

    /**
     * Resets search loading cache/controller and state flags.
     */
    private synchronized void resetSearchCache() {
        searchController = null;
        searchFuture = new CompletableFuture<>();
        searchLoadingStarted.set(false);
    }

    /**
     * Resets chat loading cache/controller and state flags.
     */
    private synchronized void resetChatCache() {
        currentChatView = null;
        currentChatController = null;
        currentChatRecipientId = null;
        chatFuture = new CompletableFuture<>();
        chatLoadingStarted.set(false);
    }

    /**
     * Ensures placeholder future is usable, recreating it when previous load failed and is idle.
     *
     * @return placeholder future ready for listeners/completion
     */
    private synchronized CompletableFuture<Parent> ensurePlaceholderFutureReady() {
        placeholderFuture = refreshFutureIfFailed(placeholderFuture, placeholderLoadingStarted.get());
        return placeholderFuture;
    }

    /**
     * Ensures search future is usable, recreating it when previous load failed and is idle.
     *
     * @return search future ready for listeners/completion
     */
    private synchronized CompletableFuture<Parent> ensureSearchFutureReady() {
        searchFuture = refreshFutureIfFailed(searchFuture, searchLoadingStarted.get());
        return searchFuture;
    }

    /**
     * Ensures chat future is usable, recreating it when previous load failed and is
     * idle.
     *
     * @return chat future ready for listeners/completion
     */
    private synchronized CompletableFuture<LoadedView> ensureChatFutureReady() {
        chatFuture = refreshFutureIfFailed(chatFuture, chatLoadingStarted.get());
        return chatFuture;
    }

    /**
     * Loads search view/controller once and completes the shared search future.
     */
    private void ensureSearchLoaded() {
        if (searchLoadingStarted.getAndSet(true)) {
            return;
        }

        CompletableFuture<Parent> future = ensureSearchFutureReady();
        try {
            LoadedView loaded = viewLoader.load(UiConstants.FXML_SEARCH);
            SearchController controller = requireController(
                    loaded.controller(),
                    SearchController.class,
                    UiConstants.FXML_SEARCH);
            controller.setContactActions(searchContactActions);
            controller.setSettings(settings);

            searchController = controller;
            future.complete(loaded.view());
        } catch (IOException e) {
            LOGGER.error( "Could not load search FXML", e);
            future.completeExceptionally(e);
            searchLoadingStarted.set(false);
            notifyViewLoadFailure(
                    ViewKind.SEARCH,
                    e,
                    this::retryShowSearchView);
        }
    }

    /**
     * Loads placeholder view once and completes the shared placeholder future.
     */
    private void ensurePlaceholderLoaded() {
        if (placeholderLoadingStarted.getAndSet(true)) {
            return;
        }

        CompletableFuture<Parent> future = ensurePlaceholderFutureReady();
        try {
            LoadedView loaded = viewLoader.load(UiConstants.FXML_PLACEHOLDER);
            future.complete(loaded.view());
        } catch (IOException e) {
            LOGGER.warn( "Could not load placeholder FXML", e);
            future.completeExceptionally(e);
            placeholderLoadingStarted.set(false);
            notifyViewLoadFailure(
                    ViewKind.PLACEHOLDER,
                    e,
                    this::retryShowPlaceholder);
        }
    }

    /**
     * Loads chat view/controller once and completes the shared chat future.
     */
    private void ensureChatLoaded() {
        if (chatLoadingStarted.getAndSet(true)) {
            return;
        }

        CompletableFuture<LoadedView> future = ensureChatFutureReady();
        try {
            LoadedView loaded = viewLoader.load(UiConstants.FXML_CHAT);
            ChatController controller = requireController(
                    loaded.controller(),
                    ChatController.class,
                    UiConstants.FXML_CHAT);
            controller.setSettings(settings);
            future.complete(new LoadedView(loaded.view(), controller));
        } catch (IOException e) {
            future.completeExceptionally(e);
            chatLoadingStarted.set(false);
        }
    }

    /**
     * Applies the loaded chat view/controller to the UI for the requested recipient.
     *
     * @param recipientId recipient id to activate
     * @param loadGeneration generation token guarding stale loads
     * @param loaded loaded chat view/controller tuple
     */
    private void applyLoadedChatView(String recipientId, int loadGeneration, LoadedView loaded) {
        if (loadGeneration != chatLoadGeneration.get()) {
            return;
        }

        ChatController chatController = requireControllerUnchecked(
                loaded.controller(),
                ChatController.class,
                UiConstants.FXML_CHAT);
        Parent view = loaded.view();

        chatController.setSettings(settings);
        chatController.setRecipient(recipientId);
        currentChatView = view;
        currentChatController = chatController;
        currentChatRecipientId = recipientId;
        contentPane.getChildren().setAll(view);
    }

    /**
     * Forwards view-load failures to the configured failure handler.
     *
     * @param viewKind failing view type
     * @param error underlying failure
     * @param retryAction retry callback
     */
    private void notifyViewLoadFailure(ViewKind viewKind, Throwable error, Runnable retryAction) {
        viewLoadFailureHandler.onViewLoadFailure(viewKind, error, retryAction);
    }

    /**
     * Validates controller type returned by FXML loader and casts it to expected type.
     *
     * @param controller actual controller object
     * @param expectedType required controller class
     * @param fxmlPath source FXML path for diagnostics
     * @param <T> expected controller type
     * @return cast controller instance
     * @throws IOException when controller type does not match expectation
     */
    private static <T> T requireController(Object controller, Class<T> expectedType, String fxmlPath) throws IOException {
        if (!expectedType.isInstance(controller)) {
            throw new IOException("Controller type mismatch for " + fxmlPath);
        }
        return expectedType.cast(controller);
    }

    /**
     * Runtime variant of {@link #requireController(Object, Class, String)} for
     * already-loaded views.
     */
    private static <T> T requireControllerUnchecked(Object controller, Class<T> expectedType, String fxmlPath) {
        if (!expectedType.isInstance(controller)) {
            throw new IllegalStateException("Controller type mismatch for " + fxmlPath);
        }
        return expectedType.cast(controller);
    }

    /**
     * Loads an FXML file from classpath and returns its root and controller.
     *
     * @param fxmlPath classpath FXML path
     * @return loaded view and controller pair
     * @throws IOException when resource cannot be loaded
     */
    private static LoadedView loadFromFxml(String fxmlPath) throws IOException {
        var resource = MainContentLoader.class.getResource(fxmlPath);
        LOGGER.info( "Loading FXML: {}", resource);
        FXMLLoader loader = new FXMLLoader(resource);
        Parent view = loader.load();
        return new LoadedView(view, loader.getController());
    }

    /**
     * Runs an action on JavaFX thread, falling back to direct execution when FX toolkit is unavailable.
     *
     * @param action UI action to execute
     */
    private static void runOnUiThread(Runnable action) {
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException ex) {
            action.run();
        }
    }

    /**
     * Replaces failed futures with fresh instances when no load is currently in progress.
     *
     * @param future existing future
     * @param loadingStarted whether load operation is currently running
     * @param <T> future result type
     * @return original or refreshed future depending on failure/loading state
     */
    static <T> CompletableFuture<T> refreshFutureIfFailed(CompletableFuture<T> future, boolean loadingStarted) {
        if (future == null) {
            return new CompletableFuture<>();
        }
        if (future.isCompletedExceptionally() && !loadingStarted) {
            return new CompletableFuture<>();
        }
        return future;
    }
}
