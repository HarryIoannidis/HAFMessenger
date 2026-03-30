# MAIN

## Purpose

Describe the main shell scene that coordinates tabs, contacts, dynamic content loading, and settings-driven behavior.

## Current Implementation

- Controller: `MainController`.
- ViewModels: `MainViewModel`, `MessagesViewModel`, `SearchSortViewModel`.
- Dynamic view loading handled by `MainContentLoader`.
- Supports contacts tab, search tab, profile strip, popup actions, and settings/runtime issue handling.

## Key Types/Interfaces

- `client.controllers.MainController`
- `client.controllers.MainContentLoader`
- `client.controllers.SearchController.ContactActions`
- `client.viewmodels.MainViewModel`

## Flow

1. Main scene initializes bindings, nav handlers, and listeners.
2. Contacts are fetched and list/profile state is synchronized.
3. Main content area switches between placeholder/chat/search/profile views.
4. Search actions bridge to contacts/chat/profile callbacks through `ContactActions`.
5. Runtime issues and settings updates are surfaced through popup/handler mechanisms.

## Error/Security Notes

- View-load failures are trapped and surfaced through popup flows.
- Logout/shutdown path attempts graceful server-side session cleanup.

## Related Files

- `client/src/main/resources/fxml/main.fxml`
- `client/src/main/java/com/haf/client/controllers/MainController.java`
- `client/src/main/java/com/haf/client/controllers/MainContentLoader.java`
- `client/src/main/java/com/haf/client/viewmodels/MainViewModel.java`
