package com.haf.shared.responses;

import com.haf.shared.dto.UserSearchResult;
import java.util.List;

/**
 * Response payload returned when loading a user's contact list.
 */
public class ContactsResponse {
    private List<UserSearchResult> contacts;
    private String error;

    /**
     * Creates an empty response instance for JSON deserialization.
     */
    public ContactsResponse() {
        // Required for JSON deserialization
    }

    /**
     * Returns the resolved contacts when the request succeeds.
     *
     * @return contact list, or {@code null} when the request fails
     */
    public List<UserSearchResult> getContacts() {
        return contacts;
    }

    /**
     * Sets the resolved contacts for a successful response.
     *
     * @param contacts contact list payload
     */
    public void setContacts(List<UserSearchResult> contacts) {
        this.contacts = contacts;
    }

    /**
     * Returns the error message when the request fails.
     *
     * @return error message, or {@code null} on success
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error message for a failed response.
     *
     * @param error failure reason
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Creates a successful contacts response.
     *
     * @param contacts resolved contact list
     * @return populated success response
     */
    public static ContactsResponse success(List<UserSearchResult> contacts) {
        ContactsResponse response = new ContactsResponse();
        response.setContacts(contacts);
        return response;
    }

    /**
     * Creates an error contacts response.
     *
     * @param errorMessage failure message
     * @return populated error response
     */
    public static ContactsResponse error(String errorMessage) {
        ContactsResponse response = new ContactsResponse();
        response.setError(errorMessage);
        return response;
    }
}
