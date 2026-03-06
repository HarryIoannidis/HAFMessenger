package com.haf.shared.dto;

import java.io.Serializable;

/**
 * DTO representing a single user search result.
 */
public class UserSearchResult implements Serializable {
    public String userId;
    public String fullName;
    public String regNumber;
    public String email;
    public String rank;

    public UserSearchResult() {
    }

    /**
     * Convenience constructor for all fields.
     */
    public UserSearchResult(String userId, String fullName, String regNumber, String email, String rank) {
        this.userId = userId;
        this.fullName = fullName;
        this.regNumber = regNumber;
        this.email = email;
        this.rank = rank;
    }
}
