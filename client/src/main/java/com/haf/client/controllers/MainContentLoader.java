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
        void onViewLoadFailure(ViewKind viewKind, Throwable error, Runnable retryAction);
    }

    @FunctionalInterface
    interface ViewLoader {
        LoadedView load(String fxmlPath) throws IOException;
    }

    record LoadedView(Parent view, Object controller) {
    }

    private final StackPane contentPane;
    private final SearchContactActions searchContactActions;
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

    MainContentLoader(StackPane contentPane,
            SearchContactActions searchContactActions,
            ViewLoadFailureHandler viewLoadFailureHandler) {
        this(contentPane, searchContactActions, viewLoadFailureHandler, MainContentLoader::loadFromFxml);
    }

    MainContentLoader(StackPane contentPane,
            SearchContactActions searchContactActions,
            ViewLoadFailureHandler viewLoadFailureHandler,
            ViewLoader viewLoader) {
        this.contentPane = Objects.requireNonNull(contentPane, "contentPane");
        this.searchContactActions = Objects.requireNonNull(searchContactActions, "searchContactActions");
        this.viewLoadFailureHandler = viewLoadFailureHandler == null ? (viewKind, error, retryAction) -> {
        } : viewLoadFailureHandler;
        this.viewLoader = Objects.requireNonNull(viewLoader, "viewLoader");
    }

    void triggerPreloading() {
        Thread.ofVirtual().name("view-preloader").start(() -> {
            ensurePlaceholderLoaded();
            ensureSearchLoaded();
        });
    }

    void showSearchView() {
        CompletableFuture<Parent> future = ensureSearchFutureReady();
        future.thenAccept(view -> runOnUiThread(() -> contentPane.getChildren().setAll(view)));
        if (!searchLoadingStarted.get()) {
            ensureSearchLoaded();
        }
    }

    void showPlaceholder() {
        CompletableFuture<Parent> future = ensurePlaceholderFutureReady();
        future.thenAccept(view -> runOnUiThread(() -> contentPane.getChildren().setAll(view)));
        if (!placeholderLoadingStarted.get()) {
            ensurePlaceholderLoaded();
        }
    }

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

    SearchController getSearchController() {
        return searchController;
    }

    String getCurrentChatRecipientId() {
        return currentChatRecipientId;
    }

    void setCurrentChatRecipientId(String recipientId) {
        currentChatRecipientId = recipientId;
    }

    void clearCurrentChatRecipient() {
        currentChatRecipientId = null;
    }

    void refreshActiveChatIfRecipient(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            return;
        }
        if (currentChatController != null && recipientId.equals(currentChatRecipientId)) {
            currentChatController.setRecipient(recipientId);
        }
    }

    private void retryShowPlaceholder() {
        resetPlaceholderCache();
        showPlaceholder();
    }

    private void retryShowSearchView() {
        resetSearchCache();
        showSearchView();
    }

    private void retryShowChat(String recipientId) {
        currentChatView = null;
        currentChatController = null;
        showChat(recipientId);
    }

    private synchronized void resetPlaceholderCache() {
        placeholderFuture = new CompletableFuture<>();
        placeholderLoadingStarted.set(false);
    }

    private synchronized void resetSearchCache() {
        searchController = null;
        searchFuture = new CompletableFuture<>();
        searchLoadingStarted.set(false);
    }

    private synchronized CompletableFuture<Parent> ensurePlaceholderFutureReady() {
        placeholderFuture = refreshFutureIfFailed(placeholderFuture, placeholderLoadingStarted.get());
        return placeholderFuture;
    }

    private synchronized CompletableFuture<Parent> ensureSearchFutureReady() {
        searchFuture = refreshFutureIfFailed(searchFuture, searchLoadingStarted.get());
        return searchFuture;
    }

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

    private void notifyViewLoadFailure(ViewKind viewKind, Throwable error, Runnable retryAction) {
        viewLoadFailureHandler.onViewLoadFailure(viewKind, error, retryAction);
    }

    private static <T> T requireController(Object controller, Class<T> expectedType, String fxmlPath) throws IOException {
        if (!expectedType.isInstance(controller)) {
            throw new IOException("Controller type mismatch for " + fxmlPath);
        }
        return expectedType.cast(controller);
    }

    private static LoadedView loadFromFxml(String fxmlPath) throws IOException {
        var resource = MainContentLoader.class.getResource(fxmlPath);
        LOGGER.log(Level.INFO, "Loading FXML: {0}", resource);
        FXMLLoader loader = new FXMLLoader(resource);
        Parent view = loader.load();
        return new LoadedView(view, loader.getController());
    }

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
