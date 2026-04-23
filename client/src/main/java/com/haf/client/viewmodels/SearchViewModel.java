package com.haf.client.viewmodels;

import com.haf.client.core.AuthSessionState;
import com.haf.client.core.NetworkSession;
import com.haf.client.utils.RuntimeIssue;
import com.haf.client.utils.UiConstants;
import com.haf.shared.responses.UserSearchResponse;
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
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchViewModel.class);

    public interface SearchGateway {

        /**
         * Executes a paginated user-search request.
         *
         * @param query  search query text
         * @param limit  maximum number of results to fetch
         * @param cursor optional pagination cursor for next page
         * @return raw JSON search response payload
         * @throws IOException when request execution fails
         */
        String searchUsers(String query, int limit, String cursor) throws IOException;
    }

    public enum ContactToggleAction {
        ADD_CONTACT,
        REMOVE_CONTACT
    }

    private final SearchGateway searchGateway;
    private final BooleanSupplier sessionActiveSupplier;
    private final CopyOnWriteArrayList<Consumer<RuntimeIssue>> runtimeIssueListeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger generation = new AtomicInteger();
    private final ObservableList<UserSearchResult> results = FXCollections.observableArrayList();
    private final ObservableList<UserSearchResult> readOnlyResults = FXCollections
            .unmodifiableObservableList(results);
    private final StringProperty statusText = new SimpleStringProperty(STATUS_IDLE);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty loadingMore = new SimpleBooleanProperty(false);
    private final ReadOnlyBooleanWrapper hasResults = new ReadOnlyBooleanWrapper(false);
    private String activeQuery = "";
    private boolean hasMore;
    private String nextCursor;
    private SearchSortViewModel.SortOptions sortOptions = SearchSortViewModel.SortOptions.DEFAULT;
    private volatile int pageSize = UiConstants.SEARCH_PAGE_SIZE;

    /**
     * Creates search view-model with an injected gateway implementation.
     *
     * @param searchGateway gateway used to execute server-side search requests
     */
    public SearchViewModel(SearchGateway searchGateway) {
        this(searchGateway, () -> true);
    }

    /**
     * Creates search view-model with explicit session liveness policy.
     *
     * @param searchGateway         gateway used to execute server-side search
     *                              requests
     * @param sessionActiveSupplier supplier returning whether search failures
     *                              should be surfaced to UI
     */
    SearchViewModel(SearchGateway searchGateway, BooleanSupplier sessionActiveSupplier) {
        this.searchGateway = Objects.requireNonNull(searchGateway, "searchGateway");
        this.sessionActiveSupplier = Objects.requireNonNull(sessionActiveSupplier, "sessionActiveSupplier");
        hasResults.bind(Bindings.isNotEmpty(results));
    }

    /**
     * Factory using the current authenticated session.
     */
    public static SearchViewModel createDefault() {
        return new SearchViewModel(
                SearchViewModel::executeDefaultSearchRequest,
                SearchViewModel::isDefaultSessionActive);
    }

    /**
     * Executes search request using the default authenticated network session.
     *
     * @param query  search query text
     * @param limit  page size
     * @param cursor optional pagination cursor
     * @return raw JSON response body
     * @throws IOException when request execution fails
     */
    private static String executeDefaultSearchRequest(String query, int limit, String cursor) throws IOException {
        var adapter = NetworkSession.get();
        if (adapter == null) {
            throw new IllegalStateException("No active network session.");
        }

        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String path = "/api/v1/search?q=" + encoded + "&limit=" + limit;
        if (cursor != null && !cursor.isBlank()) {
            path += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }

        try {
            return adapter.getAuthenticated(path).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Search request was interrupted.", ex);
        } catch (ExecutionException ex) {
            throw mapExecutionFailureToIOException(ex);
        }
    }

    /**
     * Returns whether the default session-backed search transport is active.
     *
     * @return {@code true} when both network and auth session state are present
     */
    private static boolean isDefaultSessionActive() {
        return NetworkSession.get() != null && AuthSessionState.get() != null;
    }

    /**
     * Maps execution-wrapper failures into consistent IO exceptions.
     *
     * @param executionException wrapped execution failure
     * @return mapped IOException preserving cause details
     */
    private static IOException mapExecutionFailureToIOException(ExecutionException executionException) {
        Throwable cause = executionException.getCause();
        if (cause instanceof IOException ioException) {
            return ioException;
        }
        return new IOException("Search request failed.", cause != null ? cause : executionException);
    }

    /**
     * Starts an asynchronous user search.
     * 
     * @param query search query text
     */
    public void search(String query) {
        search(query, sortOptions);
    }

    /**
     * Starts an asynchronous user search with the provided sort options.
     * 
     * @param query       search query text
     * @param sortOptions sort options to apply
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

        Thread.ofPlatform().name("search-query").start(() -> runSearch(normalized, null, false, searchGeneration));
    }

    /**
     * Returns currently active sort options.
     *
     * @return active sort options
     */
    public SearchSortViewModel.SortOptions getSortOptions() {
        return sortOptions;
    }

    /**
     * Sets runtime search page size used for primary and pagination requests.
     *
     * @param pageSize requested page size
     */
    public void setPageSize(int pageSize) {
        this.pageSize = Math.clamp(pageSize, 10, 100);
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
        Thread.ofPlatform()
                .name("search-load-more")
                .start(() -> runSearch(query, cursor, true, searchGeneration));
    }

    /**
     * Retries the most recent non-empty search query with current sort options.
     */
    public void retryLastSearch() {
        String query = activeQuery == null ? "" : activeQuery.trim();
        if (query.isEmpty()) {
            return;
        }
        search(query, sortOptions);
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

    /**
     * Exposes read-only observable search results.
     *
     * @return observable list of current results
     */
    public ObservableList<UserSearchResult> resultsProperty() {
        return readOnlyResults;
    }

    /**
     * Exposes status text for the search screen.
     *
     * @return read-only status text property
     */
    public ReadOnlyStringProperty statusTextProperty() {
        return statusText;
    }

    /**
     * Exposes loading state used to disable controls/show progress.
     *
     * @return observable loading property
     */
    public BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Exposes whether the current result list is non-empty.
     *
     * @return read-only boolean property bound to list emptiness
     */
    public ReadOnlyBooleanProperty hasResultsProperty() {
        return hasResults.getReadOnlyProperty();
    }

    /**
     * Registers a listener for recoverable runtime issues.
     *
     * @param listener runtime issue listener
     */
    public void addRuntimeIssueListener(Consumer<RuntimeIssue> listener) {
        if (listener != null) {
            runtimeIssueListeners.add(listener);
        }
    }

    /**
     * Unregisters a previously registered runtime-issue listener.
     *
     * @param listener runtime issue listener
     */
    public void removeRuntimeIssueListener(Consumer<RuntimeIssue> listener) {
        if (listener != null) {
            runtimeIssueListeners.remove(listener);
        }
    }

    /**
     * Pure policy for contact toggle action.
     * 
     * @param alreadyInContacts whether the user is already in contacts
     * @return contact toggle action
     */
    public ContactToggleAction resolveContactToggleAction(boolean alreadyInContacts) {
        return alreadyInContacts ? ContactToggleAction.REMOVE_CONTACT : ContactToggleAction.ADD_CONTACT;
    }

    /**
     * Pure policy for contact toggle label text.
     * 
     * @param action contact toggle action
     * @return contact toggle label text
     */
    public String resolveContactToggleLabel(ContactToggleAction action) {
        return action == ContactToggleAction.REMOVE_CONTACT ? "Remove contact" : "Add contact";
    }

    /**
     * Executes a search request and routes results/errors back to the UI thread.
     *
     * @param query            query text
     * @param cursor           pagination cursor for incremental loading
     * @param append           whether this call appends to existing results
     * @param searchGeneration generation token used to drop stale responses
     */
    private void runSearch(String query, String cursor, boolean append, int searchGeneration) {
        try {
            UserSearchResponse response = requestSearchResponse(query, cursor);
            runOnUiThread(() -> applyResponse(response, append, searchGeneration));
        } catch (Exception ex) {
            handleSearchFailure(ex, append, searchGeneration);
        }
    }

    /**
     * Executes one search request and parses response JSON.
     *
     * @param query  search query text
     * @param cursor optional pagination cursor
     * @return parsed search response
     * @throws Exception when request or parsing fails
     */
    private UserSearchResponse requestSearchResponse(String query, String cursor) throws Exception {
        String json = searchGateway.searchUsers(query, pageSize, cursor);
        return JsonCodec.fromJson(json, UserSearchResponse.class);
    }

    /**
     * Handles search request failures with stale/session suppression and runtime
     * issue publishing.
     *
     * @param error            request failure
     * @param append           whether this failure happened during load-more flow
     * @param searchGeneration request generation token
     */
    private void handleSearchFailure(Exception error, boolean append, int searchGeneration) {
        if (shouldSuppressSearchFailure(error, searchGeneration)) {
            return;
        }

        LOGGER.warn("Search request failed", error);
        runOnUiThread(() -> applySearchFailureUiState(append, searchGeneration));
        publishSearchRequestFailureIssue(error, append);
    }

    /**
     * Returns whether a search failure should be ignored/suppressed.
     *
     * @param error            failure candidate
     * @param searchGeneration request generation token
     * @return {@code true} when failure should not be surfaced
     */
    private boolean shouldSuppressSearchFailure(Exception error, int searchGeneration) {
        if (isStaleGeneration(searchGeneration)) {
            LOGGER.debug("Ignoring stale search failure for generation {}", searchGeneration, error);
            return true;
        }
        if (!sessionActiveSupplier.getAsBoolean()) {
            LOGGER.debug("Suppressing search failure because session is no longer active", error);
            runOnUiThread(() -> clearStateAfterSessionEnded(searchGeneration));
            return true;
        }
        return false;
    }

    /**
     * Applies UI error state for a failed search call.
     *
     * @param append           whether failure belongs to load-more flow
     * @param searchGeneration request generation token
     */
    private void applySearchFailureUiState(boolean append, int searchGeneration) {
        if (isStaleGeneration(searchGeneration)) {
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
    }

    /**
     * Publishes runtime issue describing failed search request.
     *
     * @param error  search request failure
     * @param append whether failure belongs to load-more flow
     */
    private void publishSearchRequestFailureIssue(Exception error, boolean append) {
        publishRuntimeIssue(
                append ? "search.load-more.request.failed" : "search.request.failed",
                append ? "Could not load more results" : "Search failed",
                append
                        ? "Could not load more search results. " + resolveErrorMessage(error, "Please retry.")
                        : "Search request failed. " + resolveErrorMessage(error, "Please retry."),
                append ? this::loadMore : this::retryLastSearch);
    }

    /**
     * Clears visible search state when background request completes after session
     * teardown (for example, logout while a search request is still in-flight).
     *
     * @param searchGeneration generation token for staleness checks
     */
    private void clearStateAfterSessionEnded(int searchGeneration) {
        if (isStaleGeneration(searchGeneration)) {
            return;
        }
        loading.set(false);
        loadingMore.set(false);
        hasMore = false;
        nextCursor = null;
        results.clear();
        statusText.set(STATUS_IDLE);
    }

    /**
     * Returns whether a response/failure belongs to an obsolete search generation.
     *
     * @param searchGeneration generation token to evaluate
     * @return {@code true} when generation no longer matches current active search
     */
    private boolean isStaleGeneration(int searchGeneration) {
        return searchGeneration != generation.get();
    }

    /**
     * Applies a parsed search response to view-model state on the UI thread.
     *
     * @param response         parsed server response
     * @param append           whether this response is for pagination append
     * @param searchGeneration generation token used to ignore stale responses
     */
    private void applyResponse(UserSearchResponse response, boolean append, int searchGeneration) {
        if (searchGeneration != generation.get()) {
            return;
        }

        finishLoading(append);
        if (shouldStopAfterError(response, append)) {
            return;
        }

        List<UserSearchResult> responseResults = response.getResults();
        List<UserSearchResult> normalizedResults = responseResults == null ? List.of() : responseResults;

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

    /**
     * Clears loading flags after a search call completes.
     *
     * @param append whether current call represented load-more behavior
     */
    private void finishLoading(boolean append) {
        if (append) {
            loadingMore.set(false);
        } else {
            loading.set(false);
        }
    }

    /**
     * Applies error handling rules and indicates whether response processing should
     * stop.
     *
     * @param response parsed server response
     * @param append   whether response belongs to pagination append
     * @return {@code true} when processing should stop due to null/error response
     */
    private boolean shouldStopAfterError(UserSearchResponse response, boolean append) {
        if (response == null) {
            return handleNullResponse(append);
        }

        String error = response.getError();
        if (error != null) {
            return handleErrorResponse(error, append);
        }

        return false;
    }

    /**
     * Handles a null response by setting error status and publishing a runtime
     * issue.
     *
     * @param append whether this was a pagination append request
     * @return always {@code true}, indicating processing should stop
     */
    private boolean handleNullResponse(boolean append) {
        if (!append) {
            setErrorStatus(STATUS_GENERIC_FAILURE);
        }
        publishRuntimeIssue(
                append ? "search.load-more.response.empty" : "search.response.empty",
                append ? "Could not load more results" : "Search failed",
                append ? "Server returned an empty response while loading more results."
                        : "Search response was empty.",
                append ? this::loadMore : this::retryLastSearch);
        return true;
    }

    /**
     * Handles an error present in the response by setting error status and
     * publishing
     * a runtime issue.
     *
     * @param error  error message from the server response
     * @param append whether this was a pagination append request
     * @return always {@code true}, indicating processing should stop
     */
    private boolean handleErrorResponse(String error, boolean append) {
        if (!append) {
            setErrorStatus("Error: " + error);
        }
        publishRuntimeIssue(
                append ? "search.load-more.response.error" : "search.response.error",
                append ? "Could not load more results" : "Search failed",
                error,
                append ? this::loadMore : this::retryLastSearch);
        return true;
    }

    /**
     * Handles empty-result responses and updates status text accordingly.
     *
     * @param normalizedResults normalized result list from response
     * @param append            whether response belongs to pagination append
     * @return {@code true} when caller should stop further result processing
     */
    private boolean handleEmptyResults(List<UserSearchResult> normalizedResults, boolean append) {
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

    /**
     * Sets error status and clears current results.
     *
     * @param message status/error text to display
     */
    private void setErrorStatus(String message) {
        results.clear();
        statusText.set(message);
    }

    /**
     * Appends only unique users (by userId) to existing results.
     *
     * @param incoming incoming page of candidates
     */
    private void appendUnique(List<UserSearchResult> incoming) {
        Set<String> knownUserIds = new HashSet<>();
        for (UserSearchResult existing : results) {
            if (existing != null && existing.getUserId() != null) {
                knownUserIds.add(existing.getUserId());
            }
        }

        List<UserSearchResult> additions = new ArrayList<>();
        for (UserSearchResult candidate : incoming) {
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

    /**
     * Replaces current results with a sorted snapshot of incoming results.
     *
     * @param incoming incoming results to sort and apply
     */
    private void setSortedResults(List<UserSearchResult> incoming) {
        results.setAll(sortSnapshot(incoming));
    }

    /**
     * Sorts current results list in place using active sort options.
     */
    private void sortResultsInPlace() {
        if (results.size() < 2) {
            return;
        }

        List<UserSearchResult> sorted = sortSnapshot(results);
        if (!results.equals(sorted)) {
            results.setAll(sorted);
        }
    }

    /**
     * Creates a sorted copy of an input list using active comparator settings.
     *
     * @param incoming source list to sort
     * @return sorted copy (or empty immutable list when input is null/empty)
     */
    private List<UserSearchResult> sortSnapshot(List<UserSearchResult> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }

        List<UserSearchResult> sorted = new ArrayList<>(incoming);
        sorted.sort(SearchSortViewModel.comparator(sortOptions));
        return sorted;
    }

    /**
     * Executes a UI mutation safely on JavaFX thread, with fallback for test
     * environments.
     *
     * @param action UI mutation callback
     */
    private static void runOnUiThread(Runnable action) {
        try {
            Platform.runLater(action);
        } catch (IllegalStateException _) {
            action.run();
        }
    }

    /**
     * Publishes a recoverable runtime issue to registered listeners.
     *
     * @param dedupeKey   issue dedupe key
     * @param title       issue title
     * @param message     issue message
     * @param retryAction retry callback
     */
    private void publishRuntimeIssue(String dedupeKey, String title, String message, Runnable retryAction) {
        RuntimeIssue issue = new RuntimeIssue(dedupeKey, title, message, retryAction);
        for (Consumer<RuntimeIssue> listener : runtimeIssueListeners) {
            try {
                listener.accept(issue);
            } catch (Exception ignored) {
                // Listener failures should not block others.
            }
        }
    }

    /**
     * Extracts readable throwable message with fallback text.
     *
     * @param error    throwable to inspect
     * @param fallback fallback text
     * @return resolved message
     */
    private static String resolveErrorMessage(Throwable error, String fallback) {
        if (error == null) {
            return fallback;
        }
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message;
    }
}
