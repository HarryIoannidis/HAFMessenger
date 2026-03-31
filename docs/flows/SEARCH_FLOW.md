# SEARCH & DIRECTORY FLOW ANALYSIS

This document provides a technical breakdown of the Search interactions for the HAFMessenger client, localized across `MainController` and `SearchController`.

## 1. Input Throttling Layer

- The initial UI capture happens via the `toolbarSearchField` housed in the main shell.
- It enforces strict string normalizations, checking the JVM `trim()` limits before allowing any command to proceed against the `SearchMinimumQueryLength` rule.
- This layer wraps text input with a 300ms `PauseTransition` acting as a debounce gate to prevent server-spamming during rapid keystrokes.

## 2. Query Dispatch

- The query text alongside any actively checked sorting/filter options (`SearchSortViewModel.SortOptions`) are dispatched to the `SearchController`.
- The controller formats this into a network request targeting the underlying REST directory endpoint (`/api/v1/users/search`).
- To respect performance limits, it loads paginated returns and deserializes the JSON Array back into `UserSearchResultDTO` arrays.

## 3. Mapping & Rendering Logic

- Like the chat list, the Search Controller uses a JavaFX `ListView` coupled with a custom UI factory to turn raw array values into `SEARCH_RESULT_ITEM` visual panes.
- Result lists apply varying styles depending on whether the contact is already registered locally in the user's friend list or if they are purely a fresh directory lookup.

## 4. Hand-off Transitions

- The system listens for a click-event on the generated search result row.
- Upon clicking an item, a callback fires returning the specific generic `UserId` outwards.
- The `MainController` sweeps up the event, forces the application to switch out of the `<Search>` tab back into the `<Messages>` tab, and immediately mounts the target identity to the Chat Engine—spawning an entirely new conversation fluidly.
