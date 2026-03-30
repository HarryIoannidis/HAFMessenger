# MAIN

## Purpose

Describe client startup entrypoints and initial navigation behavior.

## Current Implementation

- `Launcher.main(...)` delegates to JavaFX 25 app bootstrap.
- `ClientApp.start(Stage)` sets app title/icon, registers stage in `ViewRouter`, and opens splash view.
- Navigation uses `ViewRouter.switchToTransparent(...)` / `switchTo(...)`.
- App startup sets JavaFX 25 text-rendering properties before launch and starts from transparent splash shell.

## Key Types/Interfaces

- `com.haf.client.core.Launcher`
- `com.haf.client.core.ClientApp`
- `com.haf.client.utils.ViewRouter`
- `com.haf.client.controllers.SplashController`

## Flow

1. JavaFX 25 application starts from `Launcher`.
2. Main stage is registered with `ViewRouter`.
3. Splash scene is loaded and bootstrap logic runs.
4. Splash performs bootstrap checks and classifies startup failures for popup presentation.
5. Splash success transitions to login/main views.

## Error/Security Notes

- Missing FXML/resources fail fast through routing load exceptions.
- Splash bootstrap validates crypto/resources/network prerequisites before normal UI use.

## Related Files

- `client/src/main/java/com/haf/client/core/Launcher.java`
- `client/src/main/java/com/haf/client/core/ClientApp.java`
- `client/src/main/java/com/haf/client/utils/ViewRouter.java`
- `client/src/main/java/com/haf/client/controllers/SplashController.java`
