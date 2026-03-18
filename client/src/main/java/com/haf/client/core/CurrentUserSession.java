package com.haf.client.core;

import com.haf.client.models.UserProfileInfo;

/**
 * Stores lightweight profile data for the authenticated user.
 */
public final class CurrentUserSession {

    private static UserProfileInfo profile;

    private CurrentUserSession() {
    }

    public static void set(UserProfileInfo userProfileInfo) {
        profile = userProfileInfo;
    }

    public static UserProfileInfo get() {
        return profile;
    }

    public static void clear() {
        profile = null;
    }
}
