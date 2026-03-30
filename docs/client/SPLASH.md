# SPLASH

## Purpose

Document client bootstrap checks performed before login/main UI is shown.

## Current Implementation

- `SplashViewModel` runs staged bootstrap tasks on background thread.
- `SplashController` binds view state and handles success/failure transitions.
- Default checks include:
  - app version loading
  - crypto primitive availability
  - required resource availability
  - network reachability probe

## Key Types/Interfaces

- `client.controllers.SplashController`
- `client.viewmodels.SplashViewModel`
- `SplashViewModel` dependencies:
  - `ConfigLoader`
  - `CryptoInitializer`
  - `ResourceChecker`
  - `NetworkChecker`

## Flow

1. Splash controller initializes and binds properties.
2. ViewModel executes bootstrap sequence with progress/status updates.
3. On success: route to login.
4. On failure: present failure popup/dialog and allow retry/exit.

## Error/Security Notes

- Startup blocks normal UX if core crypto/resources fail validation.
- Network check is reachability-only and does not send credentials.

## Related Files

- `client/src/main/java/com/haf/client/controllers/SplashController.java`
- `client/src/main/java/com/haf/client/viewmodels/SplashViewModel.java`
- `client/src/main/resources/fxml/splash.fxml`
