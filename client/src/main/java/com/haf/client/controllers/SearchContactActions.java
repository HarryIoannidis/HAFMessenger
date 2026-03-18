package com.haf.client.controllers;

import com.haf.shared.dto.UserSearchResultDTO;

/**
 * Port for Search screen actions that affect contacts/chat state.
 */
public interface SearchContactActions {

    SearchContactActions NO_OP = new SearchContactActions() {
        @Override
        public boolean hasContact(String userId) {
            return false;
        }

        @Override
        public void addContact(UserSearchResultDTO result) {
            // Intentionally no-op: default port implementation when Search is not wired.
        }

        @Override
        public void removeContact(String userId) {
            // Intentionally no-op: default port implementation when Search is not wired.
        }

        @Override
        public void startChatWith(UserSearchResultDTO result) {
            // Intentionally no-op: default port implementation when Search is not wired.
        }

        @Override
        public void openProfile(UserSearchResultDTO result) {
            // Intentionally no-op: default port implementation when Search is not wired.
        }
    };

    boolean hasContact(String userId);

    void addContact(UserSearchResultDTO result);

    void removeContact(String userId);

    void startChatWith(UserSearchResultDTO result);

    void openProfile(UserSearchResultDTO result);
}
