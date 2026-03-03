# SPLASH

### Purpose
- Performs controlled initialization of client subsystems (configuration, crypto, resources, network) before displaying the login screen.
- Provides visual feedback via progress bar and status messages during bootstrap.
- Ensures application readiness and security module availability before user interaction.

### Architecture
- **SplashViewModel**: Orchestrates bootstrap sequence on background thread, exposes observable properties for UI binding.
- **SplashController**: Binds FXML UI elements to ViewModel properties, handles navigation and error dialogs.
- **Bootstrap dependencies**: Injectable interfaces (ConfigLoader, CryptoInitializer, ResourceChecker, NetworkChecker) for testability.

### Bootstrap sequence
1. **Configuration loading** (progress: 0.1):
    - `ConfigLoader.loadVersion()`: Reads version from manifest or `HAF_APP_VERSION` environment variable.
    - Updates `versionProperty` for display in UI.
    - Falls back to "1.0.0" if no version detected.
2. **Crypto initialization** (progress: 0.3):
    - `CryptoInitializer.initialize()`: Verifies availability of cryptographic providers.
    - Checks: `SecureRandom.getInstanceStrong()`, `AES/GCM/NoPadding`, X25519/XDH key agreement, `SHA-256`.
    - Throws exception if any provider unavailable (prevents runtime crypto failures).
3. **Resource verification** (progress: 0.6):
    - `ResourceChecker.verify()`: Ensures required FXML, images, and CSS files exist.
    - Checks: `/fxml/login.fxml`, `/fxml/splash.fxml`, `/images/app_logo.png`, `/css/global.css`.
    - Throws `IOException` with descriptive message if resource missing.
4. **Network reachability** (progress: 0.8):
    - `NetworkChecker.check()`: Performs HEAD request to server endpoint (if configured).
    - Endpoint from `haf.server.url` system property or `HAF_SERVER_URL` environment variable.
    - Skips check if endpoint not configured (allows offline development).
    - Timeout: 2s connect, 3s request.
    - Throws `IOException` if server unreachable or returns error status.
5. **Completion** (progress: 1.0):
    - Status set to "Ready".
    - Success callback invoked: navigates to login screen.

### Dependencies
- `SplashViewModel`:
    - `ConfigLoader`: Loads application version.
    - `CryptoInitializer`: Verifies crypto providers.
    - `ResourceChecker`: Verifies resource files exist.
    - `NetworkChecker`: Checks server reachability.
- `SplashController`:
    - `SplashViewModel`: Bootstrap orchestration.
    - `ViewRouter`: Navigation to login screen.
    - JavaFX `Alert`: Error dialog display.

### UI binding
- `status.textProperty()`: Bound to ViewModel status property (displays current bootstrap step).
- `progressBar.progressProperty()`: Bound to ViewModel progress property (0.0 to 1.0).
- `percentage.textProperty()`: Bound to ViewModel percentage property (formatted as "X%").
- `version.textProperty()`: Bound to ViewModel version property (displays detected version).
- Error state: Progress bar and percentage hidden when `errorProperty()` is true.

### Error handling
- Bootstrap failure:
    - `Task.setOnFailed()` handler sets error state, unbinds properties, shows error message.
    - `SplashController.showFailureDialog()` displays `Alert` with error message.
    - Dialog options: Retry (restarts bootstrap) or Exit (closes application).
- Resource missing:
    - `ResourceChecker` throws `IOException` with descriptive path.
- Crypto provider unavailable:
    - `CryptoInitializer` throws `NoSuchAlgorithmException`.
    - Error message displayed, prevents application from running with missing X25519/AES-GCM support.

### Threading
- Bootstrap steps run on background thread (`Thread` with name "splash-bootstrap").
- UI updates via `Task.updateMessage()` and `Task.updateProgress()` (thread-safe property updates).
- Success/failure callbacks executed on FX thread via `Task.setOnSucceeded()` / `Task.setOnFailed()`.

### Security rules
- No sensitive data loaded during bootstrap (only version, resource paths, endpoint URL).
- Crypto initialization verifies X25519 and AES-GCM providers before any encryption operations.
- Network check uses HEAD request (no credentials, minimal data transfer).
