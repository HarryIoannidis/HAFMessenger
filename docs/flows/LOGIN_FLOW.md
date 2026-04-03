# LOGIN FLOW ANALYSIS

This document provides a deep-dive technical breakdown of the login orchestrations for the HAFMessenger client, primarily localized in `LoginController.java` and `DefaultLoginService.java`.

## 1. UI Interaction & Form Verification (`LoginController`)

- **Bidirectional Binding**: The JavaFX fields (`emailField`, `passwordField`) are bidirectionally bound to `LoginViewModel` properties.
- **Remembered Prefill**: On screen initialization, `RememberedCredentialsStore` loads remembered email and password (if available) and pre-fills the form.
- **Reactive UX**:
  - Typing in either field actively strips away `UiConstants.STYLE_TEXT_FIELD_ERROR` classes without waiting for a re-submit.
  - Pressing `Enter` on the email field shifts focus to the password field, and pressing `Enter` on the password field triggers the `handleSignIn()` method.
  - The controller handles a "password toggle" by maintaining a hidden standard `TextField` that mirrors the `PasswordField`, swapping their visibility and keeping the cursor caret position in sync.
  - When remembered credentials are loaded, focus moves to the `Sign In` button for immediate keyboard submit.
- **Hardware Acceleration**: If local validation (`viewModel.validate()`) fails, the controller executes a JavaFX `TranslateTransition` natively rendering a physical back-and-forth "shake" of the invalid fields.

## 2. Thread Hand-Off

- Because networking operations block the active thread, `LoginController` sets the state to "Signing in...", disables all inputs (binded automatically via `viewModel.loadingProperty()`), and spawns a daemon thread named `login-thread`.
- From here, execution is handed straight to `DefaultLoginService.login()`.

## 3. Execution & Fault-Tolerance (`DefaultLoginService`)

- **State Clearing**: The service immediately calls `CurrentUserSession.clear()` to purge any lingering artifacts from previous sessions or logouts.
- **Retries**: A loop iterates up to `MAX_LOGIN_ATTEMPTS` (currently `3`). If a network or parsing error occurs, a `Thread.sleep` (400ms delay) triggers before it retries. *(Note: This retry logic is explicitly bypassed if the server outright rejects the credentials; it only triggers on connection hiccups or timeout).*
- **Transport**: `LoginRequest` is serialized to JSON and sent over Java `HttpClient` with mode-aware SSL context (`SslContextUtils.getSslContextForMode(...)`) to runtime-resolved login URI (`ClientRuntimeConfig`). There is a hard 10-second timeout.

## 4. Authentication Acceptance & Crypto-Bootstrapping

When the HTTP call yields a `200 OK` without error payloads in the `LoginResponse`, the service must bootstrap the secure session:

1. **Keystore Unlock**: It initializes `UserKeystoreKeyProvider`. Critically, it passes the clear-text password (`char[] passphrase`) used during login to unlock the user's local keystore on disk.
2. **Transport Hydration**: It sets up `WebSocketAdapter` with runtime-resolved URIs and the `sessionId` emitted by the server HTTP response.
3. **Directory Routing**: Overrides the `KeyProvider`'s directory fetcher to pipe missing public-key lookups back out over the REST API (`/api/v1/users/{id}/key`), utilizing the newly authenticated adapter.
4. **Messenger Tying**: It bundles the crypto-provider and adapter into `DefaultMessageSender` and `DefaultMessageReceiver`, with receiver transport mode chosen by `ClientRuntimeConfig` (`WEBSOCKET` in dev, `HTTPS_POLLING` in prod).
5. **Session Injection**: Finally, it persists all these singletons synchronously into `NetworkSession` (transport), `ChatSession` (view model orchestration), and `CurrentUserSession` (static profile storage, mapped to a `UserProfileInfo` object).

## 5. Transitioning Phase

The service emits a success code, popping execution back to the `LoginController` into `Platform.runLater` (jumping back to the Main UI Thread).

- The button is swapped to say "Loading components...".
- **Hardware Throttling**: The controller kicks off a JavaFX `PauseTransition` for precisely 50 milliseconds. This gives the GPU and Java scene graph exactly one "tick" to actually paint the UI text changes to the monitor *before* the application begins loading the heavy `MAIN` interface.
- **Disk IO Preference**: During that 50ms pulse, `RememberedCredentialsStore` writes remember-toggle/email metadata to `java.util.prefs.Preferences`, while remembered password (when enabled) is stored through OS secure credential storage.
- **Navigation Engine**: `ViewRouter.switchToTransparent(UiConstants.FXML_MAIN)` is invoked.
- **View-Level Recovery**: If the Heavy Main view crashes while compiling/rendering `FXML_MAIN` (even though authentication succeeded), `showMainLoadFailurePopup` catches it natively, throwing an error dialog without completely terminating the JVM sequence.
