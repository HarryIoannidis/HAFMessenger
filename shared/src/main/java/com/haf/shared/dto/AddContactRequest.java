package com.haf.shared.dto;

public class AddContactRequest {
    private String contactId;

    public AddContactRequest() {
        // Required for JSON deserialization
    }

    public AddContactRequest(String contactId) {
        this.contactId = contactId;
    }

    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }
}
