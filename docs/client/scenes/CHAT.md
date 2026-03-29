# CHAT

## Purpose
Describe the active chat scene behavior for messages, compose actions, and attachment actions.

## Current Implementation
- Controller: `ChatController`.
- ViewModels: `ChatViewModel` on top of `MessagesViewModel` (`ChatSession.get()`).
- Supports message rendering, compose/send, attachment triggers, and per-message context actions.

## Key Types/Interfaces
- `client.controllers.ChatController`
- `client.viewmodels.ChatViewModel`
- `client.viewmodels.MessagesViewModel`
- `client.services.ChatAttachmentService`

## Flow
1. `MainController` loads `chat.fxml` and injects recipient via `setRecipient(...)`.
2. Chat view binds draft/send state to `ChatViewModel`.
3. Message list changes render bubbles through `MessageBubbleFactory`.
4. Send and attachment actions call service/network flows.
5. Receiver updates are acknowledged for active recipient.

## Error/Security Notes
- Invalid attachment/message actions surface user-safe errors.
- Auto-scroll and context actions are governed by runtime client settings.

## Related Files
- `client/src/main/resources/fxml/chat.fxml`
- `client/src/main/java/com/haf/client/controllers/ChatController.java`
- `client/src/main/java/com/haf/client/viewmodels/ChatViewModel.java`
