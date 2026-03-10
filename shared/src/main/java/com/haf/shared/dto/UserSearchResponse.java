package com.haf.shared.dto;

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
    private List<UserSearchResult> results;
    private String error;

    public UserSearchResponse() {
        // Required for JSON deserialization
    }

    public List<UserSearchResult> getResults() {
        return results;
    }

    public void setResults(List<UserSearchResult> results) {
        this.results = results;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    /**
     * Convenience factory for a successful search response.
     */
    public static UserSearchResponse success(List<UserSearchResult> results) {
        UserSearchResponse r = new UserSearchResponse();
        r.setResults(results);
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
