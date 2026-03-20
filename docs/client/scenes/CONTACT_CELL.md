# CONTACT_CELL

### Screen objective
- Render each contact row in the left contact list with clear identity and presence state.
- Provide click and right-click interaction through a full-row overlay button.
- Show unread-message count in a compact badge.

### FXML
- `contact_cell.fxml`

### Architecture
- **Renderer class**: `ContactCell` (`ListCell<ContactInfo>`).
- **Pattern**: FXML-backed reusable list cell (not an `fx:controller` screen).
- **Usage**: Created by `MainController` as the `ListView` cell factory.

### UI elements
- `Text nameText`: contact display name.
- `Text regNumberText`: contact registration number.
- `Circle activenessCircle`: contact presence indicator.
- `StackPane unreadBadge` + `Text unreadBadgeText`: unread count badge (`9+` cap).
- `JFXButton overlayButton`: captures row click/context-menu gestures.

### Flow
1. `ContactCell.ensureLoaded()` loads `contact_cell.fxml` lazily.
2. FXML bytes are cached (`cachedFXMLBytes`) and reused to reduce repeated disk reads.
3. `updateItem(ContactInfo, boolean)` binds row text, presence color, and unread badge visibility.
4. Left-click (overlay button):
   - Selects the row in the parent `ListView`.
   - Runs configured click callback.
5. Right-click:
   - Fires overlay click first to sync selection.
   - Waits 170ms (`PauseTransition`) so selection/ripple completes.
   - Dispatches context menu request with screen coordinates.

### Notes
- Presence color fallback is gray if an invalid color string is provided.
- Unread badge is hidden for `0` and shown for positive counts.
