# CONTACT_CELL

## Purpose

Describe the custom contact list cell rendering used in the main contacts list.

## Current Implementation

- `ContactCellController` is a custom `ListCell<ContactInfo>` backed by `contact_cell.fxml`.
- Displays contact identity, presence styling, and interaction affordances used by main shell.

## Key Types/Interfaces

- `client.controllers.ContactCellController`
- `client.models.ContactInfo`
- `client/src/main/resources/fxml/contact_cell.fxml`

## Flow

1. `MainController` configures `ListView` cell factory with `ContactCellController`.
2. Each cell receives `ContactInfo` updates from `MainViewModel.contactsProperty()`.
3. Selection and context menu actions are handled by main shell callbacks.

## Error/Security Notes

- Cell rendering must tolerate missing avatars/presence metadata without breaking list interactions.

## Related Files

- `client/src/main/java/com/haf/client/controllers/ContactCellController.java`
- `client/src/main/resources/fxml/contact_cell.fxml`
- `client/src/main/java/com/haf/client/controllers/MainController.java`
