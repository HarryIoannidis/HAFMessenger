package com.haf.shared.dto;

import java.util.List;

public class ContactsResponse {
    private List<UserSearchResult> contacts;
    private String error;

    public ContactsResponse() {
        // Required for JSON deserialization
    }

    public List<UserSearchResult> getContacts() {
        return contacts;
    }

    public void setContacts(List<UserSearchResult> contacts) {
        this.contacts = contacts;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public static ContactsResponse success(List<UserSearchResult> contacts) {
        ContactsResponse response = new ContactsResponse();
        response.setContacts(contacts);
        return response;
    }

    public static ContactsResponse error(String errorMessage) {
        ContactsResponse response = new ContactsResponse();
        response.setError(errorMessage);
        return response;
    }
}
