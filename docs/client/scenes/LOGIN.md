# LOGIN

## Purpose

Document authentication scene behavior and UI/validation flow.

## Current Implementation

- Controller: `LoginController`.
- ViewModel: `LoginViewModel`.
- Service integration: `LoginService` (default implementation used in controller constructor).
- Supports password visibility toggle, remember-preference handling, and route transitions.

## Key Types/Interfaces

- `client.controllers.LoginController`
- `client.viewmodels.LoginViewModel`
- `client.services.LoginService`
- `client.utils.ViewRouter`

## Flow

1. User enters credentials and triggers sign-in.
2. Controller asks ViewModel to validate local input.
3. If valid, login service executes remote auth.
4. Success routes to main view; failure updates error message and field styling.
5. "Sign up" route opens registration view.

## Error/Security Notes

- Password is handled in-memory for request flow only.
- Loading state disables repeated submits.
- User-facing errors stay generic for auth failure paths.

## Related Files

- `client/src/main/resources/fxml/login.fxml`
- `client/src/main/java/com/haf/client/controllers/LoginController.java`
- `client/src/main/java/com/haf/client/viewmodels/LoginViewModel.java`
