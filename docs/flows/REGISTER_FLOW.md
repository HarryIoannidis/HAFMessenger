# REGISTER FLOW

## Purpose

Describe the implemented registration wizard and submission pipeline used by the client.

## Current Implementation

- Registration UI is a three-step wizard in `RegisterController`:
  - `CREDENTIALS`
  - `ID_PHOTO`
  - `SELFIE_PHOTO`
- UI state is bound to `RegisterViewModel` (inputs, error flags, loading state, password visibility toggles).
- Credentials validation is strict (required fields, `@haf.gr` email domain, phone format, and strong password policy: at least 8 chars + uppercase + number + special).
- Password field shows live strength feedback while typing (`Weak`/`Medium`/`Strong`) with red/orange/green outline and matching label color.
- Photo steps support drag-and-drop and file picker input with image/type-size validation (`.png/.jpg/.jpeg`, max `10MB`).
- Final submission runs on a background daemon thread and delegates to `RegistrationService`.

## Key Types/Interfaces

- `client.controllers.RegisterController`
- `client.viewmodels.RegisterViewModel`
- `client.services.RegistrationService`
- `client.services.DefaultRegistrationService`
- `shared.requests.RegisterRequest`
- `shared.dto.EncryptedFile`
- `shared.keystore.UserKeystore`

## Flow

1. User completes credentials step and passes `validateCredentials()`.
2. User uploads ID photo and selfie, each validated by `validateFile(...)` and step-specific presence checks.
3. Controller builds `RegistrationService.RegistrationCommand` from form fields and selected files.
4. `DefaultRegistrationService` generates both keypairs:
   - X25519 encryption keypair
   - Ed25519 signing keypair
   and constructs `RegisterRequest` with both public keys + fingerprints.
5. Service tries to fetch admin key from the configured REST path (default `/api/v1/config/admin-key`); if available, photos are encrypted into `EncryptedFile` payloads.
6. Service submits `POST` to the registration endpoint (default `/api/v1/register`).
7. On success, service persists both keypairs in local keystore and UI navigates back to login.

## Error/Security Notes

- Validation errors are field-specific and prevent step progression.
- Confirm-password remains button-click validated; no live strength behavior is applied to confirmation input.
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
