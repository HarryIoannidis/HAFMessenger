package com.haf.shared.responses;

import com.haf.shared.dto.UserSearchResult;
import java.io.Serializable;
import java.util.List;

/**
 * Response wrapper for user search results.
 *
 * On success: {@code results} contains the matches and {@code error} is null.
 * On failure: {@code error} contains the reason and {@code results} is null.
 */
public class UserSearchResponse implements Serializable {
    private List<UserSearchResult> results;
    private String error;
    private boolean hasMore;
    private String nextCursor;

    /**
     * Creates an empty search response for JSON deserialization.
     */
    public UserSearchResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns the user search results for a successful request.
     *
     * @return search results, or {@code null} when the request fails
     */
    public List<UserSearchResult> getResults() {
        return results;
    }

    /**
     * Sets the user search results for a successful request.
     *
     * @param results user search matches
     */
    public void setResults(List<UserSearchResult> results) {
        this.results = results;
    }

    /**
     * Returns the error message for failed searches.
     *
     * @return error message, or {@code null} on success
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error message for failed searches.
     *
     * @param error failure reason
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Indicates whether more result pages are available.
     *
     * @return {@code true} when another page can be requested
     */
    public boolean isHasMore() {
        return hasMore;
    }

    /**
     * Sets whether more result pages are available.
     *
     * @param hasMore pagination continuation flag
     */
    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    /**
     * Returns the pagination cursor for the next page.
     *
     * @return next-page cursor, or {@code null} when no further page exists
     */
    public String getNextCursor() {
        return nextCursor;
    }

    /**
     * Sets the pagination cursor for the next page.
     *
     * @param nextCursor next-page cursor value
     */
    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    /**
     * Convenience factory for a successful search response.
     *
     * @param results search matches for the first/only page
     * @return populated success response with pagination disabled
     */
    public static UserSearchResponse success(List<UserSearchResult> results) {
        UserSearchResponse r = new UserSearchResponse();
        r.setResults(results);
        r.setHasMore(false);
        r.setNextCursor(null);
        return r;
    }

    /**
     * Convenience factory for a successful paginated search response.
     *
     * @param results    search matches for the current page
     * @param hasMore    pagination continuation flag
     * @param nextCursor cursor to request the next page
     * @return populated success response
     */
    public static UserSearchResponse success(List<UserSearchResult> results, boolean hasMore, String nextCursor) {
        UserSearchResponse r = new UserSearchResponse();
        r.setResults(results);
        r.setHasMore(hasMore);
        r.setNextCursor(nextCursor);
        return r;
    }

    /**
     * Convenience factory for an error response.
     *
     * @param error failure reason
     * @return populated error response
     */
    public static UserSearchResponse error(String error) {
        UserSearchResponse r = new UserSearchResponse();
        r.setError(error);
        return r;
    }
}
