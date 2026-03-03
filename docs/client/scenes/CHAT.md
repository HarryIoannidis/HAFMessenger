# CHAT

### Screen objective
- Displays conversation messages as styled bubbles.
- Provides text input and send controls.
- Auto-scrolls to newest message.

### FXML
- `chat.fxml` (loaded dynamically by `MainController`)

### Architecture
- **Controller**: `ChatController`.
- **ViewModel**: `MessageViewModel` (shared via `ChatSession` singleton).
- **Pattern**: MVVM with observable list binding.

### UI elements
- `ScrollPane chatScrollPane`: scrollable message area.
- `VBox chatBox`: vertical container for message bubbles.
- `TextField messageField`: text input for composing messages.
- `JFXButton sendButton`: sends the message.

### Flow
1. `MainController.loadChat(recipientId)`:
    - Loads `chat.fxml` via `FXMLLoader`.
    - Calls `chatController.setRecipient(recipientId)`.
2. `ChatController.initialize()`:
    - Gets `MessageViewModel` from `ChatSession.get()`.
    - Populates existing messages: iterates `viewModel.getMessages()`, creates bubbles via `MessageBubbleFactory.create(vm)`.
    - Registers `ListChangeListener` on `viewModel.getMessages()`:
        - On add: creates bubble, appends to `chatBox`, auto-scrolls.
    - Wires `sendButton.setOnAction()` and `messageField.setOnAction()` to `sendMessage()`.
3. `sendMessage()`:
    - Guards: `viewModel != null`, text not empty.
    - `viewModel.sendTextMessage(recipientId, text)`.
    - Clears `messageField`.

### Message bubbles
- Created by `MessageBubbleFactory.create(MessageVM vm)`.
- Supports message types: TEXT, IMAGE, FILE.
- Styled differently for sent vs received messages.

### Dependencies
- `ChatSession`: singleton holding the current `MessageViewModel`.
- `MessageBubbleFactory`: creates styled JavaFX nodes from `MessageVM` records.
- `MessageVM`: record with sender, content, type, timestamp, direction.

### Auto-scroll
- After adding new bubbles: `chatScrollPane.layout()` then `chatScrollPane.setVvalue(1.0)`.
