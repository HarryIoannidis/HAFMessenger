# PREVIEW

### Screen objective
- Show an image preview popup from chat media messages.
- Support saving the image locally when download is allowed.
- Keep the popup lightweight, draggable, and resizable.

### FXML
- `preview.fxml`

### Architecture
- **Controller**: `PreviewController`.
- **Opened from**: `ChatController.openImagePreview(...)` through `ViewRouter.showPopup(...)`.
- **Pattern**: popup controller with imperative setup (`showImage(...)`).

### UI elements
- `HBox titleBar`: custom drag area.
- `JFXButton minimizeButton`, `JFXButton closeButton`: window controls.
- `ImageView previewImageView`: image display area (preserve ratio).
- `ProgressIndicator loadingSpinner`: shown while image is loading.
- `JFXButton downloadButton`: save image action.

### Flow
1. `ChatController` opens popup using `UiConstants.FXML_PREVIEW`.
2. `PreviewController.showImage(source, suggestedName, downloadAllowed)`:
   - stores source metadata
   - enables/disables download button
   - starts async image loading
3. While loading:
   - spinner is visible
   - on completion, image is scaled to max 400x400 and stage is resized to scene
4. Download action:
   - resolves local file path via `ImageSaveSupport.resolveLocalSourcePath(...)`
   - opens `FileChooser`
   - copies file to destination with replace semantics

### Window behavior
- Popup stage uses transparent shell from `ViewRouter`.
- `PreviewController` enables drag-to-move and resize (`WindowResizeHelper.enableResizing`).
- Close button hides the popup stage (does not terminate app).
