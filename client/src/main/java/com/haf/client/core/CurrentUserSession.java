package com.haf.client.core;

import com.haf.client.models.UserProfileInfo;

/**
 * Stores lightweight profile data for the authenticated user.
 */
public final class CurrentUserSession {

    private static UserProfileInfo profile;

    /**
     * Prevents instantiation of this static session holder.
     */
    private CurrentUserSession() {
    }

    /**
     * Stores the active user's profile information for global access.
     *
     * @param userProfileInfo the authenticated user's profile; may be {@code null} to clear session state
     */
    public static void set(UserProfileInfo userProfileInfo) {
        profile = userProfileInfo;
    }

    /**
     * Returns the currently stored user profile.
     *
     * @return the active {@link UserProfileInfo}, or {@code null} if no user is currently cached
     */
    public static UserProfileInfo get() {
        return profile;
    }

    /**
     * Clears the cached user profile from the current session.
     */
    public static void clear() {
        profile = null;
    }
}
