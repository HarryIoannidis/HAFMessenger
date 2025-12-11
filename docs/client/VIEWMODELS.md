## ViewModels

### Purpose
- ViewModels provide a separation between UI (Controllers) and business logic/data operations.
- Expose observable properties for two-way binding with JavaFX UI components.
- Coordinate background operations (network, file I/O) and update UI properties on FX thread.
- Enable testability by isolating business logic from JavaFX dependencies.

### Architecture pattern
- **MVVM (Model-View-ViewModel)**: Controllers (View) bind to ViewModel properties, ViewModel coordinates with Models/Services.
- **Observable properties**: JavaFX `StringProperty`, `DoubleProperty`, `BooleanProperty`, `ObservableList` for reactive UI updates.
- **Thread safety**: Background operations run off FX thread, UI updates via `Platform.runLater()` or `Task` property updates.

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
- Enables testing with mock dependencies (see `SplashViewModelTest`).

### Threading
- Bootstrap steps run on background thread (`Thread` with name "splash-bootstrap").
- UI property updates via `Task` properties (automatically synchronized to FX thread).
- Callbacks executed on FX thread via `Task.setOnSucceeded()` / `Task.setOnFailed()`.

### Testing
- Unit tests inject mock dependencies to test success/failure paths.
- Verify property updates, callback invocations, error state transitions.
- See `SplashViewModelTest` for examples.

---

## MessageViewModel

### Purpose
- Coordinates message sending and receiving operations.
- Maintains observable list of messages for UI display.
- Exposes status property for operation feedback.

### Properties
- `statusProperty()`: Current operation status ("Ready", "Message sent to X", "Receiving messages...", etc.).
- `getMessages()`: Observable list of message strings for display.

### Dependencies
- `MessageSender`: Sends encrypted messages to recipients.
- `MessageReceiver`: Receives and decrypts incoming messages.

### Operations
- `sendTextMessage(String recipientId, String messageText)`:
    - Encodes text to bytes, calls `messageSender.sendMessage()`.
    - Updates status and adds message to list on FX thread.
    - Handles exceptions, updates status with error message.
- `startReceiving()`:
    - Starts message receiver, updates status.
    - Errors handled with status update.
- `stopReceiving()`:
    - Stops message receiver, updates status.

### Message listener
- `MessageReceiver.MessageListener` registered in constructor.
- `onMessage()`: Handles incoming messages, adds to list on FX thread.
- `onError()`: Handles errors, updates status and adds error message to list.
- All UI updates via `Platform.runLater()` (receiver callbacks may run on background thread).

### Threading
- Network operations (`sendMessage()`, `start()`, `stop()`) may block or run on background threads.
- UI updates always on FX thread via `Platform.runLater()`.
- Observable list updates trigger UI refresh automatically (JavaFX binding).

### Security rules
- Does not log message payloads (only sender ID, recipient ID, status).
- Error messages do not expose sensitive details (network internals, encryption failures).

---

### ViewModel best practices

### Property binding
- Expose properties via getter methods: `statusProperty()`, `progressProperty()`.
- Use `SimpleStringProperty`, `SimpleDoubleProperty`, `ReadOnlyStringWrapper` for mutable/read-only properties.
- Bind derived properties: `percentageProperty()` bound to `progressProperty()` with formatting.

### Thread safety
- Background operations: use `Task<Void>` or `ExecutorService` for long-running work.
- UI updates: use `Platform.runLater()` or `Task` property updates (`updateMessage()`, `updateProgress()`).
- Observable collections: `FXCollections.observableArrayList()` for thread-safe list updates (update on FX thread).

### Error handling
- Catch exceptions in background operations, update status property with error message.
- Invoke error callbacks on FX thread for UI dialogs/alerts.
- Do not expose internal exceptions to UI (wrap in user-friendly messages).

### Testing
- Dependency injection: accept interfaces/services in constructor for mocking.
- Test property updates, callback invocations, error handling.
- Mock dependencies to simulate success/failure scenarios.

