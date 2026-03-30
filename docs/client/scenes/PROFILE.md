# PROFILE

## Purpose

Document profile scene behavior for displaying user/contact details from current app state.

## Current Implementation

- Controller: `ProfileController`.
- View: `profile.fxml`.
- Invoked from main/search/chat flows when profile display is requested.
- Self-profile actions (`request edit`, `request deletion`) are currently stubbed with popup feedback.

## Key Types/Interfaces

- `client.controllers.ProfileController`
- `client.models.UserProfileInfo`
- `client.utils.ViewRouter`

## Flow

1. Caller opens profile scene/popup with selected profile data.
2. Controller binds profile fields and renders values/icons.
3. Controller shows/hides self-action controls based on `UserProfileInfo.selfProfile()`.
4. User returns to previous context after viewing.

## Error/Security Notes

- Profile rendering should tolerate partial data without exposing internal errors.
- Editable/destructive actions should remain explicitly gated.

## Related Files

- `client/src/main/resources/fxml/profile.fxml`
- `client/src/main/java/com/haf/client/controllers/ProfileController.java`
- `client/src/main/resources/css/profile.css`
