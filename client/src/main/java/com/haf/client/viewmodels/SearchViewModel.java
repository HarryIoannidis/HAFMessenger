package com.haf.client.viewmodels;

import com.haf.client.core.NetworkSession;
import com.haf.client.utils.UiConstants;
import com.haf.shared.responses.UserSearchResponse;
import com.haf.shared.dto.UserSearchResultDTO;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ViewModel for the search screen.
 *
 * Owns the async search flow and exposes simple observable state for the view:
 * status text, loading flag and search results.
 */
public class SearchViewModel {

    public static final String STATUS_IDLE = "Search for users by name, registration number or rank.";
    public static final String STATUS_SEARCHING = "Searching...";
    public static final String STATUS_NO_RESULTS = "No results found.";
    public static final String STATUS_MIN_QUERY = "Enter at least "
            + UiConstants.SEARCH_MIN_QUERY_LENGTH + " characters to search.";
    public static final String STATUS_GENERIC_FAILURE = "Search failed. Please try again.";

    private static final Logger LOGGER = Logger.getLogger(SearchViewModel.class.getName());

    public interface SearchGateway {
        String searchUsers(String query, int limit, String cursor) throws IOException;
    }

    public enum ContactToggleAction {
        ADD_CONTACT,
        REMOVE_CONTACT
    }

    private final SearchGateway searchGateway;
    private final AtomicInteger generation = new AtomicInteger();

    private final ObservableList<UserSearchResultDTO> results = FXCollections.observableArrayList();
    private final ObservableList<UserSearchResultDTO> readOnlyResults = FXCollections
            .unmodifiableObservableList(results);
    private final StringProperty statusText = new SimpleStringProperty(STATUS_IDLE);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty loadingMore = new SimpleBooleanProperty(false);
    private final ReadOnlyBooleanWrapper hasResults = new ReadOnlyBooleanWrapper(false);
    private String activeQuery = "";
    private boolean hasMore;
    private String nextCursor;
    private SearchSortViewModel.SortOptions sortOptions = SearchSortViewModel.SortOptions.DEFAULT;

    public SearchViewModel(SearchGateway searchGateway) {
        this.searchGateway = Objects.requireNonNull(searchGateway, "searchGateway");
        hasResults.bind(Bindings.isNotEmpty(results));
    }

