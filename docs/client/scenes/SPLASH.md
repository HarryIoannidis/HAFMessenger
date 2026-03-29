# SPLASH

## Purpose
Document splash scene UI behavior during bootstrap.

## Current Implementation
- Controller: `SplashController`.
- ViewModel: `SplashViewModel`.
- Scene presents progress, status, version, and failure handling before transition to login.
- Failure popup supports retry/exit and classifies network/resource/security startup errors for clearer user messaging.

## Key Types/Interfaces
- `client.controllers.SplashController`
- `client.viewmodels.SplashViewModel`
- `client.utils.ViewRouter`

## Flow
1. Splash scene loads and binds properties.
2. ViewModel executes bootstrap checks asynchronously.
3. Failures are normalized to user-facing categories with root-cause detail.
4. Success routes to login; failure opens failure popup/dialog.

## Error/Security Notes
- Failure mode keeps app in controlled state with retry/exit options.
- Bootstrap validates crypto/resource prerequisites before normal operation.

## Related Files
- `client/src/main/resources/fxml/splash.fxml`
- `client/src/main/java/com/haf/client/controllers/SplashController.java`
- `client/src/main/java/com/haf/client/viewmodels/SplashViewModel.java`
