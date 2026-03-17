package com.haf.shared.responses;

import com.haf.shared.dto.UserSearchResultDTO;

import java.io.Serializable;
import java.util.List;

/**
 * Response wrapper for user search results.
 *
 * <p>
 * On success: {@code results} contains the matches and {@code error} is null.
 * On failure: {@code error} contains the reason and {@code results} is null.
 * </p>
 */
public class UserSearchResponse implements Serializable {
    private List<UserSearchResultDTO> results;
    private String error;
    private boolean hasMore;
    private String nextCursor;

    public UserSearchResponse() {
        // Required for JSON deserialization
    }

    public List<UserSearchResultDTO> getResults() {
        return results;
    }

    public void setResults(List<UserSearchResultDTO> results) {
        this.results = results;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    /**
     * Convenience factory for a successful search response.
     */
    public static UserSearchResponse success(List<UserSearchResultDTO> results) {
        UserSearchResponse r = new UserSearchResponse();
        r.setResults(results);
        r.setHasMore(false);
        r.setNextCursor(null);
        return r;
    }

    /**
     * Convenience factory for a successful paginated search response.
     */
    public static UserSearchResponse success(List<UserSearchResultDTO> results, boolean hasMore, String nextCursor) {
        UserSearchResponse r = new UserSearchResponse();
        r.setResults(results);
        r.setHasMore(hasMore);
        r.setNextCursor(nextCursor);
        return r;
    }

    /**
     * Convenience factory for an error response.
     */
    public static UserSearchResponse error(String error) {
        UserSearchResponse r = new UserSearchResponse();
        r.setError(error);
        return r;
    }
}
