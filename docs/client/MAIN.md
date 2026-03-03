# MAIN

### Purpose
- The central entry point of the HAF Messenger client, responsible for initializing JavaFX and launching the application UI.
- Coordinates JavaFX Application lifecycle, sets up the primary stage, and delegates to ViewRouter for navigation to the splash screen.

### Startup sequence
- `Launcher.main(String[] args)`:
    - Simple wrapper that delegates to `ClientApp.main(args)` for compatibility with JavaFX Maven plugin.
- `ClientApp.start(Stage primaryStage)`:
    - Called by JavaFX Application framework after toolkit initialization.
    - Registers primary stage with `ViewRouter.setMainStage(primaryStage)`.
    - Loads application icon from `/images/app_logo.png` (logs warning if missing, non-fatal).
    - Sets window title to "HAF Messenger".
    - Launches splash screen via `ViewRouter.switchToTransparent("/fxml/splash.fxml")`.
- Splash screen bootstrap:
    - `SplashController` initializes and starts `SplashViewModel` bootstrap sequence.
    - Bootstrap performs: config loading, crypto initialization, resource verification, network reachability check.
    - On success: navigates to login screen.
    - On failure: shows error dialog with retry/exit options.

### Dependencies created
- `ViewRouter.setMainStage(Stage stage)`:
    - Registers the primary JavaFX stage for navigation.
- `ViewRouter.switchToTransparent(String fxmlPath)`:
    - Loads FXML file and switches to transparent stage style.
    - FXML loader resolves resources relative to classpath.
- `SplashViewModel.createDefault()`:
    - Creates default bootstrap dependencies (config loader, crypto initializer, resource checker, network checker).
- `SplashController`:
    - Binds UI properties to ViewModel.
    - Starts bootstrap on initialization.
    - Handles navigation and error dialogs.

### Error handling
- Missing resources:
    - Application icon missing: logs warning, continues without icon.
    - FXML file missing: `ViewRouter` throws `RuntimeException` wrapping `IOException`, application terminates.
- Bootstrap failures:
    - SplashViewModel catches exceptions during bootstrap steps.
    - `SplashController.showFailureDialog()` displays error message with retry/exit options.
    - Retry: restarts bootstrap sequence.
    - Exit: closes application via `ViewRouter.close()`.
- JavaFX initialization errors:
    - If JavaFX toolkit fails to initialize, `Application.launch()` throws exception and terminates.
    - No graceful degradation: JavaFX is required for the client.

### Threading
- JavaFX Application Thread:
    - All UI operations (stage setup, FXML loading, property binding) run on FX thread.
    - `ViewRouter` methods are called from FX thread.
- Background threads:
    - Bootstrap steps run on background thread via `Task` in `SplashViewModel`.
    - UI updates via `Task.updateMessage()` and `Task.updateProgress()` (thread-safe property updates).
    - Success/failure callbacks executed on FX thread via `Platform.runLater()`.
