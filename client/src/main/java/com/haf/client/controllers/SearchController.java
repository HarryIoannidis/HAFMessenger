package com.haf.client.controllers;

import com.haf.client.utils.PopupMessageBuilder;
import com.haf.client.utils.UiConstants;
import com.haf.client.viewmodels.SearchSortViewModel;
import com.haf.client.viewmodels.SearchViewModel;
import com.haf.shared.dto.UserSearchResultDTO;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    // Search results containers
    @FXML
    private FlowPane resultsPane;
    @FXML
    private ScrollPane resultsScrollPane;

    // Search status placeholders
    @FXML
    private VBox statusBox;
    @FXML
    private Text statusText;

    private final SearchViewModel viewModel = SearchViewModel.createDefault();
    private final AtomicInteger renderGeneration = new AtomicInteger();
    private SearchContactActions contactActions = SearchContactActions.NO_OP;

    /**
     * Initializes bindings, infinite scroll behavior, and idle search state.
     */
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
     * Triggers an asynchronous search against the server with sort options.
     *
     * @param query       the search term (name or reg number)
     * @param sortOptions selected sort field + direction
     */
    public void search(String query, SearchSortViewModel.SortOptions sortOptions) {
        viewModel.search(query, sortOptions);
    }

    /**
     * Clears the results pane and resets the status text.
     */
    public void clearResults() {
        viewModel.clearResults();
    }

    /**
     * Binds view-model state to search view components.
     */
    private void bindViewModel() {
        statusText.textProperty().bind(viewModel.statusTextProperty());

        viewModel.hasResultsProperty().addListener((obs, oldValue, newValue) -> updateResultsVisibility(newValue));
        updateResultsVisibility(viewModel.hasResultsProperty().get());

        viewModel.resultsProperty().addListener((ListChangeListener<UserSearchResultDTO>) this::onResultsChanged);
        renderAllResults(viewModel.resultsProperty());
    }

    /**
     * Enables lazy pagination when scrolling near the bottom of result list.
     */
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

    /**
     * Reacts to result-list mutations and decides whether to re-render all rows or
     * append only additions.
     *
     * @param change change descriptor emitted by observable result list
     */
    private void onResultsChanged(ListChangeListener.Change<? extends UserSearchResultDTO> change) {
        boolean needsFullRender = false;
        List<UserSearchResultDTO> addedRows = null;

        while (change.next()) {
            if (change.wasPermutated() || change.wasReplaced() || change.wasRemoved() || change.wasUpdated()) {
                needsFullRender = true;
            }
            if (change.wasAdded()) {
                if (addedRows == null) {
                    addedRows = new ArrayList<>();
                }
                addedRows.addAll(change.getAddedSubList());
            }
        }

        if (needsFullRender) {
            renderAllResults(viewModel.resultsProperty());
            return;
        }

        if (addedRows != null) {
            appendResults(addedRows);
        }
    }

    /**
     * Toggles visibility of status placeholder and results pane.
     *
     * @param hasResults whether at least one search result exists
     */
    private void updateResultsVisibility(boolean hasResults) {
        statusBox.setVisible(!hasResults);
        resultsPane.setVisible(hasResults);
    }

    /**
     * Renders the full result snapshot asynchronously and swaps card list when
     * ready.
     *
     * @param results full result snapshot to render
     */
    private void renderAllResults(List<UserSearchResultDTO> results) {
        int generation = renderGeneration.incrementAndGet();

        if (results == null || results.isEmpty()) {
            resultsPane.getChildren().clear();
            return;
        }

        List<UserSearchResultDTO> snapshot = List.copyOf(results);
        Thread.ofVirtual().name("search-item-loader").start(() -> {
            List<javafx.scene.Node> loadedCards = new ArrayList<>();
            for (UserSearchResultDTO result : snapshot) {
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

    /**
     * Appends newly added result cards without rebuilding existing rendered cards.
     *
     * @param results newly added results
     */
    private void appendResults(List<UserSearchResultDTO> results) {
        int generation = renderGeneration.get();
        if (results == null || results.isEmpty()) {
            return;
        }

        List<UserSearchResultDTO> snapshot = List.copyOf(results);
        Thread.ofVirtual().name("search-item-loader-append").start(() -> {
            List<javafx.scene.Node> loadedCards = new ArrayList<>();
            for (UserSearchResultDTO result : snapshot) {
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

    /**
     * Injects contact/chat action callbacks used by search result buttons.
     *
     * @param contactActions action port used for add/remove/start-chat/open-profile operations
     */
    public void setContactActions(SearchContactActions contactActions) {
        this.contactActions = Objects.requireNonNull(contactActions, "contactActions");
    }

    /**
     * Fills in the fx:id nodes of a loaded search_result_item card.
     *
     * We look up nodes by their {@code fx:id} rather than coupling to a
     * sub-controller, keeping the design simple.
     *
     * @param card loaded result-card node
     * @param result data object to render inside the card
     */
    private void populateCard(javafx.scene.Node card, UserSearchResultDTO result) {
        populateTextElements(card, result);
        populateRankIcon(card, result);
        populateActionButtons(card, result);
        setupCardProfileOpen(card, result);
    }

    /**
     * Populates the text elements of a search result card.
     * 
     * @param card   The search result card.
     * @param result The search result.
     */
    private void populateTextElements(javafx.scene.Node card, UserSearchResultDTO result) {
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
    private void populateRankIcon(javafx.scene.Node card, UserSearchResultDTO result) {
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
    private void populateActionButtons(javafx.scene.Node card, UserSearchResultDTO result) {
        com.jfoenix.controls.JFXButton removeButton = (com.jfoenix.controls.JFXButton) card.lookup("#removeButton");
        com.jfoenix.controls.JFXButton startChatButton = (com.jfoenix.controls.JFXButton) card
                .lookup("#startChatButton");

        setupRemoveButton(removeButton, result);
        setupStartChatButton(startChatButton, removeButton, result);
    }

    /**
     * Enables profile-open behavior when users click on non-action areas of a card.
     *
     * @param card result card node
     * @param result represented user search result
     */
    private void setupCardProfileOpen(javafx.scene.Node card, UserSearchResultDTO result) {
        if (card == null) {
            return;
        }

        card.setCursor(Cursor.HAND);
        card.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }

            Node target = event.getPickResult() != null ? event.getPickResult().getIntersectedNode() : null;
            if (isWithinActionButton(target)) {
                return;
            }
            handleOpenProfile(result);
        });
    }

    /**
     * Checks whether a clicked node belongs to an action button subtree.
     *
     * @param node clicked node from pick result
     * @return {@code true} when click originated from remove/start-chat button hierarchy
     */
    private boolean isWithinActionButton(Node node) {
        Node cursor = node;
        while (cursor != null) {
            if (cursor instanceof com.jfoenix.controls.JFXButton button) {
                String buttonId = button.getId();
                if ("removeButton".equals(buttonId) || "startChatButton".equals(buttonId)) {
                    return true;
                }
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    /**
     * Sets up the remove button.
     * 
     * @param removeButton The remove button.
     * @param result       The search result.
     */
    private void setupRemoveButton(com.jfoenix.controls.JFXButton removeButton, UserSearchResultDTO result) {
        if (removeButton == null)
            return;

        updateToggleButtonText(removeButton, result.getUserId());

        removeButton.setOnAction(e -> {
            handleToggleContact(result, () -> updateToggleButtonText(removeButton, result.getUserId()));
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
            UserSearchResultDTO result) {
        if (startChatButton == null)
            return;

        startChatButton.setOnAction(e -> {
            handleStartChat(result);
            // Since starting a chat adds them to contacts, update the other button too.
            if (removeButton != null) {
                updateToggleButtonText(removeButton, result.getUserId());
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
    private void updateToggleButtonText(com.jfoenix.controls.JFXButton removeButton, String userId) {
        removeButton.setText(resolveToggleLabel(userId));
    }

    /**
     * Resolves add/remove label text for a user based on current contact state.
     *
     * @param userId user identifier to evaluate
     * @return UI label for toggle action button
     */
    String resolveToggleLabel(String userId) {
        SearchViewModel.ContactToggleAction action = viewModel
                .resolveContactToggleAction(contactActions.hasContact(userId));
        return viewModel.resolveContactToggleLabel(action);
    }

    /**
     * Toggles contact membership for a result without post-action callback.
     *
     * @param result search result whose contact state should be toggled
     */
    void handleToggleContact(UserSearchResultDTO result) {
        handleToggleContact(result, null);
    }

    /**
     * Toggles contact membership for a result and invokes optional completion
     * callback.
     *
     * @param result search result whose contact state should be toggled
     * @param onActionCompleted callback invoked after toggle action completes
     */
    private void handleToggleContact(UserSearchResultDTO result, Runnable onActionCompleted) {
        if (result == null) {
            return;
        }

        SearchViewModel.ContactToggleAction action = viewModel
                .resolveContactToggleAction(contactActions.hasContact(result.getUserId()));

        applyContactToggleAction(
                action,
                () -> {
                    contactActions.addContact(result);
                    LOGGER.log(Level.INFO, "Add contact requested for user: {0}", result.getUserId());
                    runIfPresent(onActionCompleted);
                },
                () -> confirmRemoveContact(result, onActionCompleted));
    }

    /**
     * Executes add/remove contact policy based on resolved toggle action.
     *
     * @param action resolved contact toggle action
     * @param addContactAction callback for add-contact branch
     * @param confirmRemoveContactAction callback for remove-contact branch
     */
    static void applyContactToggleAction(
            SearchViewModel.ContactToggleAction action,
            Runnable addContactAction,
            Runnable confirmRemoveContactAction) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(addContactAction, "addContactAction");
        Objects.requireNonNull(confirmRemoveContactAction, "confirmRemoveContactAction");

        switch (action) {
            case ADD_CONTACT -> addContactAction.run();
            case REMOVE_CONTACT -> confirmRemoveContactAction.run();
        }
    }

    /**
     * Displays a confirmation popup before removing an existing contact.
     *
     * @param result target contact represented as search result
     * @param onActionCompleted callback invoked after removal
     */
    private void confirmRemoveContact(UserSearchResultDTO result, Runnable onActionCompleted) {
        if (result == null || result.getUserId() == null || result.getUserId().isBlank()) {
            return;
        }
        String displayName = resolveContactDisplayName(result.getFullName(), result.getUserId());
        PopupMessageBuilder.create()
                .popupKey(UiConstants.POPUP_CONFIRM_REMOVE_CONTACT)
                .title("Remove contact")
                .message("Remove contact with " + displayName + " from your contacts list?")
                .actionText("Remove")
                .cancelText("Cancel")
                .dangerAction(true)
                .onAction(() -> {
                    contactActions.removeContact(result.getUserId());
                    LOGGER.log(Level.INFO, "Remove contact requested for user: {0}", result.getUserId());
                    runIfPresent(onActionCompleted);
                })
                .show();
    }

    /**
     * Resolves display name used in confirmation prompts.
     *
     * @param fullName full name candidate
     * @param userId fallback user id
     * @return best display label for the target contact
     */
    private static String resolveContactDisplayName(String fullName, String userId) {
        if (fullName != null && !fullName.isBlank()) {
            return fullName.trim();
        }
        if (userId != null && !userId.isBlank()) {
            return "user " + userId.trim();
        }
        return "this user";
    }

    /**
     * Executes callback only when non-null.
     *
     * @param action callback to execute
     */
    private static void runIfPresent(Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    /**
     * Delegates chat-start action to injected contact actions port.
     *
     * @param result selected search result
     */
    void handleStartChat(UserSearchResultDTO result) {
        if (result == null) {
            return;
        }
        LOGGER.log(Level.INFO, "Start chat requested for user: {0}", result.getUserId());
        contactActions.startChatWith(result);
    }

    /**
     * Delegates profile-open action to injected contact actions port.
     *
     * @param result selected search result
     */
    void handleOpenProfile(UserSearchResultDTO result) {
        if (result == null) {
            return;
        }
        LOGGER.log(Level.INFO, "Open profile requested for user: {0}", result.getUserId());
        contactActions.openProfile(result);
    }

    /**
     * Resolves a rank name to its icon resource path, following the existing
     * mapping in {@link UiConstants}.
     */
    private static final class RankIconResolver {

        /**
         * Prevents instantiation of this static icon mapping utility.
         */
        private RankIconResolver() {
        }

        /**
         * Maps a rank label to its corresponding icon path in {@link UiConstants}.
         *
         * @param rank rank label
         * @return icon resource path for the provided rank (or default icon path)
         */
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
