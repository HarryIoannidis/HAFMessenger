# SEARCH_RESULT_ITEM

## Purpose

Document the reusable search result card template and its interaction points.

## Current Implementation

- View template: `search_result_item.fxml`.
- Populated by `SearchController.populateCard(...)`.
- Displays user summary fields and action buttons.
- Card actions are rendered contextually (add/remove contact, open profile, start chat) through `ContactActions`.

## Key Types/Interfaces

- `client.controllers.SearchController`
- `client.utils.RankIconResolver`
- `shared.dto.UserSearchResultDTO`

## Flow

1. Controller loads card FXML for each result row.
2. Card fields/icons are populated from `UserSearchResultDTO`.
3. Button/click handlers dispatch through `SearchController.ContactActions`.

## Error/Security Notes

- Card rendering errors are isolated and logged without crashing whole result pane.
- Action handlers should validate required IDs before issuing contact/chat operations.

## Related Files

- `client/src/main/resources/fxml/search_result_item.fxml`
- `client/src/main/java/com/haf/client/controllers/SearchController.java`
- `client/src/main/resources/css/search.css`
