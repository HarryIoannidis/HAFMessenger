# PLACE_HOLDER

## Purpose

Describe the empty-state scene shown when no chat recipient is selected.

## Current Implementation

- View file: `place_holder.fxml`.
- Loaded by `MainContentLoader`/`MainController` into the main content pane.
- Shows empty-chat illustration and guidance text.
- Acts as the default center-pane content after login until a contact is selected.

## Key Types/Interfaces

- `client.controllers.MainContentLoader`
- `client.controllers.MainController`
- placeholder FXML/CSS resources

## Flow

1. Main shell has no active recipient.
2. Content loader inserts placeholder scene.
3. Selecting a contact replaces placeholder with chat scene.

## Error/Security Notes

- Placeholder state should be safe fallback when chat view cannot be loaded.
- Placeholder view is static UI only (no credential/network side effects).

## Related Files

- `client/src/main/resources/fxml/place_holder.fxml`
- `client/src/main/resources/images/misc/empty_chat.png`
- `client/src/main/java/com/haf/client/controllers/MainContentLoader.java`
