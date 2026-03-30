package com.haf.shared.dto;

import java.io.Serializable;

/**
 * DTO representing a single user search result.
 */
public class UserSearchResultDTO implements Serializable {
    private String userId;
    private String fullName;
    private String regNumber;
    private String email;
    private String rank;
    private String telephone;
    private String joinedDate;
    private boolean active;
    private boolean presenceHidden;

    /**
     * Creates an empty search-result DTO for JSON deserialization.
     */
    public UserSearchResultDTO() {
        // Required for JSON deserialization
    }

    /**
     * Creates a search-result DTO with core identity fields.
     *
     * @param userId user id
     * @param fullName full name
     * @param regNumber registration number
     * @param email email address
     * @param rank rank value
     */
    public UserSearchResultDTO(String userId, String fullName, String regNumber, String email, String rank) {
        this(userId, fullName, regNumber, email, rank, null, null, false, false);
    }

    /**
     * Creates a search-result DTO with core identity fields and presence state.
     *
     * @param userId user id
     * @param fullName full name
     * @param regNumber registration number
     * @param email email address
     * @param rank rank value
     * @param active active presence flag
     */
    public UserSearchResultDTO(String userId, String fullName, String regNumber, String email, String rank, boolean active) {
        this(userId, fullName, regNumber, email, rank, null, null, active, false);
    }

    /**
     * Creates a search-result DTO with profile fields.
     *
     * @param userId user id
     * @param fullName full name
     * @param regNumber registration number
     * @param email email address
     * @param rank rank value
     * @param telephone telephone number
     * @param joinedDate joined-date text
     */
    public UserSearchResultDTO(
            String userId,
            String fullName,
            String regNumber,
            String email,
            String rank,
            String telephone,
            String joinedDate) {
        this(userId, fullName, regNumber, email, rank, telephone, joinedDate, false, false);
    }

    /**
     * Creates a fully populated search-result DTO.
     *
     * @param userId user id
     * @param fullName full name
     * @param regNumber registration number
     * @param email email address
     * @param rank rank value
     * @param telephone telephone number
     * @param joinedDate joined-date text
     * @param active active presence flag
     */
    public UserSearchResultDTO(
            String userId,
            String fullName,
            String regNumber,
            String email,
            String rank,
            String telephone,
            String joinedDate,
            boolean active) {
        this(userId, fullName, regNumber, email, rank, telephone, joinedDate, active, false);
    }

    /**
     * Creates a fully populated search-result DTO with explicit presence-privacy
     * state.
     *
     * @param userId user id
     * @param fullName full name
     * @param regNumber registration number
     * @param email email address
     * @param rank rank value
     * @param telephone telephone number
     * @param joinedDate joined-date text
     * @param active active presence flag
     * @param presenceHidden {@code true} when presence is intentionally hidden
     */
    public UserSearchResultDTO(
            String userId,
            String fullName,
            String regNumber,
            String email,
            String rank,
            String telephone,
            String joinedDate,
            boolean active,
            boolean presenceHidden) {
        this.userId = userId;
        this.fullName = fullName;
        this.regNumber = regNumber;
        this.email = email;
        this.rank = rank;
        this.telephone = telephone;
        this.joinedDate = joinedDate;
        this.active = active;
        this.presenceHidden = presenceHidden;
    }

    /**
     * Returns user id.
     *
     * @return user id
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets user id.
     *
     * @param userId user id
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns full name.
     *
     * @return full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets full name.
     *
     * @param fullName full name
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Returns registration number.
     *
     * @return registration number
     */
    public String getRegNumber() {
        return regNumber;
    }

    /**
     * Sets registration number.
     *
     * @param regNumber registration number
     */
    public void setRegNumber(String regNumber) {
        this.regNumber = regNumber;
    }

    /**
     * Returns email address.
     *
     * @return email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets email address.
     *
     * @param email email address
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns rank value.
     *
     * @return rank value
     */
    public String getRank() {
        return rank;
    }

    /**
     * Sets rank value.
     *
     * @param rank rank value
     */
    public void setRank(String rank) {
        this.rank = rank;
    }

    /**
     * Returns telephone number.
     *
     * @return telephone number
     */
    public String getTelephone() {
        return telephone;
    }

    /**
     * Sets telephone number.
     *
     * @param telephone telephone number
     */
    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    /**
     * Returns joined-date text.
     *
     * @return joined-date text
     */
    public String getJoinedDate() {
        return joinedDate;
    }

    /**
     * Sets joined-date text.
     *
     * @param joinedDate joined-date text
     */
    public void setJoinedDate(String joinedDate) {
        this.joinedDate = joinedDate;
    }

    /**
     * Returns whether the user is currently active.
     *
     * @return {@code true} when user is active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Sets whether the user is currently active.
     *
     * @param active active presence flag
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns whether the user is hiding presence details.
     *
     * @return {@code true} when presence is hidden
     */
    public boolean isPresenceHidden() {
        return presenceHidden;
    }

    /**
     * Sets whether the user is hiding presence details.
     *
     * @param presenceHidden hidden-presence flag
     */
    public void setPresenceHidden(boolean presenceHidden) {
        this.presenceHidden = presenceHidden;
    }
}
