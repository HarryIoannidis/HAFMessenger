# REGISTER

## Purpose
Describe registration scene behavior including credential collection and encrypted photo submission.

## Current Implementation
- Controller: `RegisterController`.
- ViewModel: `RegisterViewModel`.
- Service: `RegistrationService` (default impl in controller).
- Multi-step flow: credentials -> ID photo -> selfie -> submit.

## Key Types/Interfaces
- `client.controllers.RegisterController`
- `client.viewmodels.RegisterViewModel`
- `client.services.RegistrationService`
- `shared.requests.RegisterRequest`
- `shared.dto.EncryptedFileDTO`

## Flow
1. User completes credentials step with validation.
2. User selects ID/selfie files via drop zone or picker.
3. Client encrypts photo payloads before submission.
4. Registration request is sent over authenticated TLS path.

## Error/Security Notes
- Validation is field-specific and blocks invalid progression.
- Password and private key material are not persisted in plaintext by UI layer.
- Upload/encryption failures return user-safe error messages.

## Related Files
- `client/src/main/resources/fxml/register.fxml`
- `client/src/main/java/com/haf/client/controllers/RegisterController.java`
- `client/src/main/java/com/haf/client/viewmodels/RegisterViewModel.java`