    /**
     * Factory using the current authenticated session.
     */
    public static SearchViewModel createDefault() {
        return new SearchViewModel((query, limit, cursor) -> {
            if (NetworkSession.get() == null) {
                throw new IllegalStateException("No active network session.");
            }

            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String path = "/api/v1/search?q=" + encoded + "&limit=" + limit;
            if (cursor != null && !cursor.isBlank()) {
                path += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
            }
            try {
                return NetworkSession.get().getAuthenticated(path).get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Search request was interrupted.", ex);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof IOException ioException) {
                    throw ioException;
                }
                throw new IOException("Search request failed.", cause != null ? cause : ex);
            }
        });
    }

    /**
     * Starts an asynchronous user search.
     */
    public void search(String query) {
        search(query, sortOptions);
    }

    /**
     * Starts an asynchronous user search with the provided sort options.
     */
    public void search(String query, SearchSortViewModel.SortOptions sortOptions) {
        this.sortOptions = SearchSortViewModel.normalize(sortOptions);

        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            clearResults();
            return;
        }
        if (normalized.length() < UiConstants.SEARCH_MIN_QUERY_LENGTH) {
            generation.incrementAndGet();
            runOnUiThread(() -> {
                loading.set(false);
                loadingMore.set(false);
                results.clear();
                statusText.set(STATUS_MIN_QUERY);
            });
            return;
        }

        int searchGeneration = generation.incrementAndGet();
        activeQuery = normalized;
        hasMore = false;
        nextCursor = null;

        runOnUiThread(() -> {
            loading.set(true);
            loadingMore.set(false);
            statusText.set(STATUS_SEARCHING);
            results.clear();
        });

        Thread.ofVirtual().name("search-query").start(() -> runSearch(normalized, null, false, searchGeneration));
    }

    public void setSortOptions(SearchSortViewModel.SortOptions sortOptions) {
        this.sortOptions = SearchSortViewModel.normalize(sortOptions);
        runOnUiThread(this::sortResultsInPlace);
    }

    public SearchSortViewModel.SortOptions getSortOptions() {
        return sortOptions;
    }

    /**
     * Loads the next page for the current query, if available.
     */
    public void loadMore() {
        if (loading.get() || loadingMore.get() || !hasMore || nextCursor == null || activeQuery.isBlank()) {
            return;
        }

        int searchGeneration = generation.get();
        String query = activeQuery;
        String cursor = nextCursor;

        runOnUiThread(() -> loadingMore.set(true));
        Thread.ofVirtual()
                .name("search-load-more")
                .start(() -> runSearch(query, cursor, true, searchGeneration));
    }

    /**
     * Clears results and restores the idle status prompt.
     */
    public void clearResults() {
        generation.incrementAndGet();
        activeQuery = "";
        hasMore = false;
        nextCursor = null;
        runOnUiThread(() -> {
            loading.set(false);
            loadingMore.set(false);
            results.clear();
            statusText.set(STATUS_IDLE);
        });
    }

    public ObservableList<UserSearchResultDTO> resultsProperty() {
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

    /**
     * Pure policy for contact toggle action.
     */
    public ContactToggleAction resolveContactToggleAction(boolean alreadyInContacts) {
        return alreadyInContacts ? ContactToggleAction.REMOVE_CONTACT : ContactToggleAction.ADD_CONTACT;
    }

    /**
     * Pure policy for contact toggle label text.
     */
    public String resolveContactToggleLabel(ContactToggleAction action) {
        return action == ContactToggleAction.REMOVE_CONTACT ? "Remove contact" : "Add contact";
    }

    private void runSearch(String query, String cursor, boolean append, int searchGeneration) {
        try {
            String json = searchGateway.searchUsers(query, UiConstants.SEARCH_PAGE_SIZE, cursor);
            UserSearchResponse response = JsonCodec.fromJson(json, UserSearchResponse.class);
            runOnUiThread(() -> applyResponse(response, append, searchGeneration));
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Search request failed", ex);
            runOnUiThread(() -> {
                if (searchGeneration != generation.get()) {
                    return;
                }

                if (append) {
                    loadingMore.set(false);
                    return;
                }

                loading.set(false);
                loadingMore.set(false);
                hasMore = false;
                nextCursor = null;
                results.clear();
                statusText.set(STATUS_GENERIC_FAILURE);
            });
        }
    }

    private void applyResponse(UserSearchResponse response, boolean append, int searchGeneration) {
        if (searchGeneration != generation.get()) {
            return;
        }

        finishLoading(append);
        if (shouldStopAfterError(response, append)) {
            return;
        }

        List<UserSearchResultDTO> responseResults = response.getResults();
        List<UserSearchResultDTO> normalizedResults = responseResults == null ? List.of() : responseResults;

        hasMore = response.isHasMore();
        nextCursor = hasMore ? response.getNextCursor() : null;

        if (handleEmptyResults(normalizedResults, append)) {
            return;
        }

        if (append) {
            appendUnique(normalizedResults);
            sortResultsInPlace();
        } else {
            setSortedResults(normalizedResults);
        }
        statusText.set("");
    }

    private void finishLoading(boolean append) {
        if (append) {
            loadingMore.set(false);
        } else {
            loading.set(false);
        }
    }

    private boolean shouldStopAfterError(UserSearchResponse response, boolean append) {
        if (response == null) {
            if (!append) {
                setErrorStatus(STATUS_GENERIC_FAILURE);
            }
            return true;
        }

        String error = response.getError();
        if (error != null) {
            if (!append) {
                setErrorStatus("Error: " + error);
            }
            return true;
        }

        return false;
    }

    private boolean handleEmptyResults(List<UserSearchResultDTO> normalizedResults, boolean append) {
        if (!normalizedResults.isEmpty()) {
            return false;
        }

        if (append) {
            return true;
        }

        results.clear();
        statusText.set(STATUS_NO_RESULTS);
        return true;
    }

    private void setErrorStatus(String message) {
        results.clear();
        statusText.set(message);
    }

    private void appendUnique(List<UserSearchResultDTO> incoming) {
        Set<String> knownUserIds = new HashSet<>();
        for (UserSearchResultDTO existing : results) {
            if (existing != null && existing.getUserId() != null) {
                knownUserIds.add(existing.getUserId());
            }
        }

        List<UserSearchResultDTO> additions = new ArrayList<>();
        for (UserSearchResultDTO candidate : incoming) {
            if (candidate == null) {
                continue;
            }
            String userId = candidate.getUserId();
            if (userId == null || knownUserIds.add(userId)) {
                additions.add(candidate);
            }
        }

        if (!additions.isEmpty()) {
            results.addAll(additions);
        }
    }

    private void setSortedResults(List<UserSearchResultDTO> incoming) {
        results.setAll(sortSnapshot(incoming));
    }

    private void sortResultsInPlace() {
        if (results.size() < 2) {
            return;
        }

        List<UserSearchResultDTO> sorted = sortSnapshot(results);
        if (!results.equals(sorted)) {
            results.setAll(sorted);
        }
    }

    private List<UserSearchResultDTO> sortSnapshot(List<UserSearchResultDTO> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }

        List<UserSearchResultDTO> sorted = new ArrayList<>(incoming);
        sorted.sort(SearchSortViewModel.comparator(sortOptions));
        return sorted;
    }

    private static void runOnUiThread(Runnable action) {
        try {
            Platform.runLater(action);
        } catch (IllegalStateException ex) {
            action.run();
        }
    }
}
