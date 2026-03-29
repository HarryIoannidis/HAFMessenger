# SEARCH

## Purpose
Describe user search scene behavior and contact/chat action bridging.

## Current Implementation
- Controller: `SearchController`.
- ViewModel: `SearchViewModel` + optional `SearchSortViewModel` options.
- Result cards loaded from `search_result_item.fxml`.
- Action bridge interface is `SearchController.ContactActions`.

## Key Types/Interfaces
- `client.controllers.SearchController`
- `SearchController.ContactActions`
- `client.viewmodels.SearchViewModel`
- `client.viewmodels.SearchSortViewModel`

## Flow
1. Main shell triggers `search(query[, sort])`.
2. ViewModel executes async search requests and publishes result/status properties.
3. Controller renders result cards and handles infinite-scroll page loading.
4. Card actions call `ContactActions` for add/remove/start-chat/open-profile.

## Error/Security Notes
- Search failures are surfaced as runtime issues and status text updates.
- Query minimum length and page-size controls protect server/query behavior.

## Related Files
- `client/src/main/resources/fxml/search.fxml`
- `client/src/main/resources/fxml/search_result_item.fxml`
- `client/src/main/java/com/haf/client/controllers/SearchController.java`
- `client/src/main/java/com/haf/client/viewmodels/SearchViewModel.java`
