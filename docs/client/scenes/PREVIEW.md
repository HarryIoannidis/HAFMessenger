# PREVIEW

## Purpose
Describe the attachment preview scene for viewing/downloading media or files.

## Current Implementation
- Controller: `PreviewController`.
- View: `preview.fxml`.
- Used for user-initiated preview flows from chat context actions.
- Supports in-app image preview and save/download flows for attachment payloads.

## Key Types/Interfaces
- `client.controllers.PreviewController`
- `client.utils.ImageSaveSupport`
- `client.utils.ViewRouter`

## Flow
1. User triggers preview action from chat/message context.
2. Preview scene/controller receives payload metadata/content.
3. Controller loads the image asynchronously and manages spinner/hover-zoom behavior.
4. Download action resolves local source path and opens save dialog when allowed.
5. User can inspect and optionally save/download content.

## Error/Security Notes
- Preview flow should avoid auto-executing external content.
- File save paths and failures are surfaced to user safely.

## Related Files
- `client/src/main/resources/fxml/preview.fxml`
- `client/src/main/java/com/haf/client/controllers/PreviewController.java`
- `client/src/main/resources/css/preview.css`
