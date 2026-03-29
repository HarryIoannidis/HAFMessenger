# CONTACT_CELL

## Purpose
Describe the custom contact list cell rendering used in the main contacts list.

## Current Implementation
- `ContactCell` is a custom `ListCell<ContactInfo>` backed by `contact_cell.fxml`.
- Displays contact identity, presence styling, and interaction affordances used by main shell.

## Key Types/Interfaces
- `client.controllers.ContactCell`
- `client.models.ContactInfo`
- `client/src/main/resources/fxml/contact_cell.fxml`

## Flow
1. `MainController` configures `ListView` cell factory with `ContactCell`.
2. Each cell receives `ContactInfo` updates from `MainViewModel.contactsProperty()`.
3. Selection and context menu actions are handled by main shell callbacks.

## Error/Security Notes
- Cell rendering must tolerate missing avatars/presence metadata without breaking list interactions.

## Related Files
- `client/src/main/java/com/haf/client/controllers/ContactCell.java`
- `client/src/main/resources/fxml/contact_cell.fxml`
- `client/src/main/java/com/haf/client/controllers/MainController.java`
