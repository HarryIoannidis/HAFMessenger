# REGISTRATION FLOW ANALYSIS

This document provides a deep-dive technical breakdown of the User Registration orchestrations for the HAFMessenger client, primarily localized in `RegisterController.java` and `DefaultRegistrationService.java`.

## 1. Multi-Step Wizard Engine

The registration screen acts as a stateful, single-page wizard managed via an internal ENUM (`RegistrationStep.CREDENTIALS`, `ID_PHOTO`, `SELFIE_PHOTO`).

- It seamlessly hides and shows modular JavaFX `VBox` layouts (`credentialsVBox`, `photosVBox`).
- A single action button dynamically morphs its text ("Next Step", "Last Step", "Register") depending on the active stage without destroying the window or duplicating controllers.

## 2. Interactive Local Validation

Like the Login layer, `RegisterController` utilizes bidirectional property binding with `RegisterViewModel`.

- It executes layered validation gates: `validateCredentials()`, `validateIdPhoto()`, and `validateSelfiePhoto()`.
- **Reactive UX**: Entering data instantly strips `UiConstants.STYLE_TEXT_FIELD_ERROR` classes. Password confirmation matching is tracked live.
- **Hardware Acceleration**: If validation fails (e.g. missing an ID photo or leaving a required field blank), the controller natively applies a JavaFX `TranslateTransition` to violently "shake" only the invalid UI nodes.

## 3. Advanced Drag-And-Drop Photography

Handling heavy media locally is a crucial part of the sign-up.

- **Drop Zones**: The UI enables OS-native drag-and-drop functionality supporting `TransferMode.COPY_OR_MOVE`.
- **Validation**: When a file is dropped or selected via `FileChooser`, the controller evaluates if it is an image (`.png, .jpg`) and prevents payloads over 10MB to avoid overwhelming the server.
- **Preview Engine**: The controller dynamically generates an `ImageView` thumbnail preview of the local file, parsing the raw byte length and formatting it clearly to the user (converting explicitly to KB or MB representation).

## 4. Execution Pipeline

When hitting the final "Register" submit:

- The UI triggers `loadingProperty()`, disabling all interactive elements and updating the button to "Registering...".
- A daemon thread executes `registrationService.register()`.
- An immutable `RegistrationCommand` is constructed assembling the 7 textual strings (Name, RegNum, ID, Rank, Phone, Email, Password) alongside the two underlying `java.io.File` pointers for the ID & Selfie.
- The service marshals this data (handling encryption parameters locally) and issues the HTTP POST payload to the backend.
- Upon receiving a `RegistrationResult.Success`, the controller automatically delegates `ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN)` passing the baton cleanly back to the Login Flow.
