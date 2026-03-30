# POPUP_MESSAGE

## Purpose

Document the reusable popup-message scene used for confirmations, notices, and guarded runtime actions.

## Current Implementation

- Controller: `PopupMessageController`.
- View: `popup_message.fxml`.
- Content/actions are provided through immutable `PopupMessageSpec` values (typically built via `PopupMessageBuilder`).
- Popup instances are shown through `ViewRouter` with keyed stage reuse.
- Supports single-action or dual-action layouts, danger styling for destructive actions, and optional drag-to-move behavior.

## Key Types/Interfaces

- `client.controllers.PopupMessageController`
- `client.utils.PopupMessageBuilder`
- `client.utils.PopupMessageSpec`
- `client.utils.ViewRouter`
- `client.utils.UiConstants`

## Flow

1. Caller builds a popup spec (`title`, `message`, button labels, callbacks, style flags).
2. Caller triggers `PopupMessageBuilder.show()` (or `ViewRouter.showPopup(...)`) with a popup key.
3. Controller applies spec values to title/message/buttons and close-button visibility.
4. User presses action/cancel/close and the popup stage hides.
5. Controller executes configured callback (`onAction` or `onCancel`) after hide.

## Error/Security Notes

- Popup callbacks are explicit and scoped per spec, avoiding implicit side effects.
- Destructive actions are visually marked (`dangerAction`) before user confirmation.
- Startup privacy-unlock popup hides the title-bar close control to enforce explicit unlock/cancel flow.

## Related Files

- `client/src/main/resources/fxml/popup_message.fxml`
- `client/src/main/java/com/haf/client/controllers/PopupMessageController.java`
- `client/src/main/java/com/haf/client/utils/PopupMessageBuilder.java`
- `client/src/main/java/com/haf/client/utils/PopupMessageSpec.java`
- `client/src/main/java/com/haf/client/controllers/MainController.java`
