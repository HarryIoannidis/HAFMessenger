# LOGIN

## Purpose

Document authentication scene behavior and UI/validation flow.

## Current Implementation

- Controller: `LoginController`.
- ViewModel: `LoginViewModel`.
- Service integration: `LoginService` (default implementation used in controller constructor).
- Supports password visibility toggle, secure remember-credentials handling, and route transitions.

## Key Types/Interfaces

- `client.controllers.LoginController`
- `client.viewmodels.LoginViewModel`
- `client.services.LoginService`
- `client.utils.ViewRouter`
- `client.security.RememberedCredentialsStore`
- `client.security.SecurePasswordVault`

## Flow

1. On initialize, controller loads remembered email/password via `RememberedCredentialsStore` and pre-fills fields when enabled.
2. If remember-prefill is applied, focus is moved to the `Sign In` button for immediate keyboard submit.
3. User enters/edits credentials and triggers sign-in.
4. Controller asks ViewModel to validate local input.
5. If valid, login service executes remote auth.
6. Success routes to main view and persists remember state; failure updates error message and field styling.
7. "Sign up" route opens registration view.

## Error/Security Notes

- Password is handled in-memory for request flow and stored at rest only in OS secure vault when remember-credentials is enabled.
- If secure vault access is unavailable, remember mode degrades to email-only prefill (no plaintext password persistence).
- Loading state disables repeated submits.
- User-facing errors stay generic for auth failure paths.

## Related Files

- `client/src/main/resources/fxml/login.fxml`
- `client/src/main/java/com/haf/client/controllers/LoginController.java`
- `client/src/main/java/com/haf/client/viewmodels/LoginViewModel.java`
- `client/src/main/java/com/haf/client/security/RememberedCredentialsStore.java`
- `client/src/main/java/com/haf/client/security/SecurePasswordVault.java`
