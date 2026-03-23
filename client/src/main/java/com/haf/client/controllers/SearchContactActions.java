package com.haf.client.controllers;

import com.haf.shared.dto.UserSearchResultDTO;

/**
 * Port for Search screen actions that affect contacts/chat state.
 */
public interface SearchContactActions {

    SearchContactActions NO_OP = new SearchContactActions() {
        /**
         * NO-OP implementation that always reports no existing contact.
         *
         * @param userId user identifier to check
         * @return always {@code false}
         */
        @Override
        public boolean hasContact(String userId) {
            return false;
        }

        /**
         * NO-OP implementation used when search actions are not wired.
         *
         * @param result search result selected by the user
         */
        @Override
        public void addContact(UserSearchResultDTO result) {
            // Intentionally no-op: default port implementation when Search is not wired.
        }

        /**
         * NO-OP implementation used when search actions are not wired.
         *
         * @param userId user identifier to remove from contacts
         */
        @Override
        public void removeContact(String userId) {
            // Intentionally no-op: default port implementation when Search is not wired.
        }

        /**
         * NO-OP implementation used when search actions are not wired.
         *
         * @param result search result selected to start a chat
         */
        @Override
        public void startChatWith(UserSearchResultDTO result) {
            // Intentionally no-op: default port implementation when Search is not wired.
        }

        /**
         * NO-OP implementation used when search actions are not wired.
         *
         * @param result search result selected to open profile details
         */
        @Override
        public void openProfile(UserSearchResultDTO result) {
            // Intentionally no-op: default port implementation when Search is not wired.
        }
    };

    /**
     * Checks whether the given user already exists in the local contacts list.
     *
     * @param userId user identifier to check
     * @return {@code true} when the contact already exists, otherwise {@code false}
     */
    boolean hasContact(String userId);

    /**
     * Adds the selected search result to local contacts.
     *
     * @param result selected search result to add
     */
    void addContact(UserSearchResultDTO result);

    /**
     * Removes a user from local contacts by id.
     *
     * @param userId user identifier to remove
     */
    void removeContact(String userId);

    /**
     * Starts a chat flow for the selected search result.
     *
     * @param result selected user to start chatting with
     */
    void startChatWith(UserSearchResultDTO result);

    /**
     * Opens the profile popup for the selected search result.
     *
     * @param result selected user profile to display
     */
    void openProfile(UserSearchResultDTO result);
}
