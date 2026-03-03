# VIEWMODELS

## SplashViewModel

### Purpose
- Orchestrates bootstrap sequence during splash screen display.
- Exposes status, progress, version, percentage, and error state properties.

### Properties
- `statusProperty()`: Current bootstrap step message ("Loading configuration...", "Initializing security modules...", etc.).
- `progressProperty()`: Bootstrap progress (0.0 to 1.0).
- `versionProperty()`: Detected application version.
- `percentageProperty()`: Formatted progress percentage ("0%", "50%", "100%").
- `errorProperty()`: Boolean indicating error state (hides progress bar when true).

### Bootstrap flow
- `startBootstrap(Runnable onSuccess, Consumer<Throwable> onFailure)`:
    - Creates `Task<Void>` on background thread.
    - Executes steps: config loading, crypto initialization, resource verification, network check.
    - Updates progress via `Task.updateProgress()` and `Task.updateMessage()` (thread-safe).
    - On success: invokes `onSuccess` callback on FX thread.
    - On failure: sets error state, invokes `onFailure` callback with exception.

### Dependency injection
- Constructor accepts functional interfaces: `ConfigLoader`, `CryptoInitializer`, `ResourceChecker`, `NetworkChecker`.
- `createDefault()`: Factory method creating ViewModel with default implementations.

---

## LoginViewModel

### Purpose
- Handles login form validation and state management.
- Exposes observable properties for email/password validation errors and login status.

### Properties
- `emailProperty()`: Bound to email input field.
- `passwordProperty()`: Bound to password input field.
- `emailErrorProperty()`: Boolean, true when email validation fails.
- `passwordErrorProperty()`: Boolean, true when password validation fails.
- `statusProperty()`: Current login status message.

### Validation
- `validate()`: Validates email format and password requirements.
    - Email: non-empty, valid format.
    - Password: non-empty, minimum length.
    - Sets error properties and returns boolean.

---

## RegisterViewModel

### Purpose
- Handles multi-step registration validation and state management.
- Validates personal info fields, password, and photo uploads across steps.

### Properties
- Personal info: `fullNameProperty()`, `regNumberProperty()`, `idNumberProperty()`, `rankProperty()`, `telephoneProperty()`, `emailProperty()`.
- Password: `passwordProperty()`, `confirmPasswordProperty()`.
- Error properties: `*ErrorProperty()` for each field.
- `currentStepProperty()`: Current step in the registration flow (1-4).

### Validation
- Step 1: Personal information (name, reg number, ID, rank, telephone, email).
- Step 2: Password and confirmation (match, strength).
- Step 3: ID photo (file selected, valid format).
- Step 4: Selfie photo (file selected, valid format).

---

## MessageViewModel

### Purpose
- Coordinates message sending and receiving operations.
- Maintains observable list of messages for UI display.
- Exposes status property for operation feedback.

### Properties
- `statusProperty()`: Current operation status ("Ready", "Message sent to X", etc.).
- `getMessages()`: Observable list of `MessageVM` records for display.

### Dependencies
- `MessageSender`: Sends encrypted messages to recipients.
- `MessageReceiver`: Receives and decrypts incoming messages.

### Operations
- `sendTextMessage(String recipientId, String messageText)`:
    - Encodes text to bytes, calls `messageSender.sendMessage()`.
    - Updates status and adds message to list on FX thread.
- `startReceiving()` / `stopReceiving()`: Controls message receiver.

### Message listener
- `MessageReceiver.MessageListener` registered in constructor.
- `onMessage()`: Handles incoming messages, adds to list on FX thread.
- `onError()`: Handles errors, updates status.
- All UI updates via `Platform.runLater()`.

---

## ViewModel Best Practices

### Property binding
- Expose properties via getter methods: `statusProperty()`, `progressProperty()`.
- Use `SimpleStringProperty`, `SimpleDoubleProperty`, `ReadOnlyStringWrapper`.

### Thread safety
- Background operations: use `Task<Void>` or `ExecutorService`.
- UI updates: use `Platform.runLater()` or `Task` property updates.
- Observable collections: update on FX thread.

### Error handling
- Catch exceptions in background operations, update status property.
- Do not expose internal exceptions to UI (wrap in user-friendly messages).

### Testing
- Dependency injection for mocking.
- Test property updates, callback invocations, error handling.
