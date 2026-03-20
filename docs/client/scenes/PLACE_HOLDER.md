# PLACE_HOLDER

### Screen objective
- Display a neutral empty-state view when no conversation is selected.
- Guide the user to start a chat from the contacts list or search flow.

### FXML
- `place_holder.fxml`

### Architecture
- **Controller**: none (static view).
- **Loader**: `MainContentLoader` lazily loads and caches this view.
- **Usage**: shown in the main content pane when there is no active chat.

### UI elements
- `Text`: "No chat selected..."
- `Text`: "Start by opening a chat."
- `ImageView`: empty chat illustration (`/images/misc/empty_chat.png`)

### Flow
1. `MainContentLoader.ensurePlaceholderLoaded()` loads `place_holder.fxml` once.
2. `MainContentLoader.showPlaceholder()` swaps the cached view into `contentPane`.
3. `MainController` requests placeholder when no contact is selected or after tab state changes that clear active chat.

### Notes
- The view uses `global.css` only.
- This file is intentionally simple and does not require a dedicated controller.
