package com.haf.client.models;

import com.haf.shared.dto.UserSearchResultDTO;

/**
 * View model for the profile popup.
 */
public record UserProfileInfo(
        String userId,
        String fullName,
        String rank,
        String regNumber,
        String joinedDate,
        String email,
        String telephone,
        boolean selfProfile) {

    /**
     * Builds popup profile data from a search result DTO.
     *
     * @param result search result item returned by the directory query
     * @param selfProfile {@code true} when the profile belongs to the logged-in user
     * @return mapped {@link UserProfileInfo}, or {@code null} when {@code result} is {@code null}
     */
    public static UserProfileInfo fromSearchResult(UserSearchResultDTO result, boolean selfProfile) {
        if (result == null) {
            return null;
        }
        return new UserProfileInfo(
                result.getUserId(),
                result.getFullName(),
                result.getRank(),
                result.getRegNumber(),
                result.getJoinedDate(),
                result.getEmail(),
                result.getTelephone(),
                selfProfile);
    }

    /**
     * Builds popup profile data from an existing contact entry.
     *
     * @param contact local contact entry selected by the user
     * @param selfProfile {@code true} when the profile belongs to the logged-in user
     * @return mapped {@link UserProfileInfo}, or {@code null} when {@code contact} is {@code null}
     */
    public static UserProfileInfo fromContact(ContactInfo contact, boolean selfProfile) {
        if (contact == null) {
            return null;
        }
        return new UserProfileInfo(
                contact.id(),
                contact.name(),
                contact.rank(),
                contact.regNumber(),
                contact.joinedDate(),
                contact.email(),
                contact.telephone(),
                selfProfile);
    }

    /**
     * Returns a copy of this profile with a different self-profile flag.
     *
     * @param selfProfile {@code true} when this profile should expose self-only actions
     * @return a new {@link UserProfileInfo} containing the same identity fields and updated self flag
     */
    public UserProfileInfo asSelfProfile(boolean selfProfile) {
        return new UserProfileInfo(userId, fullName, rank, regNumber, joinedDate, email, telephone, selfProfile);
    }
}
