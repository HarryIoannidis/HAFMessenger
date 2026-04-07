# CHAT FEED FLOW ANALYSIS

This document provides a deep-dive technical breakdown of the secure chat feed interactions for the HAFMessenger client, localized in `ChatController.java`.

## 1. Feed Rendering & Observability

When a chat tab opens, the `ChatController` mounts itself onto the active recipient ID.

- It fetches the active message list from `ChatViewModel`.
- A `WeakListChangeListener` is bound directly to the observable feed. This specifically ensures memory isn't leaked when wildly jumping between thousands of different chat partners.
- When the feed changes via the active messaging transport (WebSocket in development mode, HTTPS polling in production — selected by `ClientRuntimeConfig.MessagingTransportMode`), it parses the `MessageVM` wrapper.

## 2. Bubble Generation & Scroll Management

To keep the performance tight:

- The controller pushes the raw `MessageVM` off to a `MessageBubbleFactory` determining alignment, styling, and timestamp padding based on whether the payload `.isOutgoing()`.
- The node is appended to the `VBox`.
- It forces `chatScrollPane.layout()` and actively fires `chatScrollPane.setVvalue(1.0)` to automatically snap the scrollbar to the bottom if `isChatAutoScrollToLatest()` is configured in system settings.

## 3. Attachment Pre-Flight Protocol

When sending an image or document, heavy safeguards trigger:

- `chooseImageAttachment/chooseDocumentAttachment` strictly isolates user `FileChooser` capability by extension filters (`*.pdf` vs `*.png`).
- Checks `MAX_ATTACHMENT_BYTES` explicitly, bounding payloads at a strict 10MB cutoff utilizing `java.nio.file.Files.size()`.
- If an oversized file attempts to pass, a custom two-action alert blocks it, preventing a hard server disconnect for oversize blobs.
- Success delegates the file pointer to the `ChatAttachmentService`, which manages background uploading securely.

## 4. Action Mechanics (Context Menus & Privacy)

Messages support deep native-feeling menus:

- A custom `PauseTransition` mimics the native ~170ms OS delay required to visually pop open a right-click `ContextMenu` avoiding "flickering" bugs on Linux window managers.
- `resolveContextActions()` logically parses what the user can do:
  - `.TEXT` gets **Copy**.
  - `.IMAGE` gets **Preview** & **Download**.
  - `.FILE` gets **Download**.
- Image previews parse a local temporary path and push a new visual stage utilizing `ImageSaveSupport` keeping complex I/O pipelines out of the UI controller.
- Saving files respects a user "Privacy Confirmation" prompt if toggled, ensuring potentially malicious blobs aren't executed accidentally.
