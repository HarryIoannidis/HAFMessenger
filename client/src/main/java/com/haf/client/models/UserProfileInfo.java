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

    public UserProfileInfo asSelfProfile(boolean selfProfile) {
        return new UserProfileInfo(userId, fullName, rank, regNumber, joinedDate, email, telephone, selfProfile);
    }
}
