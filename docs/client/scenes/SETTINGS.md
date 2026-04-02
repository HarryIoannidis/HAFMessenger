# SETTINGS

## Purpose

Describe the settings popup scene that exposes per-user client preferences and restart-required handling.

## Current Implementation

- Controller: `SettingsController`.
- View: `settings.fxml`.
- Settings rows are generated programmatically with `SettingsRowBuilder` and bound directly to `ClientSettings`.
- Left navigation uses `ListView<SettingsMenuItem>` with a custom cell backed by `settings_item_cell.fxml`.
- Popup is opened/preloaded from main shell through `ViewRouter` using key `popup-settings`.

## Key Types/Interfaces

- `client.controllers.SettingsController`
- `client.models.SettingsMenuItem`
- `client.utils.ClientSettings`
- `client.utils.SettingsRowBuilder`
- `client.utils.PopupMessageBuilder`
- `client.utils.ViewRouter`
- `client.security.RememberedCredentialsStore`

## Flow

1. `MainController` preloads and opens the keyed settings popup.
2. Controller receives active `ClientSettings` and optional restart callback.
3. `initialize()` registers panes, builds rows, wires controls, and configures menu selection.
4. Toggle/checkbox/slider interactions mutate the active settings object in real time.
5. Turning off "Remember Credentials" updates preference flags and removes remembered password from OS secure storage.
6. Closing checks `settings.isRestartRequiredDirty()` and optionally prompts restart-now/later.

## Error/Security Notes

- Control wiring is null-safe; missing optional nodes fail gracefully.
- Remember-credentials toggle writes login remember flags to preferences and clears any secure-vault password when disabled.
- Password persistence for remember mode uses OS secure credential storage only (no plaintext password in preferences).
- Restart-required changes are explicitly confirmed before requesting application restart.

## Related Files

- `client/src/main/resources/fxml/settings.fxml`
- `client/src/main/resources/fxml/settings_item_cell.fxml`
- `client/src/main/java/com/haf/client/controllers/SettingsController.java`
- `client/src/main/java/com/haf/client/security/RememberedCredentialsStore.java`
- `client/src/main/java/com/haf/client/utils/ClientSettings.java`
- `client/src/main/java/com/haf/client/utils/SettingsRowBuilder.java`
- `client/src/main/java/com/haf/client/controllers/MainController.java`
