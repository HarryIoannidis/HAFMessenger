package com.haf.shared.dto;

import java.io.Serializable;

/**
 * DTO representing a single user search result.
 */
public class UserSearchResult implements Serializable {
    private String userId;
    private String fullName;
    private String regNumber;
    private String email;
    private String rank;

    public UserSearchResult() {
        // Required for JSON deserialization
    }

    public UserSearchResult(String userId, String fullName, String regNumber, String email, String rank) {
        this.userId = userId;
        this.fullName = fullName;
        this.regNumber = regNumber;
        this.email = email;
        this.rank = rank;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRegNumber() {
        return regNumber;
    }

    public void setRegNumber(String regNumber) {
        this.regNumber = regNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }
}
