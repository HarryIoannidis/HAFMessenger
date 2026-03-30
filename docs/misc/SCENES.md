# SCENES

## Purpose

Provide a map of client scene-level documentation and their current controller/ViewModel ownership.

## Current Implementation

Implemented scene docs live under `docs/client/scenes/` and cover:

- `SPLASH`, `LOGIN`, `REGISTER`, `MAIN`, `CHAT`
- `SEARCH`, `SEARCH_RESULT_ITEM`, `PROFILE`, `PREVIEW`, `PLACE_HOLDER`, `CONTACT_CELL`
- `SETTINGS`, `SETTINGS_ITEM_CELL`, `POPUP_MESSAGE`
- Scene files map directly to JavaFX resources under `client/src/main/resources/fxml` and are loaded by controller/router entrypoints.

## Key Types/Interfaces

- Controllers: `SplashController`, `LoginController`, `RegisterController`, `MainController`, `ChatController`, `SearchController`, `ProfileController`, `PreviewController`, `ContactCell`, `SettingsController`, `PopupMessageController`.
- ViewModels: `SplashViewModel`, `LoginViewModel`, `RegisterViewModel`, `MainViewModel`, `MessagesViewModel`, `ChatViewModel`, `SearchViewModel`.

## Flow

1. `ClientApp` boots splash scene.
2. Login/registration transition into main shell.
3. Main shell dynamically loads chat/search/profile/placeholder content.
4. Popup scenes (profile/preview/settings/runtime dialogs) are managed through `ViewRouter` popup APIs.
5. Message and presence updates flow through ViewModels to scene controllers.

## Error/Security Notes

- Scene docs should document only current implemented flows; speculative/placeholder UI samples must be marked as planned.
- Security-sensitive behavior (credential handling, attachment actions, decrypt errors) should match controller/ViewModel code paths.

## Related Files

- `docs/client/scenes/SPLASH.md`
- `docs/client/scenes/LOGIN.md`
- `docs/client/scenes/MAIN.md`
- `docs/client/scenes/CHAT.md`
- `docs/client/scenes/SETTINGS.md`
- `docs/client/scenes/SETTINGS_ITEM_CELL.md`
- `docs/client/scenes/POPUP_MESSAGE.md`
- `client/src/main/resources/fxml`
