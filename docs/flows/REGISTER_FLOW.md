# REGISTER FLOW

## Purpose

Describe the implemented registration wizard and submission pipeline used by the client.

## Current Implementation

- Registration UI is a three-step wizard in `RegisterController`:
  - `CREDENTIALS`
  - `ID_PHOTO`
  - `SELFIE_PHOTO`
- UI state is bound to `RegisterViewModel` (inputs, error flags, loading state, password visibility toggles).
- Credentials validation is strict (required fields, `@haf.gr` email domain, phone format, password length/match).
- Photo steps support drag-and-drop and file picker input with image/type-size validation (`.png/.jpg/.jpeg`, max `10MB`).
- Final submission runs on a background daemon thread and delegates to `RegistrationService`.

## Key Types/Interfaces

- `client.controllers.RegisterController`
- `client.viewmodels.RegisterViewModel`
- `client.services.RegistrationService`
- `client.services.DefaultRegistrationService`
- `shared.requests.RegisterRequest`
- `shared.dto.EncryptedFileDTO`
- `shared.keystore.UserKeystore`

## Flow

1. User completes credentials step and passes `validateCredentials()`.
2. User uploads ID photo and selfie, each validated by `validateFile(...)` and step-specific presence checks.
3. Controller builds `RegistrationService.RegistrationCommand` from form fields and selected files.
4. `DefaultRegistrationService` generates an X25519 registration keypair and constructs `RegisterRequest`.
5. Service tries to fetch admin key from `/api/v1/config/admin-key`; if available, photos are encrypted into `EncryptedFileDTO` payloads.
6. Service submits `POST /api/v1/register`.
7. On success, service persists generated key material in local keystore and UI navigates back to login.

## Error/Security Notes

- Validation errors are field-specific and prevent step progression.
- Registration failures are surfaced as rejected/failure messages without exposing raw internal exceptions to users.
- Generated private key is stored sealed (`private.enc`) in user keystore roots resolved through `KeystoreRoot` policy.
- Password is reused as passphrase for local keystore sealing during registration success path.

## Related Files

- `client/src/main/java/com/haf/client/controllers/RegisterController.java`
- `client/src/main/java/com/haf/client/viewmodels/RegisterViewModel.java`
- `client/src/main/java/com/haf/client/services/DefaultRegistrationService.java`
- `client/src/main/resources/fxml/register.fxml`
- `shared/src/main/java/com/haf/shared/requests/RegisterRequest.java`
- `server/src/main/java/com/haf/server/ingress/HttpIngressServer.java`
