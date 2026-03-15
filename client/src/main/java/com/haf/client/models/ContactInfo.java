package com.haf.client.models;

/**
 * Lightweight data holder for a contact list entry.
 *
 * @param name            Display name shown in the cell and the toolbar panel.
 * @param activenessLabel Short status string, e.g. "Online", "Offline".
 * @param activenessColor JavaFX CSS colour string for the status dot, e.g.
 *                        "#00b706".
 */
public record ContactInfo(String id, String name, String regNumber, String activenessLabel, String activenessColor) {

    /** Convenience factory for an active contact. */
    public static ContactInfo online(String id, String name, String regNumber) {
        return new ContactInfo(id, name, regNumber, "Active", "#00b706");
    }

    /** Convenience factory for an inactive contact. */
    public static ContactInfo offline(String id, String name, String regNumber) {
        return new ContactInfo(id, name, regNumber, "Inactive", "#ff0000");
    }

    public static ContactInfo active(String id, String name, String regNumber) {
        return online(id, name, regNumber);
    }

    public static ContactInfo inactive(String id, String name, String regNumber) {
        return offline(id, name, regNumber);
    }

    public static ContactInfo fromPresence(String id, String name, String regNumber, boolean active) {
        return active ? active(id, name, regNumber) : inactive(id, name, regNumber);
    }
}
