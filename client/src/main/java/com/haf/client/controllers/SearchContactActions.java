package com.haf.client.controllers;

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
        public void addContact(String userId, String fullName, String regNumber) {
            // Intentionally no-op: default port implementation when Search is not wired.
        }

        @Override
        public void removeContact(String userId) {
            // Intentionally no-op: default port implementation when Search is not wired.
        }

        @Override
        public void startChatWith(String userId, String fullName, String regNumber) {
            // Intentionally no-op: default port implementation when Search is not wired.
        }
    };

    boolean hasContact(String userId);

    void addContact(String userId, String fullName, String regNumber);

    void removeContact(String userId);

    void startChatWith(String userId, String fullName, String regNumber);
}
