# SEARCH

### Screen objective
- Search users by name or registration number from the main shell.
- Render result cards with contact actions (add/remove, start chat, open profile).
- Support incremental loading when scrolling near the bottom.

### FXML
- `search.fxml`

### Architecture
- **Controller**: `SearchController`.
- **ViewModel**: `SearchViewModel`.
- **Loaded by**: `MainContentLoader` and cached for reuse.
- **Card template**: `search_result_item.fxml`.

### UI elements
- `ScrollPane resultsScrollPane`: scroll container for results.
- `FlowPane resultsPane`: card layout for result items.
- `VBox statusBox`: overlay shown when no results.
- `Text statusText`: bound to search status text from ViewModel.

### Flow
1. `SearchController.initialize()`:
   - binds status and result visibility
   - binds result list listeners
   - binds infinite scroll (`vvalue >= UiConstants.SEARCH_SCROLL_LOAD_THRESHOLD`)
   - clears initial results
2. `search(query)` delegates async search execution to `SearchViewModel`.
3. Result updates:
   - full re-render for structural list changes
   - append-only render for added pages
4. Each card is loaded from `search_result_item.fxml`, then populated with:
   - name, reg number, email
   - rank icon
   - action button handlers
5. Card click behavior:
   - open profile on primary click
   - ignore clicks originating from action buttons

### Contact actions bridge
- `setContactActions(SearchContactActions)` injects callbacks from `MainController`.
- Supported actions:
  - add/remove contact
  - start chat
  - open profile
