# SEARCH FLOW

## Purpose

Describe the current search orchestration across `MainController`, `SearchController`, and `SearchViewModel`.

## Current Implementation

- Search input originates in `MainController` (`toolbarSearchField`) and is routed through `SearchFilterController` into `SearchController`.
- Search trigger policy is settings-driven:
  - enter-to-search mode
  - optional instant-on-type mode with `300ms` debounce
- `SearchViewModel` executes async authenticated requests to `GET /api/v1/search?q=<query>&limit=<n>[&cursor=<token>]`.
- Results are rendered as cards in a `FlowPane` via `search_result_item.fxml` (not a `ListView` row factory).
- Pagination uses keyset cursor tokens returned by `UserSearchResponse`; controller can request more pages on scroll.
- Cross-screen actions are delegated through `SearchController.ContactActions` (`add/remove contact`, `start chat`, `open profile`).

## Key Types/Interfaces

- `client.controllers.MainController`
- `client.controllers.SearchController`
- `SearchController.ContactActions`
- `client.viewmodels.SearchViewModel`
- `client.viewmodels.SearchSortViewModel`
- `shared.responses.UserSearchResponse`
- `shared.dto.UserSearchResultDTO`

## Flow

1. User types query in main toolbar search field while search tab is active.
2. `MainController` applies search settings (debounce/enter requirement/min length) and dispatches `search(query[, sort])`.
3. `SearchViewModel` validates query length (`UiConstants.SEARCH_MIN_QUERY_LENGTH`) and starts background search for generation-safe results.
4. ViewModel sends authenticated request to `/api/v1/search` with configured page size and optional cursor.
5. Parsed `UserSearchResponse` updates status text, result list, and `nextCursor/hasMore` state.
6. `SearchController` renders result cards and wires per-card actions back to `ContactActions`.
7. When infinite scroll is enabled, near-bottom scrolling triggers `loadMore()` for cursor pagination.

## Error/Security Notes

- Search calls require an active authenticated `NetworkSession`.
- Failed requests update status text and publish recoverable runtime issues with retry callbacks.
- Page size is clamped client-side and cursor pagination is server-signed/validated.

## Related Files

- `client/src/main/java/com/haf/client/controllers/MainController.java`
- `client/src/main/java/com/haf/client/controllers/SearchController.java`
- `client/src/main/java/com/haf/client/viewmodels/SearchViewModel.java`
- `client/src/main/resources/fxml/search.fxml`
- `client/src/main/resources/fxml/search_result_item.fxml`
- `server/src/main/java/com/haf/server/ingress/HttpIngressServer.java`
