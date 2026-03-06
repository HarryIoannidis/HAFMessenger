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
    public List<UserSearchResult> results;
    public String error;

    public UserSearchResponse() {
    }

    /**
     * Convenience factory for a successful search response.
     */
    public static UserSearchResponse success(List<UserSearchResult> results) {
        UserSearchResponse r = new UserSearchResponse();
        r.results = results;
        return r;
    }

    /**
     * Convenience factory for an error response.
     */
    public static UserSearchResponse error(String error) {
        UserSearchResponse r = new UserSearchResponse();
        r.error = error;
        return r;
    }
}
