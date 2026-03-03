# MAIN

### Screen objective
- Primary application screen after login.
- Contact list navigation, chat area, profile panel, and search functionality.
- Dynamically loads chat view or placeholder based on contact selection.

### FXML
- `main.fxml` (shell), `chat.fxml` (loaded into chat area), `place_holder.fxml` (default empty state), `contact_cell.fxml` (contact list items).

### Architecture
- **Controller**: `MainController`.
- **Sub-controllers**: `ChatController` (loaded per contact), `ContactCell` (list cell factory).
- **Pattern**: MVVM — `MessageViewModel` via `ChatSession` singleton.

### UI elements
- `BorderPane rootContainer`: main layout with title bar, sidebar, chat area.
- `HBox titleBar`: draggable custom title bar with window controls.
- `VBox sidebar`: navigation bar + contact list.
- `StackPane chatArea`: dynamic content area (chat or placeholder).
- `HBox profilePanel`: contact profile panel (toggle visibility).

### Navigation bar
- `JFXButton messagesNavBtn`: messages tab (default active).
- `JFXButton searchNavBtn`: search tab.
- Active tab: blue indicator line under active button, icon color change.
- `activateMessagesTab()`: show contact list, hide search.
- `activateSearchTab()`: show search field + filter, hide contact list.

### Contact list
- `ListView<ContactInfo> contactListView`: populated with sample contacts.
- `ContactCell`: custom `ListCell` loaded from `contact_cell.fxml`.
- Contact selection: `setupContactSelection()` listener.
    - On select: `loadChat(recipientId)` → loads `chat.fxml`, injects recipient ID.
    - Shows profile panel with contact info.
    - On deselect: `loadPlaceholder()` → loads `place_holder.fxml`.

### Chat area loading
- `loadChat(String recipientId)`:
    - `FXMLLoader` loads `chat.fxml`.
    - Gets `ChatController`, calls `setRecipient(recipientId)`.
    - Sets loaded view into `chatArea`.
- `loadPlaceholder()`:
    - `FXMLLoader` loads `place_holder.fxml`.
    - Sets loaded view into `chatArea`.

### Window style
- `StageStyle.DECORATED` (standard window with OS controls + custom styling).
- Draggable title bar with custom minimize/maximize/close buttons.

### Profile panel
- `showProfilePanel(ContactInfo contact)`: displays contact name, rank, email.
- `hideProfilePanel()`: hides panel.
