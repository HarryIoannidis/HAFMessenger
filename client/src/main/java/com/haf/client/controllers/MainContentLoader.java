package com.haf.client.controllers;

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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns lazy loading/caching of main-content views (placeholder, search, chat).
 */
final class MainContentLoader {
    private static final Logger LOGGER = Logger.getLogger(MainContentLoader.class.getName());

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

    private SearchController searchController;
    private Parent currentChatView;
    private ChatController currentChatController;
    private String currentChatRecipientId;

    private volatile CompletableFuture<Parent> placeholderFuture = new CompletableFuture<>();
    private volatile CompletableFuture<Parent> searchFuture = new CompletableFuture<>();
    private final AtomicInteger chatLoadGeneration = new AtomicInteger();
    private final AtomicBoolean placeholderLoadingStarted = new AtomicBoolean(false);
    private final AtomicBoolean searchLoadingStarted = new AtomicBoolean(false);

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
        this(contentPane, searchContactActions, viewLoadFailureHandler, MainContentLoader::loadFromFxml);
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
            ViewLoader viewLoader) {
        this.contentPane = Objects.requireNonNull(contentPane, "contentPane");
        this.searchContactActions = Objects.requireNonNull(searchContactActions, "searchContactActions");
        this.viewLoadFailureHandler = viewLoadFailureHandler == null ? (viewKind, error, retryAction) -> {
        } : viewLoadFailureHandler;
        this.viewLoader = Objects.requireNonNull(viewLoader, "viewLoader");
    }

    /**
     * Starts background preloading for placeholder and search views.
     */
    void triggerPreloading() {
        Thread.ofVirtual().name("view-preloader").start(() -> {
            ensurePlaceholderLoaded();
            ensureSearchLoaded();
        });
    }

    /**
     * Displays the search view, loading it lazily if needed.
     */
    void showSearchView() {
        CompletableFuture<Parent> future = ensureSearchFutureReady();
        future.thenAccept(view -> runOnUiThread(() -> contentPane.getChildren().setAll(view)));
        if (!searchLoadingStarted.get()) {
            Thread.ofVirtual().name("search-view-loader").start(this::ensureSearchLoaded);
        }
    }

    /**
     * Displays the placeholder view, loading it lazily if needed.
     */
    void showPlaceholder() {
        CompletableFuture<Parent> future = ensurePlaceholderFutureReady();
        future.thenAccept(view -> runOnUiThread(() -> contentPane.getChildren().setAll(view)));
        if (!placeholderLoadingStarted.get()) {
            Thread.ofVirtual().name("placeholder-view-loader").start(this::ensurePlaceholderLoaded);
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

        Runnable loadAction = () -> {
            try {
                LoadedView loaded = viewLoader.load(UiConstants.FXML_CHAT);
                ChatController chatController = requireController(
                        loaded.controller(),
                        ChatController.class,
                        UiConstants.FXML_CHAT);
                Parent view = loaded.view();

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
                notifyViewLoadFailure(
                        ViewKind.CHAT,
                        e,
                        () -> retryShowChat(recipientId));
            }
        };

        runOnUiThread(loadAction);
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
        currentChatView = null;
        currentChatController = null;
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

            searchController = controller;
            future.complete(loaded.view());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load search FXML", e);
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
            LOGGER.log(Level.WARNING, "Could not load placeholder FXML", e);
            future.completeExceptionally(e);
            placeholderLoadingStarted.set(false);
            notifyViewLoadFailure(
                    ViewKind.PLACEHOLDER,
                    e,
                    this::retryShowPlaceholder);
        }
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
     * Loads an FXML file from classpath and returns its root and controller.
     *
     * @param fxmlPath classpath FXML path
     * @return loaded view and controller pair
     * @throws IOException when resource cannot be loaded
     */
    private static LoadedView loadFromFxml(String fxmlPath) throws IOException {
        var resource = MainContentLoader.class.getResource(fxmlPath);
        LOGGER.log(Level.INFO, "Loading FXML: {0}", resource);
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
