package com.haf.shared.responses;

import java.io.Serializable;

/**
 * Login response DTO returned by the server.
 *
 * On success: profile fields are populated and {@code error} is null.
 * On failure: {@code error} contains the reason and other fields are null.
 */
public class LoginResponse implements Serializable {
    private String userId;
    private String sessionId;
    private String fullName;
    private String rank;
    private String regNumber;
    private String email;
    private String telephone;
    private String joinedDate;
    private String status;
    private String error;

    /**
     * Creates an empty login response DTO for JSON deserialization.
     */
    public LoginResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns authenticated user id.
     *
     * @return authenticated user id
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets authenticated user id.
     *
     * @param userId authenticated user id
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns server session id.
     *
     * @return session id
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets server session id.
     *
     * @param sessionId session id
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Returns full display name of the authenticated user.
     *
     * @return full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets full display name of the authenticated user.
     *
     * @param fullName full name
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Returns rank value assigned to the user.
     *
     * @return rank
     */
    public String getRank() {
        return rank;
    }

    /**
     * Sets rank value assigned to the user.
     *
     * @param rank rank value
     */
    public void setRank(String rank) {
        this.rank = rank;
    }

    /**
     * Returns user account status.
     *
     * @return account status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets user account status.
     *
     * @param status account status
     */
    public void setStatus(String status) {
        this.status = status;
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
     * Returns user email.
     *
     * @return user email
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets user email.
     *
     * @param email user email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns user telephone value.
     *
     * @return user telephone
     */
    public String getTelephone() {
        return telephone;
    }

    /**
     * Sets user telephone value.
     *
     * @param telephone user telephone
     */
    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    /**
     * Returns user joined-date string.
     *
     * @return joined-date string
     */
    public String getJoinedDate() {
        return joinedDate;
    }

    /**
     * Sets user joined-date string.
     *
     * @param joinedDate joined-date string
     */
    public void setJoinedDate(String joinedDate) {
        this.joinedDate = joinedDate;
    }

    /**
     * Returns error text for failed login responses.
     *
     * @return error text
     */
    public String getError() {
        return error;
    }

    /**
     * Sets error text for failed login responses.
     *
     * @param error error text
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Creates a successful login response with core profile fields.
     *
     * @param userId authenticated user id
     * @param sessionId created session id
     * @param fullName full name
     * @param rank rank value
     * @param status account status
     * @return populated success response
     */
    public static LoginResponse success(String userId, String sessionId,
            String fullName, String rank, String status) {
        return success(userId, sessionId, fullName, rank, null, null, null, null, status);
    }

    /**
     * Creates a successful login response with full profile payload.
     *
     * @param userId authenticated user id
     * @param sessionId created session id
     * @param fullName full name
     * @param rank rank value
     * @param regNumber registration number
     * @param email email
     * @param telephone telephone
     * @param joinedDate joined-date string
     * @param status account status
     * @return populated success response
     */
    public static LoginResponse success(
            String userId,
            String sessionId,
            String fullName,
            String rank,
            String regNumber,
            String email,
            String telephone,
            String joinedDate,
            String status) {
        LoginResponse r = new LoginResponse();
        r.setUserId(userId);
        r.setSessionId(sessionId);
        r.setFullName(fullName);
        r.setRank(rank);
        r.setRegNumber(regNumber);
        r.setEmail(email);
        r.setTelephone(telephone);
        r.setJoinedDate(joinedDate);
        r.setStatus(status);
        return r;
    }

    /**
     * Creates a failed login response.
     *
     * @param error error text
     * @return populated error response
     */
    public static LoginResponse error(String error) {
        LoginResponse r = new LoginResponse();
        r.setError(error);
        return r;
    }
}
