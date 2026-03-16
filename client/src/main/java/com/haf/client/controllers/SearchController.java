package com.haf.client.controllers;

import com.haf.client.utils.UiConstants;
import com.haf.client.viewmodels.SearchViewModel;
import com.haf.shared.dto.UserSearchResult;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the search view ({@code search.fxml}).
 *
 * Keeps rendering/wiring logic in the view and delegates async state +
 * business logic to {@link SearchViewModel}.
 */
public class SearchController {

    private static final Logger LOGGER = Logger.getLogger(SearchController.class.getName());

    @FXML
    private FlowPane resultsPane;

    @FXML
    private ScrollPane resultsScrollPane;

    @FXML
    private VBox statusBox;

    @FXML
    private Text statusText;

    private final SearchViewModel viewModel = SearchViewModel.createDefault();
    private final AtomicInteger renderGeneration = new AtomicInteger();

    @FXML
    public void initialize() {
        bindViewModel();
        bindInfiniteScroll();
        viewModel.clearResults();
    }

    /**
     * Triggers an asynchronous search against the server.
     *
     * @param query the search term (name or reg number)
     */
    public void search(String query) {
        viewModel.search(query);
    }

    /**
     * Clears the results pane and resets the status text.
     */
    public void clearResults() {
        viewModel.clearResults();
    }

    private void bindViewModel() {
        statusText.textProperty().bind(viewModel.statusTextProperty());

        viewModel.hasResultsProperty().addListener((obs, oldValue, newValue) -> updateResultsVisibility(newValue));
        updateResultsVisibility(viewModel.hasResultsProperty().get());

        viewModel.resultsProperty().addListener((ListChangeListener<UserSearchResult>) this::onResultsChanged);
        renderAllResults(viewModel.resultsProperty());
    }

