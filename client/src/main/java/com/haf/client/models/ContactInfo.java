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

    private static final int DEFAULT_UNREAD_COUNT = 0;
    private static final String HIDDEN_ACTIVITY_LABEL = "Hidden Activity";

    /**
     * Canonical constructor that normalizes unread count to a non-negative value.
     *
     * @param id unique contact/user id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @param activenessLabel status label used in the UI
     * @param activenessColor status color used for the presence indicator
     * @param unreadCount local unread message count
     */
    public ContactInfo {
        if (unreadCount < 0) {
            unreadCount = 0;
        }
    }

    /** Convenience factory for an active contact. */
    public static ContactInfo online(String id, String name, String regNumber) {
        return online(id, name, regNumber, null, null, null, null);
    }

    /**
     * Creates an active contact with full profile fields and zero unread by
     * default.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @return active {@link ContactInfo} entry
     */
    public static ContactInfo online(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate) {
        return online(id, name, regNumber, rank, email, telephone, joinedDate, DEFAULT_UNREAD_COUNT);
    }

    /**
     * Creates an active contact with explicit unread count.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @param unreadCount unread messages for this contact
     * @return active {@link ContactInfo} entry
     */
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

    /**
     * Creates an inactive contact with full profile fields and default unread
     * state.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @return inactive {@link ContactInfo} entry
     */
    public static ContactInfo offline(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate) {
        return offline(id, name, regNumber, rank, email, telephone, joinedDate, DEFAULT_UNREAD_COUNT);
    }

    /**
     * Creates an inactive contact with explicit unread count.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @param unreadCount unread messages for this contact
     * @return inactive {@link ContactInfo} entry
     */
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

    /**
     * Creates a contact with unknown presence and default unread state.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @return unknown-presence {@link ContactInfo} entry
     */
    public static ContactInfo unknown(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate) {
        return unknown(id, name, regNumber, rank, email, telephone, joinedDate, DEFAULT_UNREAD_COUNT);
    }

    /**
     * Creates a contact with unknown presence and explicit unread count.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @param unreadCount unread messages for this contact
     * @return unknown-presence {@link ContactInfo} entry
     */
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

    /**
     * Creates a contact with hidden-presence activity state.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @param unreadCount unread messages for this contact
     * @return hidden-activity {@link ContactInfo} entry
     */
    public static ContactInfo hiddenActivity(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate,
            int unreadCount) {
        return new ContactInfo(
                id,
                name,
                regNumber,
                rank,
                email,
                telephone,
                joinedDate,
                HIDDEN_ACTIVITY_LABEL,
                "transparent",
                unreadCount);
    }

    /**
     * Returns hidden activity label literal used in UI.
     *
     * @return hidden activity label
     */
    public static String hiddenActivityLabel() {
        return HIDDEN_ACTIVITY_LABEL;
    }

    /**
     * Checks whether a status label represents hidden activity.
     *
     * @param label status label to evaluate
     * @return {@code true} when label resolves to hidden activity
     */
    public static boolean isHiddenActivityLabel(String label) {
        if (label == null) {
            return false;
        }
        return HIDDEN_ACTIVITY_LABEL.equalsIgnoreCase(label.trim());
    }

    /**
     * Alias for {@link #online(String, String, String)}.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @return active {@link ContactInfo} entry
     */
    public static ContactInfo active(String id, String name, String regNumber) {
        return online(id, name, regNumber);
    }

    /**
     * Alias for {@link #online(String, String, String, String, String, String, String)}.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @return active {@link ContactInfo} entry
     */
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

    /**
     * Alias for {@link #online(String, String, String, String, String, String, String, int)}.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @param unreadCount unread messages for this contact
     * @return active {@link ContactInfo} entry
     */
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

    /**
     * Alias for {@link #offline(String, String, String)}.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @return inactive {@link ContactInfo} entry
     */
    public static ContactInfo inactive(String id, String name, String regNumber) {
        return offline(id, name, regNumber);
    }

    /**
     * Alias for {@link #offline(String, String, String, String, String, String, String)}.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @return inactive {@link ContactInfo} entry
     */
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

    /**
     * Alias for {@link #offline(String, String, String, String, String, String, String, int)}.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @param unreadCount unread messages for this contact
     * @return inactive {@link ContactInfo} entry
     */
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

    /**
     * Creates contact info from a presence flag with minimal profile fields.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param active presence flag
     * @return active/inactive {@link ContactInfo} derived from presence
     */
    public static ContactInfo fromPresence(String id, String name, String regNumber, boolean active) {
        return fromPresence(id, name, regNumber, null, null, null, null, active);
    }

    /**
     * Creates contact info from a presence flag with full profile fields.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @param active presence flag
     * @return active/inactive {@link ContactInfo} derived from presence
     */
    public static ContactInfo fromPresence(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate,
            boolean active) {
        return fromPresence(
                id,
                name,
                regNumber,
                rank,
                email,
                telephone,
                joinedDate,
                active,
                false,
                DEFAULT_UNREAD_COUNT);
    }

    /**
     * Creates contact info from presence and explicit unread count.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @param active presence flag
     * @param unreadCount unread messages for this contact
     * @return active/inactive {@link ContactInfo} derived from presence
     */
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
        return fromPresence(id, name, regNumber, rank, email, telephone, joinedDate, active, false, unreadCount);
    }

    /**
     * Creates contact info from presence state, hidden flag, and explicit unread
     * count.
     *
     * @param id unique contact id
     * @param name display name
     * @param regNumber military registration number
     * @param rank rank label
     * @param email contact email
     * @param telephone contact telephone
     * @param joinedDate join date text
     * @param active visible presence flag
     * @param hidden {@code true} when presence is intentionally hidden
     * @param unreadCount unread messages for this contact
     * @return contact derived from effective presence visibility
     */
    public static ContactInfo fromPresence(
            String id,
            String name,
            String regNumber,
            String rank,
            String email,
            String telephone,
            String joinedDate,
            boolean active,
            boolean hidden,
            int unreadCount) {
        if (hidden) {
            return hiddenActivity(id, name, regNumber, rank, email, telephone, joinedDate, unreadCount);
        }
        return active
                ? active(id, name, regNumber, rank, email, telephone, joinedDate, unreadCount)
                : inactive(id, name, regNumber, rank, email, telephone, joinedDate, unreadCount);
    }

}
