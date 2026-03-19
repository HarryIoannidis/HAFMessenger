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

    private final StackPane contentPane;
    private final SearchContactActions searchContactActions;

    private SearchController searchController;
    private Parent currentChatView;
    private ChatController currentChatController;
    private String currentChatRecipientId;

    private final CompletableFuture<Parent> placeholderFuture = new CompletableFuture<>();
    private final CompletableFuture<Parent> searchFuture = new CompletableFuture<>();
    private final AtomicInteger chatLoadGeneration = new AtomicInteger();
    private final AtomicBoolean placeholderLoadingStarted = new AtomicBoolean(false);
    private final AtomicBoolean searchLoadingStarted = new AtomicBoolean(false);

    MainContentLoader(StackPane contentPane, SearchContactActions searchContactActions) {
        this.contentPane = Objects.requireNonNull(contentPane, "contentPane");
        this.searchContactActions = Objects.requireNonNull(searchContactActions, "searchContactActions");
    }

    void triggerPreloading() {
        Thread.ofVirtual().name("view-preloader").start(() -> {
            ensurePlaceholderLoaded();
            ensureSearchLoaded();
        });
    }

    void showSearchView() {
        searchFuture.thenAccept(view -> Platform.runLater(() -> contentPane.getChildren().setAll(view)));
        if (!searchLoadingStarted.get()) {
            ensureSearchLoaded();
        }
    }

    void showPlaceholder() {
        placeholderFuture.thenAccept(view -> Platform.runLater(() -> contentPane.getChildren().setAll(view)));
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
                var resource = getClass().getResource(UiConstants.FXML_CHAT);
                LOGGER.log(Level.INFO, "Loading chat FXML: {0}", resource);
                FXMLLoader loader = new FXMLLoader(resource);
                Parent view = loader.load();

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

        if (Platform.isFxApplicationThread()) {
            loadAction.run();
        } else {
            Platform.runLater(loadAction);
        }
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
            controller.setContactActions(searchContactActions);

            searchController = controller;
            searchFuture.complete(view);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load search FXML", e);
            searchFuture.completeExceptionally(e);
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
}
