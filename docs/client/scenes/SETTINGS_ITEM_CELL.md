# SETTINGS_ITEM_CELL

## Purpose

Document the reusable settings-menu item cell template used by the settings popup left navigation.

## Current Implementation

- View template: `settings_item_cell.fxml`.
- Loaded by `SettingsController.SettingsMenuCell` (inner class) via `FXMLLoader`.
- Renders category icon + category label and an overlay button for consistent click targeting.
- Used as the `ListView<SettingsMenuItem>` cell factory in `settings.fxml`.

## Key Types/Interfaces

- `client.controllers.SettingsController.SettingsMenuCell`
- `client.models.SettingsMenuItem`
- `client.utils.UiConstants`

## Flow

1. Settings menu list sets cell factory to `SettingsMenuCell`.
2. Each cell lazily loads `settings_item_cell.fxml` once.
3. `updateItem(...)` maps `SettingsMenuItem` icon/label into the loaded nodes.
4. Overlay button selects the current row in list view.
5. Selected row drives pane switching in `SettingsController.showPane(...)`.

## Error/Security Notes

- If cell FXML fails to load, the list falls back to text-only rendering.
- Cell behavior is local UI logic only; it does not trigger network/security-sensitive operations.

## Related Files

- `client/src/main/resources/fxml/settings_item_cell.fxml`
- `client/src/main/java/com/haf/client/controllers/SettingsController.java`
- `client/src/main/java/com/haf/client/models/SettingsMenuItem.java`
- `client/src/main/resources/fxml/settings.fxml`
