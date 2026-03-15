package com.haf.client.viewmodels;

import com.haf.client.core.NetworkSession;
import com.haf.shared.dto.UserSearchResponse;
import com.haf.shared.dto.UserSearchResult;
import com.haf.shared.utils.JsonCodec;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewModel for the search screen.
 *
 * Owns the async search flow and exposes simple observable state for the view:
 * status text, loading flag and search results.
 */
public class SearchViewModel {

    public static final String STATUS_IDLE = "Search for users by name or registration number.";
    public static final String STATUS_SEARCHING = "Searching...";
    public static final String STATUS_NO_RESULTS = "No results found.";
    public static final String STATUS_GENERIC_FAILURE = "Search failed. Please try again.";

    private static final Logger LOGGER = Logger.getLogger(SearchViewModel.class.getName());

    @FunctionalInterface
    public interface SearchGateway {
        String searchUsers(String query) throws Exception;
    }

    private final SearchGateway searchGateway;

    private final ObservableList<UserSearchResult> results = FXCollections.observableArrayList();
    private final ObservableList<UserSearchResult> readOnlyResults = FXCollections.unmodifiableObservableList(results);
    private final StringProperty statusText = new SimpleStringProperty(STATUS_IDLE);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final ReadOnlyBooleanWrapper hasResults = new ReadOnlyBooleanWrapper(false);

    public SearchViewModel(SearchGateway searchGateway) {
        this.searchGateway = Objects.requireNonNull(searchGateway, "searchGateway");
        hasResults.bind(Bindings.isNotEmpty(results));
    }

    /**
     * Factory using the current authenticated session.
     */
    public static SearchViewModel createDefault() {
        return new SearchViewModel(query -> {
            if (NetworkSession.get() == null) {
                throw new IllegalStateException("No active network session.");
            }

            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String path = "/api/v1/search?q=" + encoded;
            return NetworkSession.get().getAuthenticated(path).get();
        });
    }

    /**
     * Starts an asynchronous user search.
     */
    public void search(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            clearResults();
            return;
        }

        runOnUiThread(() -> {
            loading.set(true);
            statusText.set(STATUS_SEARCHING);
            results.clear();
        });

        Thread.ofVirtual().name("search-query").start(() -> runSearch(normalized));
    }

    /**
     * Clears results and restores the idle status prompt.
     */
    public void clearResults() {
        runOnUiThread(() -> {
            loading.set(false);
            results.clear();
            statusText.set(STATUS_IDLE);
        });
    }

    public ObservableList<UserSearchResult> resultsProperty() {
        return readOnlyResults;
    }

    public ReadOnlyStringProperty statusTextProperty() {
        return statusText;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public ReadOnlyBooleanProperty hasResultsProperty() {
        return hasResults.getReadOnlyProperty();
    }

    private void runSearch(String query) {
        try {
            String json = searchGateway.searchUsers(query);
            UserSearchResponse response = JsonCodec.fromJson(json, UserSearchResponse.class);
            runOnUiThread(() -> applyResponse(response));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Search request failed", ex);
            runOnUiThread(() -> {
                loading.set(false);
                results.clear();
                statusText.set(STATUS_GENERIC_FAILURE);
            });
        }
    }

    private void applyResponse(UserSearchResponse response) {
        loading.set(false);

        if (response == null) {
            results.clear();
            statusText.set(STATUS_GENERIC_FAILURE);
            return;
        }

        if (response.getError() != null) {
            results.clear();
            statusText.set("Error: " + response.getError());
            return;
        }

        List<UserSearchResult> responseResults = response.getResults();
        if (responseResults == null || responseResults.isEmpty()) {
            results.clear();
            statusText.set(STATUS_NO_RESULTS);
            return;
        }

        results.setAll(responseResults);
        statusText.set("");
    }

    private static void runOnUiThread(Runnable action) {
        try {
            Platform.runLater(action);
        } catch (IllegalStateException ex) {
            action.run();
        }
    }
}
