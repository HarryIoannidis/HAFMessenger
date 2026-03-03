# SPLASH

### Screen objective
- Application initialization: configuration, crypto verification, resource checking, network reachability.
- Visual progress feedback with animated progress bar and status messages.
- Navigation to login on success, retry/exit dialog on failure.

### FXML
- `splash.fxml`

### Architecture
- **Controller**: `SplashController`.
- **ViewModel**: `SplashViewModel`.
- **Pattern**: MVVM with property binding.

### UI elements
- `StackPane rootContainer`: transparent background for custom window.
- `ImageView logo`: application logo (`app_logo.png`).
- `Text title`: "HAF Messenger".
- `Text subtitle`: tagline.
- `Text status`: current bootstrap step message.
- `ProgressBar progressBar`: 0.0–1.0, hidden on error.
- `Text percentage`: formatted progress ("0%", "50%", "100%"), hidden on error.
- `Text version`: detected application version.

### Flow
1. `SplashController.initialize()`:
    - Calls `bindViewModel()` to bind UI properties.
    - Calls `viewModel.startBootstrap(onSuccess, onFailure)`.
2. Bootstrap sequence (background thread):
    - Config loading → Crypto init → Resource check → Network check.
    - Progress and status updated via `Task` properties.
3. On success → `navigateToLogin()` via `ViewRouter.switchToTransparent()`.
4. On failure → `showFailureDialog()` with retry/exit options.

### Window style
- `StageStyle.TRANSPARENT` (undecorated, custom styling).
- No title bar, no window controls.

### Error handling
- `Alert` dialog with error details, Retry (restarts bootstrap) or Cancel (exits app).
