# ROUTING

### Purpose
- Centralized navigation utility for switching between FXML views in the JavaFX application.
- Manages the primary application stage and handles style transitions (decorated/transparent).
- Provides consistent FXML loading and scene switching across the application.

### Architecture
- Static utility class with singleton stage reference.
- Stage registered via `setMainStage()` during application startup (`ClientApp.start()`).
- FXML paths resolved relative to classpath (`ViewRouter.class.getResource()`).

### Navigation methods

#### switchTo(String fxmlPath)
- Switches to decorated window style (standard window with title bar).
- If stage is currently transparent, recreates stage with `StageStyle.DECORATED`.
- Loads FXML file, creates or updates scene, centers window, shows stage.
- Throws `RuntimeException` if FXML file not found or load fails.

#### switchToTransparent(String fxmlPath)
- Switches to transparent window style (custom window without OS decorations).
- If stage is currently decorated, recreates stage with `StageStyle.TRANSPARENT`.
- Sets scene fill to `Color.TRANSPARENT` for custom window styling.
- Loads FXML file, creates scene, centers window, shows stage.
- Throws `RuntimeException` if FXML file not found or load fails.

#### close()
- Closes the main application stage.
- Used for application exit (e.g., from error dialogs).

### Stage management

#### Stage registration
- `setMainStage(Stage stage)`: Called once during `ClientApp.start()`.
- Stores stage reference for subsequent navigation calls.
- Stage must be set before calling navigation methods.

#### Stage recreation
- `recreateStage(StageStyle style)`: Private method for style transitions.
- Closes current stage, creates new stage with specified style.
- Preserves icons and title from previous stage.
- Required because JavaFX `StageStyle` cannot be changed after creation.

### FXML loading
- `FXMLLoader` resolves FXML paths relative to `ViewRouter` class location.
- Path format: `/fxml/splash.fxml`, `/fxml/login.fxml` (leading slash for classpath root).
- Controller instantiation: FXML `fx:controller` attribute specifies controller class.
- Controller `initialize()` method called automatically after FXML load.

### Scene management
- If scene exists: updates root node via `scene.setRoot(root)` (preserves scene properties).
- If no scene: creates new `Scene` with loaded root.
- Transparent scenes: `scene.setFill(Color.TRANSPARENT)` for custom window styling.

### Error handling
- FXML load failure (`IOException`): Wrapped in `RuntimeException` with descriptive message.
- Error message: "Failed to load FXML: {fxmlPath}".
- Application terminates if navigation fails (no fallback view).

### Usage examples
- Splash screen: `ViewRouter.switchToTransparent("/fxml/splash.fxml")` (custom styled window).
- Login screen: `ViewRouter.switchToTransparent("/fxml/login.fxml")` (custom styled window).
- Main chat: `ViewRouter.switchTo("/fxml/main_chat.fxml")` (standard window).
- Error exit: `ViewRouter.close()` (from error dialog).

### Threading
- All methods must be called from JavaFX Application Thread.
- FXML loading and scene updates are synchronous operations.
- Stage operations (`show()`, `close()`) are thread-safe but should be called from FX thread.

### Security rules
- FXML paths validated at load time (file must exist in classpath).
- No dynamic path construction from user input (prevents path traversal).
- Controller classes must be in `com.haf.client.controllers` package (module exports).

### Best practices
- Use `switchToTransparent()` for splash/login screens (custom styling).
- Use `switchTo()` for main application screens (standard window).
- Register stage early in application lifecycle (`ClientApp.start()`).
- Handle navigation failures gracefully (show error dialog, allow retry/exit).
