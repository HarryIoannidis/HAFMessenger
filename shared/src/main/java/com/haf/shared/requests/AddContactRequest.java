package com.haf.shared.requests;

/**
 * Request payload for adding a user to the caller's contacts.
 */
public class AddContactRequest {
    private String contactId;

    /**
     * Creates an empty contact request for JSON deserialization.
     */
    public AddContactRequest() {
        // Required for JSON deserialization
    }

    /**
     * Creates a request targeting the provided contact identifier.
     *
     * @param contactId user identifier to add as a contact
     */
    public AddContactRequest(String contactId) {
        this.contactId = contactId;
    }

    /**
     * Returns the identifier of the contact to add.
     *
     * @return target contact identifier
     */
    public String getContactId() {
        return contactId;
    }

    /**
     * Sets the identifier of the contact to add.
     *
     * @param contactId target contact identifier
     */
    public void setContactId(String contactId) {
        this.contactId = contactId;
    }
}
