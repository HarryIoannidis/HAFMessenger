# SEARCH_RESULT_ITEM

### Screen objective
- Define the reusable card layout for one user row in search results.
- Expose ids (`fx:id`) consumed by `SearchController.populateCard(...)`.

### FXML
- `search_result_item.fxml`

### Architecture
- **Controller**: none (template view).
- **Populated by**: `SearchController` using `Node.lookup("#id")`.
- **Rendered in**: `FlowPane resultsPane` inside `search.fxml`.

### UI elements
- Identity section:
  - `ImageView avatarImage`
  - `Text fullNameText`
  - `Text regNumberText`
  - `Text emailText`
  - `ImageView rankImage`
- Action section:
  - `JFXButton removeButton` (toggles Add/Remove contact)
  - `JFXButton startChatButton`

### Flow
1. `SearchController` loads this FXML for each `UserSearchResultDTO`.
2. Controller fills text fields and rank icon.
3. Controller wires action handlers:
   - toggle contact relationship
   - start chat with selected user
4. Whole card is clickable for opening profile, except clicks on action buttons.

### Notes
- The file uses `search.css` for card styling.
- Since there is no dedicated controller, all behavior is centralized in `SearchController`.