    private void bindInfiniteScroll() {
        if (resultsScrollPane == null) {
            return;
        }

        resultsScrollPane.vvalueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && newValue.doubleValue() >= UiConstants.SEARCH_SCROLL_LOAD_THRESHOLD) {
                viewModel.loadMore();
            }
        });
    }

    private void onResultsChanged(ListChangeListener.Change<? extends UserSearchResult> change) {
        boolean needsFullRender = false;
        List<UserSearchResult> addedRows = new ArrayList<>();

        while (change.next()) {
            if (change.wasPermutated() || change.wasReplaced() || change.wasRemoved() || change.wasUpdated()) {
                needsFullRender = true;
            }
            if (change.wasAdded()) {
                addedRows.addAll(change.getAddedSubList());
            }
        }

        if (needsFullRender) {
            renderAllResults(viewModel.resultsProperty());
            return;
        }

        if (!addedRows.isEmpty()) {
            appendResults(addedRows);
        }
    }

    private void updateResultsVisibility(boolean hasResults) {
        statusBox.setVisible(!hasResults);
        resultsPane.setVisible(hasResults);
    }

    private void renderAllResults(List<UserSearchResult> results) {
        int generation = renderGeneration.incrementAndGet();

        if (results == null || results.isEmpty()) {
            resultsPane.getChildren().clear();
            return;
        }

        List<UserSearchResult> snapshot = List.copyOf(results);
        Thread.ofVirtual().name("search-item-loader").start(() -> {
            List<javafx.scene.Node> loadedCards = new ArrayList<>();
            for (UserSearchResult result : snapshot) {
                try {
                    var resource = getClass().getResource(UiConstants.FXML_SEARCH_RESULT_ITEM);
                    FXMLLoader loader = new FXMLLoader(resource);
                    javafx.scene.Node card = loader.load();
                    populateCard(card, result);
                    loadedCards.add(card);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not load search_result_item.fxml", e);
                }
            }

            Platform.runLater(() -> {
                if (generation != renderGeneration.get()) {
                    return;
                }
                resultsPane.getChildren().setAll(loadedCards);
            });
        });
    }

    private void appendResults(List<UserSearchResult> results) {
        int generation = renderGeneration.get();
        if (results == null || results.isEmpty()) {
            return;
        }

        List<UserSearchResult> snapshot = List.copyOf(results);
        Thread.ofVirtual().name("search-item-loader-append").start(() -> {
            List<javafx.scene.Node> loadedCards = new ArrayList<>();
            for (UserSearchResult result : snapshot) {
                try {
                    var resource = getClass().getResource(UiConstants.FXML_SEARCH_RESULT_ITEM);
                    FXMLLoader loader = new FXMLLoader(resource);
                    javafx.scene.Node card = loader.load();
                    populateCard(card, result);
                    loadedCards.add(card);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not load search_result_item.fxml", e);
                }
            }

            Platform.runLater(() -> {
                if (generation != renderGeneration.get()) {
                    return;
                }
                resultsPane.getChildren().addAll(loadedCards);
            });
        });
    }

    private MainController mainController;

    /**
     * Sets the main controller.
     * 
     * @param mainController The main controller.
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Fills in the fx:id nodes of a loaded search_result_item card.
     *
     * <p>
     * We look up nodes by their {@code fx:id} rather than coupling to a
     * sub-controller, keeping the design simple.
     * </p>
     */
    private void populateCard(javafx.scene.Node card, UserSearchResult result) {
        populateTextElements(card, result);
        populateRankIcon(card, result);
        populateActionButtons(card, result);
    }

    /**
     * Populates the text elements of a search result card.
     * 
     * @param card   The search result card.
     * @param result The search result.
     */
    private void populateTextElements(javafx.scene.Node card, UserSearchResult result) {
        Text fullNameText = (Text) card.lookup("#fullNameText");
        Text regNumberText = (Text) card.lookup("#regNumberText");
        Text emailText = (Text) card.lookup("#emailText");

        if (fullNameText != null) {
            fullNameText.setText(result.getFullName() != null ? result.getFullName() : "");
        }
        if (regNumberText != null) {
            regNumberText.setText(result.getRegNumber() != null ? result.getRegNumber() : "");
        }
        if (emailText != null) {
            emailText.setText(result.getEmail() != null ? result.getEmail() : "");
        }
    }

    /**
     * Populates the rank icon of a search result card.
     * 
     * @param card   The search result card.
     * @param result The search result.
     */
    private void populateRankIcon(javafx.scene.Node card, UserSearchResult result) {
        javafx.scene.image.ImageView rankImage = (javafx.scene.image.ImageView) card.lookup("#rankImage");
        if (rankImage != null && result.getRank() != null) {
            String iconPath = RankIconResolver.resolve(result.getRank());
            try {
                var resource = getClass().getResource(iconPath);
                if (resource != null) {
                    // Use background loading for images to keep UI thread snappy
                    rankImage.setImage(new javafx.scene.image.Image(resource.toExternalForm(), true));
                }
            } catch (Exception ignored) {
                // leave blank if icon not found
            }
        }
    }

    /**
     * Populates the action buttons of a search result card.
     * 
     * @param card   The search result card.
     * @param result The search result.
     */
    private void populateActionButtons(javafx.scene.Node card, UserSearchResult result) {
        com.jfoenix.controls.JFXButton removeButton = (com.jfoenix.controls.JFXButton) card.lookup("#removeButton");
        com.jfoenix.controls.JFXButton startChatButton = (com.jfoenix.controls.JFXButton) card
                .lookup("#startChatButton");

        setupRemoveButton(removeButton, result);
        setupStartChatButton(startChatButton, removeButton, result);
    }

    /**
     * Sets up the remove button.
     * 
     * @param removeButton The remove button.
     * @param result       The search result.
     */
    private void setupRemoveButton(com.jfoenix.controls.JFXButton removeButton, UserSearchResult result) {
        if (removeButton == null)
            return;

        updateRemoveButtonText(removeButton, result.getUserId());

        removeButton.setOnAction(e -> {
            if (mainController == null)
                return;

            if (mainController.hasContact(result.getUserId())) {
                mainController.removeContact(result.getUserId());
                LOGGER.info("Remove contact requested for user: " + result.getUserId());
            } else {
                mainController.addContact(result.getUserId(), result.getFullName(), result.getRegNumber());
                LOGGER.info("Add contact requested for user: " + result.getUserId());
            }
            updateRemoveButtonText(removeButton, result.getUserId());
        });
    }

    /**
     * Sets up the start chat button.
     * 
     * @param startChatButton The start chat button.
     * @param removeButton    The remove button.
     * @param result          The search result.
     */
    private void setupStartChatButton(com.jfoenix.controls.JFXButton startChatButton,
            com.jfoenix.controls.JFXButton removeButton,
            UserSearchResult result) {
        if (startChatButton == null)
            return;

        startChatButton.setOnAction(e -> {
            LOGGER.info("Start chat requested for user: " + result.getUserId());
            if (mainController != null) {
                mainController.startChatWith(result.getUserId(), result.getFullName(), result.getRegNumber());
                // Since starting a chat adds them to contacts, update the other button too.
                if (removeButton != null) {
                    updateRemoveButtonText(removeButton, result.getUserId());
                }
            }
        });
    }

    /**
     * Updates the text of the remove button based on whether the user is already a
     * contact.
     * 
     * @param removeButton The remove button.
     * @param userId       The user ID.
     */
    private void updateRemoveButtonText(com.jfoenix.controls.JFXButton removeButton, String userId) {
        if (mainController != null && mainController.hasContact(userId)) {
            removeButton.setText("Remove contact");
        } else {
            removeButton.setText("Add contact");
        }
    }

    /**
     * Resolves a rank name to its icon resource path, following the existing
     * mapping in {@link UiConstants}.
     */
    private static final class RankIconResolver {

        private RankIconResolver() {
        }

        static String resolve(String rank) {
            return switch (rank) {
                case UiConstants.RANK_YPOSMINIAS -> UiConstants.ICON_RANK_YPOSMINIAS;
                case UiConstants.RANK_SMINIAS -> UiConstants.ICON_RANK_SMINIAS;
                case UiConstants.RANK_EPISMINIAS -> UiConstants.ICON_RANK_EPISMINIAS;
                case UiConstants.RANK_ARCHISMINIAS -> UiConstants.ICON_RANK_ARCHISMINIAS;
                case UiConstants.RANK_ANTHYPASPISTIS -> UiConstants.ICON_RANK_ANTHYPASPISTIS;
                case UiConstants.RANK_ANTHYPOSMINAGOS -> UiConstants.ICON_RANK_ANTHYPOSMINAGOS;
                case UiConstants.RANK_YPOSMINAGOS -> UiConstants.ICON_RANK_YPOSMINAGOS;
                case UiConstants.RANK_SMINAGOS -> UiConstants.ICON_RANK_SMINAGOS;
                case UiConstants.RANK_EPISMINAGOS -> UiConstants.ICON_RANK_EPISMINAGOS;
                case UiConstants.RANK_ANTISMINARCHOS -> UiConstants.ICON_RANK_ANTISMINARCHOS;
                case UiConstants.RANK_SMINARCHOS -> UiConstants.ICON_RANK_SMINARCHOS;
                case UiConstants.RANK_TAKSIARCOS -> UiConstants.ICON_RANK_TAKSIARCOS;
                case UiConstants.RANK_YPOPTERARCHOS -> UiConstants.ICON_RANK_YPOPTERARCHOS;
                case UiConstants.RANK_ANTIPTERARCHOS -> UiConstants.ICON_RANK_ANTIPTERARCHOS;
                default -> UiConstants.ICON_RANK_DEFAULT;
            };
        }
    }

}
