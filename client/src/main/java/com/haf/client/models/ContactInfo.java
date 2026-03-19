package com.haf.client.models;

/**
 * Lightweight data holder for a contact list entry.
 *
 * @param name            Display name shown in the cell and the toolbar panel.
 * @param rank            Rank text for profile popup.
 * @param email           Email shown in profile popup.
 * @param telephone       Phone number shown in profile popup.
 * @param joinedDate      Joined date shown in profile popup.
 * @param activenessLabel Short status string, e.g. "Online", "Offline".
 * @param activenessColor JavaFX CSS colour string for the status dot, e.g.
 *                        "#00b706".
 * @param unreadCount     Local unread counter for this contact. Non-negative.
 */
public record ContactInfo(
        String id,
        String name,
        String regNumber,
        String rank,
        String email,
        String telephone,
        String joinedDate,
        String activenessLabel,
        String activenessColor,
        int unreadCount) {

    public ContactInfo {
        if (unreadCount < 0) {
            unreadCount = 0;
        }
    }

    /** Convenience factory for an active contact. */
    public static ContactInfo online(String id, String name, String regNumber) {
        return online(id, name, regNumber, null, null, null, null);
    }

    public static ContactInfo online(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate) {
        return online(id, name, regNumber, rank, email, telephone, joinedDate, 0);
    }

    public static ContactInfo online(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate,
            int unreadCount) {
        return new ContactInfo(id, name, regNumber, rank, email, telephone, joinedDate, "Active", "#00b706", unreadCount);
    }

    /** Convenience factory for an inactive contact. */
    public static ContactInfo offline(String id, String name, String regNumber) {
        return offline(id, name, regNumber, null, null, null, null);
    }

    public static ContactInfo offline(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate) {
        return offline(id, name, regNumber, rank, email, telephone, joinedDate, unreadCountForNewContact());
    }

    public static ContactInfo offline(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate,
            int unreadCount) {
        return new ContactInfo(id, name, regNumber, rank, email, telephone, joinedDate, "Inactive", "#ff0000", unreadCount);
    }

    /**
     * Neutral pre-presence state used for optimistic inserts.
     * Dot/text stay hidden until a concrete presence update arrives.
     */
    public static ContactInfo unknown(String id, String name, String regNumber) {
        return unknown(id, name, regNumber, null, null, null, null);
    }

    public static ContactInfo unknown(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate) {
        return unknown(id, name, regNumber, rank, email, telephone, joinedDate, unreadCountForNewContact());
    }

    public static ContactInfo unknown(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate,
            int unreadCount) {
        return new ContactInfo(id, name, regNumber, rank, email, telephone, joinedDate, "", "transparent", unreadCount);
    }

    public static ContactInfo active(String id, String name, String regNumber) {
        return online(id, name, regNumber);
    }

    public static ContactInfo active(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate) {
        return online(id, name, regNumber, rank, email, telephone, joinedDate);
    }

    public static ContactInfo active(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate,
            int unreadCount) {
        return online(id, name, regNumber, rank, email, telephone, joinedDate, unreadCount);
    }

    public static ContactInfo inactive(String id, String name, String regNumber) {
        return offline(id, name, regNumber);
    }

    public static ContactInfo inactive(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate) {
        return offline(id, name, regNumber, rank, email, telephone, joinedDate);
    }

    public static ContactInfo inactive(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate,
            int unreadCount) {
        return offline(id, name, regNumber, rank, email, telephone, joinedDate, unreadCount);
    }

    public static ContactInfo fromPresence(String id, String name, String regNumber, boolean active) {
        return fromPresence(id, name, regNumber, null, null, null, null, active);
    }

    public static ContactInfo fromPresence(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate,
            boolean active) {
        return fromPresence(id, name, regNumber, rank, email, telephone, joinedDate, active, unreadCountForNewContact());
    }

    public static ContactInfo fromPresence(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate,
            boolean active,
            int unreadCount) {
        return active
                ? active(id, name, regNumber, rank, email, telephone, joinedDate, unreadCount)
                : inactive(id, name, regNumber, rank, email, telephone, joinedDate, unreadCount);
    }

    private static int unreadCountForNewContact() {
        return 0;
    }
}
