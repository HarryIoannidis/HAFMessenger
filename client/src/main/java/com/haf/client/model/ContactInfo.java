package com.haf.client.model;

/**
 * Lightweight data holder for a contact list entry.
 *
 * @param name            Display name shown in the cell and the toolbar panel.
 * @param activenessLabel Short status string, e.g. "Online", "Offline".
 * @param activenessColor JavaFX CSS colour string for the status dot, e.g.
 *                        "#00b706".
 */
public record ContactInfo(String name, String activenessLabel, String activenessColor) {

    /** Convenience factory for an online contact. */
    public static ContactInfo online(String name) {
        return new ContactInfo(name, "Online", "#00b706");
    }

    /** Convenience factory for an offline contact. */
    public static ContactInfo offline(String name) {
        return new ContactInfo(name, "Offline", "#888888");
    }
}
