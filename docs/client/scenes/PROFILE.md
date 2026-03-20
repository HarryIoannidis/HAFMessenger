# PROFILE

### Screen objective
- Present detailed profile data for either:
  - the current user (self profile), or
  - a selected/search result contact.
- Provide placeholder self-service actions for profile edit/deletion requests.

### FXML
- `profile.fxml`

### Architecture
- **Controller**: `ProfileController`.
- **Opened from**: `MainController` via `ViewRouter.showPopup(...)`.
- **Model**: `UserProfileInfo`.

### UI elements
- Left panel:
  - Avatar image
  - `Text userIdText`
  - `JFXButton requestEditButton`
  - `JFXButton requestDeletionButton`
- Main panel fields:
  - `fullNameValueText`
  - `rankValueText`
  - `regNumberValueText`
  - `joinedDateValueText`
  - `emailValueText`
  - `telephoneValueText`
- Title bar:
  - `minimizeButton`
  - `closeButton`

### Flow
1. `MainController` resolves target profile and calls popup configuration `controller.showProfile(profile)`.
2. `ProfileController.showProfile(...)` stores model and calls `applyProfile()`.
3. `applyProfile()`:
   - maps values to UI
   - formats empty values as fallback dash
   - formats `userId` with `#` prefix
4. Self-action buttons are only visible when `profile.selfProfile()` is true.
5. Edit/Delete actions currently show informational stub dialogs ("not implemented yet").

### Window behavior
- Drag support via `titleBar` mouse events.
- Minimize and close handled by controller buttons.
