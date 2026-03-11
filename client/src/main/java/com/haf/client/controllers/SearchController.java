package com.haf.client.controllers;

import com.haf.client.core.NetworkSession;
import com.haf.client.utils.UiConstants;
import com.haf.shared.dto.UserSearchResponse;
import com.haf.shared.dto.UserSearchResult;
import com.haf.shared.utils.JsonCodec;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the search view ({@code search.fxml}).
 *
 * <p>
 * Called by {@link MainController} when the user types a query and presses
 * Enter. Fetches results from the server, loads an
 * {@code search_result_item.fxml} card for each match and adds it to the
 * {@link #resultsPane}.
 * </p>
 */
public class SearchController {

    private static final Logger LOGGER = Logger.getLogger(SearchController.class.getName());

    @FXML
    private FlowPane resultsPane;

    @FXML
    private VBox statusBox;

    @FXML
    private Text statusText;

    /**
     * Triggers an asynchronous search against the server.
     *
     * @param query the search term (name or reg number)
     */
    public void search(String query) {
        if (query == null || query.isBlank()) {
            clearResults();
            return;
        }

        resultsPane.getChildren().clear();
        showStatus("Searching...");

        Thread.ofVirtual().name("search-query").start(() -> {
            try {
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String path = "/api/v1/search?q=" + encoded;
                String json = NetworkSession.get().getAuthenticated(path).get();
                UserSearchResponse response = JsonCodec.fromJson(json, UserSearchResponse.class);

                Platform.runLater(() -> displayResults(response));
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Search request failed", ex);
                Platform.runLater(() -> showStatus("Search failed. Please try again."));
            }
        });
    }

    /**
     * Clears the results pane and resets the status text.
     */
    public void clearResults() {
        resultsPane.getChildren().clear();
        showStatus("Search for users by name or registration number.");
    }

    /**
     * Populates the results pane with cards.
     */
    private void displayResults(UserSearchResponse response) {
        resultsPane.getChildren().clear();

        if (response.getError() != null) {
            showStatus("Error: " + response.getError());
            return;
        }

        if (response.getResults() == null || response.getResults().isEmpty()) {
            showStatus("No results found.");
            return;
        }

        hideStatus();

        // Offload FXML loading of results to a background thread to prevent UI jank
        Thread.ofVirtual().name("search-item-loader").start(() -> {
            java.util.List<javafx.scene.Node> loadedCards = new java.util.ArrayList<>();
            for (UserSearchResult result : response.getResults()) {
                try {
                    var resource = getClass().getResource(UiConstants.FXML_SEARCH_RESULT_ITEM);
                    LOGGER.log(Level.INFO, "Loading search result item FXML: {0}", resource);
                    FXMLLoader loader = new FXMLLoader(resource);
                    javafx.scene.Node card = loader.load();
                    // We can populate the card's text and set up listeners in the background.
                    // Just don't touch live scenes.
                    populateCard(card, result);
                    loadedCards.add(card);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Could not load search_result_item.fxml", e);
                }
            }

            // Once all cards are prepared, add them to the UI thread
            Platform.runLater(() -> resultsPane.getChildren().setAll(loadedCards));
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

    /**
     * Shows a status message.
     * 
     * @param message The status message.
     */
    private void showStatus(String message) {
        statusText.setText(message);
        statusBox.setVisible(true);
        resultsPane.setVisible(false);
    }

    /**
     * Hides the status message.
     */
    private void hideStatus() {
        statusBox.setVisible(false);
        resultsPane.setVisible(true);
    }
}
